package cl.storeflow.warehouse.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.BodegaDao
import cl.storeflow.warehouse.data.local.dao.PlanCacheDao
import cl.storeflow.warehouse.data.local.entity.PlanCacheEntity
import cl.storeflow.warehouse.data.remote.ClientePlan
import cl.storeflow.warehouse.data.remote.DegradacionDto
import cl.storeflow.warehouse.data.remote.EntitlementDto
import cl.storeflow.warehouse.data.remote.RespuestaEntitlement
import cl.storeflow.warehouse.data.sync.GestorSesion
import cl.storeflow.warehouse.data.sync.ResultadoToken
import cl.storeflow.warehouse.domain.model.DegradacionFree
import cl.storeflow.warehouse.domain.model.EvaluadorPlan
import cl.storeflow.warehouse.domain.model.PlanActual
import cl.storeflow.warehouse.domain.model.PlanValidado
import cl.storeflow.warehouse.domain.model.RelojPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ResultadoValidacion {
    data class Actualizado(
        val empresaId: String,
        val rol: String,
        val bloqueado: Boolean,
        val planCambio: Boolean
    ) : ResultadoValidacion
    data object SinPlan : ResultadoValidacion
    data object SinConexion : ResultadoValidacion
    data class Error(val detalle: String) : ResultadoValidacion
}

@Singleton
class RelojPlanAndroid @Inject constructor(
    @ApplicationContext private val context: Context
) : RelojPlan {
    override fun ahoraMs() = System.currentTimeMillis()
    override fun elapsedMs() = SystemClock.elapsedRealtime()
    override fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}

/** Refleja en el dispositivo el plan que calcula el servidor, su única fuente de verdad (FR-010). */
@Singleton
class PlanRepository @Inject constructor(
    private val planCacheDao: PlanCacheDao,
    private val authSessionDao: AuthSessionDao,
    private val bodegaDao: BodegaDao,
    private val gestorSesion: GestorSesion,
    private val clientePlan: ClientePlan,
    private val reloj: RelojPlan
) {
    private val evaluador = EvaluadorPlan(reloj)
    private val json = Json { ignoreUnknownKeys = true }

    /** Valida con un token explícito: en el login la sesión todavía no está en Room. */
    suspend fun validar(accessToken: String): ResultadoValidacion =
        procesar(clientePlan.obtenerEntitlement(accessToken))

    suspend fun validarActual(): ResultadoValidacion {
        val sesion = when (val token = gestorSesion.obtenerToken(false)) {
            is ResultadoToken.Vigente -> token.sesion
            is ResultadoToken.SinRed -> {
                planCacheDao.actualizarMayorReloj(token.sesion.empresa_id, reloj.ahoraMs())
                return ResultadoValidacion.SinConexion
            }
            is ResultadoToken.SesionInvalida, ResultadoToken.SinSesion ->
                return ResultadoValidacion.SinConexion
        }
        planCacheDao.actualizarMayorReloj(sesion.empresa_id, reloj.ahoraMs())

        val respuesta = clientePlan.obtenerEntitlement(sesion.access_token)
        if (respuesta != RespuestaEntitlement.NoAutorizado) return procesar(respuesta)

        val renovada = gestorSesion.obtenerToken(true) as? ResultadoToken.Vigente
            ?: return ResultadoValidacion.SinConexion
        return procesar(clientePlan.obtenerEntitlement(renovada.sesion.access_token))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarPlan(): Flow<PlanActual?> = authSessionDao.observarSesion()
        .flatMapLatest { sesion ->
            if (sesion == null) {
                flowOf(null)
            } else {
                combine(
                    planCacheDao.observar(sesion.empresa_id),
                    bodegaDao.observarPorEmpresa(sesion.empresa_id)
                ) { cache, bodegas ->
                    cache?.let { aPlanActual(it, bodegas.mapTo(mutableSetOf()) { b -> b.id }) }
                }
            }
        }

    private suspend fun procesar(respuesta: RespuestaEntitlement): ResultadoValidacion =
        when (respuesta) {
            is RespuestaEntitlement.Ok -> guardar(respuesta.dto)
            RespuestaEntitlement.SinPlan -> marcarSinPlan()
            RespuestaEntitlement.NoAutorizado, RespuestaEntitlement.SinConexion ->
                ResultadoValidacion.SinConexion
            is RespuestaEntitlement.Error -> {
                Timber.e("PLAN: error al validar — ${respuesta.detalle}")
                ResultadoValidacion.Error(respuesta.detalle)
            }
        }

    private suspend fun guardar(dto: EntitlementDto): ResultadoValidacion {
        val previa = planCacheDao.obtener(dto.empresa_id)
        val nueva = try {
            aEntidad(dto, previa?.dispositivo_id ?: UUID.randomUUID().toString())
        } catch (e: DateTimeParseException) {
            Timber.e(e, "PLAN: fecha inválida en la respuesta")
            return ResultadoValidacion.Error("Fecha inválida en la respuesta de plan")
        }
        planCacheDao.guardar(nueva)
        return ResultadoValidacion.Actualizado(
            empresaId = dto.empresa_id,
            rol = dto.rol,
            bloqueado = dto.bloqueado,
            planCambio = previa != null && cambioElPlan(previa, nueva)
        )
    }

    private fun aEntidad(dto: EntitlementDto, dispositivoId: String): PlanCacheEntity {
        val ahora = reloj.ahoraMs()
        return PlanCacheEntity(
            empresa_id = dto.empresa_id,
            plan_efectivo = dto.plan_efectivo ?: PLAN_FREE,
            estado = if (dto.bloqueado) ESTADO_BLOQUEADO else dto.estado ?: PLAN_FREE,
            bloqueado = dto.bloqueado,
            puede_crear_bodega = dto.permisos?.crear_bodega ?: false,
            puede_crear_usuario = dto.permisos?.crear_usuario ?: false,
            historial_desde = aMillis(dto.historial_desde),
            lotes = dto.capacidades?.lotes ?: false,
            degradacion_free = dto.degradacion_free?.let { json.encodeToString(it) },
            bodegas_solo_lectura = json.encodeToString(dto.bodegas_solo_lectura),
            dispositivo_id = dispositivoId,
            trial_fin = aMillis(dto.trial_fin),
            vencimiento = aMillis(dto.vencimiento),
            validado_en_servidor = aMillis(dto.ahora_servidor) ?: ahora,
            reloj_en_validacion = ahora,
            mayor_reloj_observado = ahora,
            elapsed_en_validacion = reloj.elapsedMs(),
            boot_count = reloj.bootCount()
        )
    }

    /** La RPC no existe: se deja de restringir, conservando el identificador del dispositivo. */
    private suspend fun marcarSinPlan(): ResultadoValidacion {
        val sesion = authSessionDao.obtenerSesion() ?: return ResultadoValidacion.SinPlan
        val previa = planCacheDao.obtener(sesion.empresa_id) ?: return ResultadoValidacion.SinPlan
        planCacheDao.guardar(
            previa.copy(
                plan_efectivo = SIN_PLAN,
                estado = SIN_PLAN,
                bloqueado = false,
                puede_crear_bodega = true,
                puede_crear_usuario = true,
                historial_desde = null,
                lotes = true,
                degradacion_free = null,
                bodegas_solo_lectura = "[]",
                trial_fin = null,
                vencimiento = null
            )
        )
        return ResultadoValidacion.SinPlan
    }

    private fun cambioElPlan(previa: PlanCacheEntity, nueva: PlanCacheEntity) =
        previa.plan_efectivo != nueva.plan_efectivo ||
            previa.bloqueado != nueva.bloqueado ||
            previa.puede_crear_bodega != nueva.puede_crear_bodega ||
            previa.puede_crear_usuario != nueva.puede_crear_usuario ||
            previa.lotes != nueva.lotes ||
            previa.bodegas_solo_lectura != nueva.bodegas_solo_lectura

    private fun aPlanActual(cache: PlanCacheEntity, bodegas: Set<String>): PlanActual {
        if (cache.plan_efectivo == SIN_PLAN) return PlanActual(SIN_PLAN, SIN_PLAN, null)

        val evaluado = evaluador.evaluar(aPlanValidado(cache), bodegas)
        val estado = if (evaluado.degradado) PLAN_FREE else evaluado.estado
        val dias = cache.trial_fin
            ?.takeIf { estado == ESTADO_TRIAL }
            ?.let { fin ->
                val transcurrido = evaluado.mayorRelojObservado - cache.reloj_en_validacion
                val restante = (fin - cache.validado_en_servidor - transcurrido).coerceAtLeast(0)
                ((restante + DIA_MS - 1) / DIA_MS).toInt()
            }
        return PlanActual(evaluado.planEfectivo, estado, dias)
    }

    private fun aPlanValidado(cache: PlanCacheEntity) = PlanValidado(
        planEfectivo = cache.plan_efectivo,
        estado = cache.estado,
        bloqueado = cache.bloqueado,
        lotes = cache.lotes,
        puedeCrearBodega = cache.puede_crear_bodega,
        puedeCrearUsuario = cache.puede_crear_usuario,
        historialDesde = cache.historial_desde,
        bodegasSoloLectura = leerLista(cache.bodegas_solo_lectura),
        degradacion = cache.degradacion_free?.let(::leerDegradacion),
        relojEnValidacion = cache.reloj_en_validacion,
        mayorRelojObservado = cache.mayor_reloj_observado,
        elapsedEnValidacion = cache.elapsed_en_validacion,
        bootCount = cache.boot_count
    )

    private fun leerLista(valor: String): Set<String> = try {
        json.decodeFromString<List<String>>(valor).toSet()
    } catch (e: SerializationException) {
        Timber.e(e, "PLAN: lista de bodegas inválida en caché")
        emptySet()
    }

    private fun leerDegradacion(valor: String): DegradacionFree? = try {
        val dto = json.decodeFromString<DegradacionDto>(valor)
        DegradacionFree(
            bodegasActivas = dto.bodegas_activas.toSet(),
            bloqueado = dto.bloqueado,
            lotes = dto.capacidades.lotes,
            puedeCrearBodega = dto.permisos.crear_bodega,
            puedeCrearUsuario = dto.permisos.crear_usuario,
            historialDesde = aMillis(dto.historial_desde)
        )
    } catch (e: SerializationException) {
        Timber.e(e, "PLAN: degradación inválida en caché")
        null
    } catch (e: DateTimeParseException) {
        Timber.e(e, "PLAN: fecha inválida en la degradación")
        null
    }

    private fun aMillis(valor: String?): Long? =
        valor?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }

    companion object {
        const val SIN_PLAN = "SIN_PLAN"
        private const val PLAN_FREE = "FREE"
        private const val ESTADO_TRIAL = "TRIAL"
        private const val ESTADO_BLOQUEADO = "BLOQUEADO"
        private const val DIA_MS = 24L * 60 * 60 * 1_000
    }
}
