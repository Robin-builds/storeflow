package cl.stockflow.warehouse.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cl.stockflow.warehouse.data.local.dao.*
import cl.stockflow.warehouse.data.remote.SUPABASE_ANON_KEY
import cl.stockflow.warehouse.data.remote.SUPABASE_URL
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import timber.log.Timber

private val json = Json { ignoreUnknownKeys = true }

@HiltWorker
class PullWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authSessionDao: AuthSessionDao,
    private val empresaDao: EmpresaDao,
    private val bodegaDao: BodegaDao,
    private val proveedorDao: ProveedorDao,
    private val usuarioDao: UsuarioDao,
    private val productoDao: ProductoDao,
    private val movimientoDao: MovimientoDao,
    private val atributoTemplateDao: AtributoTemplateDao,
    private val productoAtributoDao: ProductoAtributoDao
) : CoroutineWorker(context, params) {

    private val httpClient = HttpClient(Android) { expectSuccess = false }

    override suspend fun doWork(): Result {
        val sesion = authSessionDao.obtenerSesion() ?: run {
            Timber.d("PULL: sin sesión activa, abortando")
            return Result.success()
        }
        Timber.d("PULL: iniciando — bodega_id=${sesion.bodega_id} empresa_id=${sesion.empresa_id}")

        return try {
            val token = sesion.access_token

            // cada tabla se intenta siempre — un fallo en una no bloquea las demás
            val e1 = pull("empresas", token) { body ->
                val dtos = json.decodeFromString<List<EmpresaDto>>(body)
                empresaDao.upsertAll(dtos.map { it.toEntity() })
                dtos.size
            }
            val e2 = pull("bodegas", token) { body ->
                val dtos = json.decodeFromString<List<BodegaDto>>(body)
                bodegaDao.upsertAll(dtos.map { it.toEntity() })
                dtos.size
            }
            val e3 = pull("proveedores", token) { body ->
                val dtos = json.decodeFromString<List<ProveedorDto>>(body)
                proveedorDao.upsertAll(dtos.map { it.toEntity() })
                dtos.size
            }
            val e4 = pull("usuarios", token) { body ->
                val dtos = json.decodeFromString<List<UsuarioDto>>(body)
                usuarioDao.upsertAll(dtos.map { it.toEntity() })
                dtos.size
            }
            val e5 = pull("productos", token) { body ->
                val dtos = json.decodeFromString<List<ProductoDto>>(body)
                productoDao.upsertAll(dtos.map { it.toEntity() })
                val enRoom = productoDao.contarTodos()
                Timber.d("PULL: productos upsertAll OK — ${dtos.size} DTOs, $enRoom filas en Room")
                dtos.size
            }
            val e6 = pull("movimientos", token) { body ->
                val dtos = json.decodeFromString<List<MovimientoDto>>(body)
                movimientoDao.upsertAll(dtos.map { it.toEntity() })
                val enRoom = movimientoDao.contarTodos()
                Timber.d("PULL: movimientos upsertAll OK — ${dtos.size} DTOs, $enRoom filas en Room")
                dtos.size
            }
            val e7 = pull("atributo_templates", token) { body ->
                val dtos = json.decodeFromString<List<AtributoTemplateDto>>(body)
                atributoTemplateDao.upsertAll(dtos.map { it.toEntity() })
                dtos.size
            }
            val e8 = pull("producto_atributos", token) { body ->
                val dtos = json.decodeFromString<List<ProductoAtributoDto>>(body)
                productoAtributoDao.upsertAll(dtos.map { it.toEntity() })
                dtos.size
            }

            val ok = e1 && e2 && e3 && e4 && e5 && e6 && e7 && e8
            Timber.d("PULL: completado — ${if (ok) "SUCCESS" else "RETRY"} e1=$e1 e2=$e2 e3=$e3 e4=$e4 e5=$e5 e6=$e6 e7=$e7 e8=$e8")
            if (ok) Result.success() else Result.retry()
        } finally {
            httpClient.close()
        }
    }

    private suspend fun pull(tabla: String, token: String, upsert: suspend (String) -> Int): Boolean {
        return try {
            val response = httpClient.get("$SUPABASE_URL/rest/v1/$tabla?select=*") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer $token")
                    append("Accept", "application/json")
                }
            }
            if (!response.status.isSuccess()) {
                Timber.e("PULL: HTTP ${response.status.value} en $tabla — ${response.bodyAsText()}")
                return false
            }
            val body = response.bodyAsText()
            Timber.d("PULL: $tabla HTTP OK — body[0..200]=${body.take(200)}")
            val count = upsert(body)
            Timber.d("PULL: $tabla procesado — $count DTOs")
            true
        } catch (e: Exception) {
            Timber.e(e, "PULL: excepción en $tabla")
            false
        }
    }

    companion object {
        const val WORK_NAME = "stockflow_pull"
    }
}
