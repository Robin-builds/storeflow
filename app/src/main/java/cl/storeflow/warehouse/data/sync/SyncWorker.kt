package cl.storeflow.warehouse.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cl.storeflow.warehouse.data.local.dao.AtributoTemplateDao
import cl.storeflow.warehouse.data.local.dao.BodegaDao
import cl.storeflow.warehouse.data.local.dao.LoteDao
import cl.storeflow.warehouse.data.local.dao.MovimientoDao
import cl.storeflow.warehouse.data.local.dao.ProductoDao
import cl.storeflow.warehouse.data.local.entity.OperacionSync
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import cl.storeflow.warehouse.data.remote.SUPABASE_ANON_KEY
import cl.storeflow.warehouse.data.remote.SUPABASE_URL
import cl.storeflow.warehouse.data.repository.PlanRepository
import cl.storeflow.warehouse.data.repository.ResultadoValidacion
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val gestorSesion: GestorSesion,
    private val procesadorCola: ProcesadorCola,
    private val planRepository: PlanRepository,
    private val productoDao: ProductoDao,
    private val movimientoDao: MovimientoDao,
    private val bodegaDao: BodegaDao,
    private val atributoTemplateDao: AtributoTemplateDao,
    private val loteDao: LoteDao
) : CoroutineWorker(context, workerParams) {

    private val httpClient = HttpClient(Android) { expectSuccess = false }

    override suspend fun doWork(): Result {
        return try {
            val accessToken = when (val resultado = gestorSesion.obtenerToken()) {
                is ResultadoToken.Vigente -> resultado.sesion.access_token
                is ResultadoToken.SinRed -> {
                    Timber.d("SYNC: sin red para refrescar el token")
                    return Result.retry()
                }
                is ResultadoToken.SesionInvalida -> {
                    // La cola se conserva hasta que el mismo usuario vuelva a ingresar (FR-030).
                    Timber.w("SYNC: sesión inválida, se espera un nuevo ingreso")
                    return Result.success()
                }
                ResultadoToken.SinSesion -> {
                    Timber.d("SYNC: sin sesión activa, abortando")
                    return Result.failure()
                }
            }

            // Antes de mirar la cola: un cambio de plan debe llegar aunque no haya nada que enviar.
            // Si falla por red se continúa, porque el servidor sigue aplicando el límite.
            val validacion = planRepository.validarActual()

            val bloqueado = (validacion as? ResultadoValidacion.Actualizado)?.bloqueado
                ?: planRepository.estaBloqueado()

            val resultado = procesadorCola.procesar(
                enviar = { item -> pushItem(item, accessToken) },
                alSincronizar = { item ->
                    Timber.d("SYNC: OK — ${item.entidad_tipo}/${item.operacion} id=${item.entidad_id}")
                    marcarSincronizado(item)
                },
                usuarioBloqueado = bloqueado
            )
            planRepository.informarRetenidos(accessToken)
            if (resultado.hayQueReintentar) Result.retry() else Result.success()
        } finally {
            httpClient.close()
        }
    }

    private suspend fun pushItem(item: SyncEntity, accessToken: String): RespuestaPush {
        if (item.entidad_tipo == "producto_atributos")
            return pushProductoAtributosBatch(item, accessToken)
        return try {
            val url = when (item.operacion) {
                OperacionSync.INSERT -> "$SUPABASE_URL/rest/v1/${item.entidad_tipo}"
                OperacionSync.UPDATE,
                OperacionSync.DELETE -> "$SUPABASE_URL/rest/v1/${item.entidad_tipo}?id=eq.${item.entidad_id}"
            }
            val response = httpClient.request(url) {
                method = when (item.operacion) {
                    OperacionSync.INSERT -> HttpMethod.Post
                    OperacionSync.UPDATE -> HttpMethod.Patch
                    OperacionSync.DELETE -> HttpMethod.Delete
                }
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer $accessToken")
                    append("Content-Type", "application/json")
                    append("Prefer", "return=minimal")
                }
                if (item.operacion != OperacionSync.DELETE) {
                    setBody(item.payload)
                }
            }
            aRespuestaPush(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SYNC: excepción en pushItem id=${item.id}")
            RespuestaPush(status = null)
        }
    }

    private suspend fun pushProductoAtributosBatch(item: SyncEntity, accessToken: String): RespuestaPush {
        return try {
            val deleteResponse = httpClient.delete(
                "$SUPABASE_URL/rest/v1/producto_atributos?producto_id=eq.${item.entidad_id}"
            ) {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer $accessToken")
                }
            }
            if (!deleteResponse.status.isSuccess()) return aRespuestaPush(deleteResponse)
            if (item.payload == "[]") return RespuestaPush(deleteResponse.status.value)
            val insertResponse = httpClient.post("$SUPABASE_URL/rest/v1/producto_atributos") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer $accessToken")
                    append("Content-Type", "application/json")
                    append("Prefer", "return=minimal")
                }
                setBody(item.payload)
            }
            aRespuestaPush(insertResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SYNC: excepción en pushProductoAtributosBatch producto=${item.entidad_id}")
            RespuestaPush(status = null)
        }
    }

    private suspend fun aRespuestaPush(response: HttpResponse): RespuestaPush {
        val status = response.status.value
        if (response.status.isSuccess()) return RespuestaPush(status)
        val cuerpo = response.bodyAsText()
        Timber.e("SYNC: HTTP $status — $cuerpo")
        val json = try {
            Json.parseToJsonElement(cuerpo) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        }
        fun texto(campo: String) = (json?.get(campo) as? JsonPrimitive)?.takeIf { it.isString }?.content
        return RespuestaPush(status, texto("code"), texto("message"))
    }

    private suspend fun marcarSincronizado(item: SyncEntity) {
        val ahora = System.currentTimeMillis()
        when (item.entidad_tipo) {
            "productos"          -> productoDao.marcarSincronizado(item.entidad_id, ahora)
            "movimientos"        -> movimientoDao.marcarSincronizado(item.entidad_id, ahora)
            "bodegas"            -> bodegaDao.marcarSincronizado(item.entidad_id, ahora)
            "atributo_templates" -> atributoTemplateDao.marcarSincronizado(item.entidad_id, ahora)
            "producto_atributos" -> { /* PK compuesta sin campo synced */ }
            "lotes"              -> loteDao.marcarSincronizado(item.entidad_id, ahora)
        }
    }

    companion object {
        const val WORK_NAME = "storeflow_sync"
    }
}
