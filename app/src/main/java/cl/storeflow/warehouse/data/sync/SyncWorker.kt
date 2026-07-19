package cl.storeflow.warehouse.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cl.storeflow.warehouse.data.local.dao.AtributoTemplateDao
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.BodegaDao
import cl.storeflow.warehouse.data.local.dao.LoteDao
import cl.storeflow.warehouse.data.local.dao.MovimientoDao
import cl.storeflow.warehouse.data.local.dao.ProductoDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.local.entity.OperacionSync
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import cl.storeflow.warehouse.data.remote.SUPABASE_ANON_KEY
import cl.storeflow.warehouse.data.remote.SUPABASE_URL
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.util.Date

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncDao: SyncDao,
    private val authSessionDao: AuthSessionDao,
    private val productoDao: ProductoDao,
    private val movimientoDao: MovimientoDao,
    private val bodegaDao: BodegaDao,
    private val atributoTemplateDao: AtributoTemplateDao,
    private val loteDao: LoteDao
) : CoroutineWorker(context, workerParams) {

    private val httpClient = HttpClient(Android) { expectSuccess = false }

    override suspend fun doWork(): Result {
        return try {
            val sesion = authSessionDao.obtenerSesion() ?: run {
                Timber.d("SYNC: sin sesión activa, abortando")
                return Result.failure()
            }

            val accessToken = if (sesion.expires_at.before(Date())) {
                Timber.d("SYNC: token expirado, refrescando")
                refreshToken(sesion) ?: run {
                    Timber.e("SYNC: no se pudo refrescar el token")
                    return Result.retry()
                }
            } else {
                sesion.access_token
            }

            val cola = syncDao.obtenerCola()
            Timber.d("SYNC: ${cola.size} items en cola")
            if (cola.isEmpty()) return Result.success()

            var hayErrores = false
            for (item in cola) {
                val exito = pushItem(item, accessToken)
                when {
                    exito -> {
                        Timber.d("SYNC: OK — ${item.entidad_tipo}/${item.operacion} id=${item.entidad_id}")
                        marcarSincronizado(item)
                        syncDao.eliminar(item)
                    }
                    item.reintentos >= MAX_REINTENTOS -> {
                        Timber.e("SYNC: descartado tras $MAX_REINTENTOS reintentos — id=${item.id}")
                        syncDao.eliminar(item)
                    }
                    else -> {
                        Timber.w("SYNC: fallo, reintento ${item.reintentos + 1} — id=${item.id}")
                        syncDao.incrementarReintentos(item.id)
                        hayErrores = true
                    }
                }
            }

            if (hayErrores) Result.retry() else Result.success()
        } finally {
            httpClient.close()
        }
    }

    private suspend fun refreshToken(sesion: AuthSessionEntity): String? {
        return try {
            val response = httpClient.post("$SUPABASE_URL/auth/v1/token?grant_type=refresh_token") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Content-Type", "application/json")
                }
                setBody("""{"refresh_token":"${sesion.refresh_token}"}""")
            }
            if (!response.status.isSuccess()) {
                Timber.e("SYNC: refresh HTTP ${response.status.value}")
                return null
            }
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val newAccess = json["access_token"]?.jsonPrimitive?.content ?: return null
            val newRefresh = json["refresh_token"]?.jsonPrimitive?.content ?: sesion.refresh_token
            val expiresIn = json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L
            authSessionDao.guardarSesion(
                sesion.copy(
                    access_token = newAccess,
                    refresh_token = newRefresh,
                    expires_at = Date(System.currentTimeMillis() + expiresIn * 1000)
                )
            )
            Timber.d("SYNC: token refrescado OK")
            newAccess
        } catch (e: Exception) {
            Timber.e(e, "SYNC: excepción en refreshToken")
            null
        }
    }

    private suspend fun pushItem(item: SyncEntity, accessToken: String): Boolean {
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
            if (!response.status.isSuccess()) {
                Timber.e("SYNC: HTTP ${response.status.value} — ${response.bodyAsText()}")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            Timber.e(e, "SYNC: excepción en pushItem id=${item.id}")
            false
        }
    }

    private suspend fun pushProductoAtributosBatch(item: SyncEntity, accessToken: String): Boolean {
        return try {
            val deleteResponse = httpClient.delete(
                "$SUPABASE_URL/rest/v1/producto_atributos?producto_id=eq.${item.entidad_id}"
            ) {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer $accessToken")
                }
            }
            if (!deleteResponse.status.isSuccess()) {
                Timber.e("SYNC: HTTP ${deleteResponse.status.value} al borrar atributos producto=${item.entidad_id}")
                return false
            }
            if (item.payload == "[]") return true
            val insertResponse = httpClient.post("$SUPABASE_URL/rest/v1/producto_atributos") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer $accessToken")
                    append("Content-Type", "application/json")
                    append("Prefer", "return=minimal")
                }
                setBody(item.payload)
            }
            if (!insertResponse.status.isSuccess())
                Timber.e("SYNC: HTTP ${insertResponse.status.value} al insertar atributos producto=${item.entidad_id}")
            insertResponse.status.isSuccess()
        } catch (e: Exception) {
            Timber.e(e, "SYNC: excepción en pushProductoAtributosBatch producto=${item.entidad_id}")
            false
        }
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
        private const val MAX_REINTENTOS = 3
    }
}
