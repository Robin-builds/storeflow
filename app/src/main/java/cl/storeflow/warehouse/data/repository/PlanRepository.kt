package cl.storeflow.warehouse.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.AvisoPlanDao
import cl.storeflow.warehouse.data.local.dao.BodegaDao
import cl.storeflow.warehouse.data.local.dao.PlanCacheDao
import cl.storeflow.warehouse.data.local.dao.RetenidoPorTipo
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.AvisoPlanEntity
import cl.storeflow.warehouse.data.local.entity.PlanCacheEntity
import cl.storeflow.warehouse.data.remote.ClientePlan
import cl.storeflow.warehouse.data.remote.DegradacionDto
import cl.storeflow.warehouse.data.remote.EntitlementDto
import cl.storeflow.warehouse.data.remote.RespuestaEntitlement
import cl.storeflow.warehouse.data.sync.GestorSesion
import cl.storeflow.warehouse.data.sync.PullTrigger
import cl.storeflow.warehouse.data.sync.ResolverRetencion
import cl.storeflow.warehouse.data.sync.ResultadoToken
import cl.storeflow.warehouse.data.sync.SyncTrigger
import cl.storeflow.warehouse.domain.model.DegradacionFree
import cl.storeflow.warehouse.domain.model.EvaluadorPlan
import cl.storeflow.warehouse.domain.model.MomentosAviso
import cl.storeflow.warehouse.domain.model.PlanActual
import cl.storeflow.warehouse.domain.model.PlanEvaluado
import cl.storeflow.warehouse.domain.model.PlanValidado
import cl.storeflow.warehouse.domain.model.RelojPlan
import cl.storeflow.warehouse.domain.model.RetenidoUsuario
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        val bloqueado: Boolean
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
    private val reloj: RelojPlan,
    private val syncDao: SyncDao,
    private val pullTrigger: PullTrigger,
    private val syncTrigger: SyncTrigger,
    private val avisoPlanDao: AvisoPlanDao
) {
    private val evaluador = EvaluadorPlan(reloj)
    private val json = Json { ignoreUnknownKeys = true }

    // En memoria: solo el ADMIN lo recibe y se renueva en cada validación.
    private val _retenidosEmpresa = MutableStateFlow<List<RetenidoUsuario>>(emptyList())
    val retenidosEmpresa: StateFlow<List<RetenidoUsuario>> = _retenidosEmpresa.asStateFlow()

    @Volatile
    private var ultimoRetenidoInformado: Int? = null

    /** Valida con un token explícito: en el login la sesión todavía no está en Room. */
    suspend fun validar(accessToken: String): ResultadoValidacion =
        procesar(clientePlan.obtenerEntitlement(accessToken), accessToken)

    fun observarRetenidosPropios(): Flow<List<RetenidoPorTipo>> = syncDao.observarCola().map { cola ->
        val retenidos = ResolverRetencion.retenidos(cola)
        cola.filter { it.id in retenidos }
            .groupingBy { it.entidad_tipo }
            .eachCount()
            .toSortedMap()
            .map { (tipo, cantidad) -> RetenidoPorTipo(tipo, cantidad) }
    }

    fun observarBloqueado(): Flow<Boolean> =
        observarPlan().map { it?.bloqueado ?: false }.distinctUntilChanged()

    fun observarLotes(): Flow<Boolean> =
        observarPlan().map { it?.lotes ?: true }.distinctUntilChanged()

    fun observarHistorialDesde(): Flow<Long?> =
        observarPlan().map { it?.historialDesde }.distinctUntilChanged()

    suspend fun estaBloqueado(): Boolean = evaluarCache()?.bloqueado ?: false

    suspend fun incluyeLotes(): Boolean = evaluarCache()?.lotes ?: true

    suspend fun puedeCrearBodega(): Boolean = evaluarCache()?.puedeCrearBodega ?: true

    suspend fun puedeCrearUsuario(): Boolean = evaluarCache()?.puedeCrearUsuario ?: true

    /** El aviso más reciente sin cerrar, solo para ADMIN (FR-017). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarAvisoActual(): Flow<String?> = authSessionDao.observarSesion()
        .flatMapLatest { sesion ->
            if (sesion == null || sesion.rol != ROL_ADMIN) {
                flowOf(null)
            } else {
                combine(
                    planCacheDao.observar(sesion.empresa_id),
                    avisoPlanDao.observar(sesion.empresa_id)
                ) { cache, avisos -> avisoVigente(cache, avisos) }
            }
        }
        .distinctUntilChanged()

    private fun avisoVigente(cache: PlanCacheEntity?, avisos: List<AvisoPlanEntity>): String? {
        if (cache == null || cache.plan_efectivo == SIN_PLAN || cache.bloqueado) return null

        // Sin señal se calcula desde el plan guardado. Solo si seguía en trial: quien pagó
        // no recibe avisos de vencimiento.
        val calculados = cache.trial_fin
            ?.takeIf { cache.estado == ESTADO_TRIAL }
            ?.let { fin ->
                val transcurrido = maxOf(reloj.ahoraMs(), cache.mayor_reloj_observado) - cache.reloj_en_validacion
                MomentosAviso.alcanzados(fin, cache.validado_en_servidor + transcurrido)
            }
            ?: emptyList()

        val vigente = MomentosAviso.vigente(
            emitidos = avisos.map { it.momento } + calculados,
            cerrados = avisos.filter { it.cerrado }.map { it.momento }
        )
        return when (cache.estado) {
            ESTADO_TRIAL -> vigente
            // Un aviso guardado de antes del vencimiento ya no sería cierto en Free.
            PLAN_FREE -> vigente?.takeIf { it == MomentosAviso.TERMINO || it == MomentosAviso.MAS_2 }
            else -> null
        }
    }

    /** Cierra el aviso y los anteriores. Sin señal, queda pendiente de confirmar. */
    suspend fun cerrarAviso(momento: String) {
        val sesion = authSessionDao.obtenerSesion() ?: return
        val momentos = MomentosAviso.hasta(momento)
        if (momentos.isEmpty()) return

        // Un aviso calculado sin señal todavía no está guardado.
        momentos.forEach {
            avisoPlanDao.insertarSiNoExiste(AvisoPlanEntity(empresa_id = sesion.empresa_id, momento = it))
        }
        avisoPlanDao.cerrarLocal(sesion.empresa_id, momentos)

        val token = gestorSesion.obtenerToken(false) as? ResultadoToken.Vigente ?: return
        confirmarCierres(sesion.empresa_id, token.sesion.access_token)
    }

    private suspend fun confirmarCierres(empresaId: String, accessToken: String) {
        val pendientes = avisoPlanDao.obtenerCierresPendientes(empresaId)
        val ultimo = pendientes.maxByOrNull { MomentosAviso.ORDEN.indexOf(it.momento) } ?: return
        if (clientePlan.cerrarAviso(accessToken, ultimo.momento)) {
            pendientes.forEach { avisoPlanDao.confirmarCierre(empresaId, it.momento) }
        }
    }

    private suspend fun sincronizarAvisos(dto: EntitlementDto, accessToken: String) {
        if (dto.bloqueado || dto.rol != ROL_ADMIN) return
        dto.avisos.forEach {
            avisoPlanDao.insertarSiNoExiste(AvisoPlanEntity(empresa_id = dto.empresa_id, momento = it.momento))
        }
        val cerrados = dto.avisos.filter { it.cerrado }.map { it.momento }
        if (cerrados.isNotEmpty()) avisoPlanDao.marcarCerradosEnServidor(dto.empresa_id, cerrados)
        confirmarCierres(dto.empresa_id, accessToken)
    }

    /** Null sin plan guardado o sin plan en el servidor: en ese caso no se restringe nada. */
    private suspend fun evaluarCache(): PlanEvaluado? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        val cache = planCacheDao.obtener(sesion.empresa_id) ?: return null
        if (cache.plan_efectivo == SIN_PLAN) return null
        val bodegas = bodegaDao.observarPorEmpresa(sesion.empresa_id).first()
            .mapTo(mutableSetOf()) { it.id }
        return evaluador.evaluar(aPlanValidado(cache), bodegas)
    }

    /**
     * Informa la cantidad retenida solo cuando cambió (FR-023). Tras reiniciar la app informa
     * una vez, incluso 0, para limpiar en el servidor lo que se resolvió antes del reinicio.
     */
    suspend fun informarRetenidos(accessToken: String) {
        val sesion = authSessionDao.obtenerSesion() ?: return
        val cache = planCacheDao.obtener(sesion.empresa_id) ?: return
        if (cache.plan_efectivo == SIN_PLAN) return
        val cantidad = ResolverRetencion.retenidos(syncDao.obtenerCola()).size
        if (cantidad == ultimoRetenidoInformado) return
        if (clientePlan.reportarRetenidos(accessToken, cache.dispositivo_id, cantidad)) {
            ultimoRetenidoInformado = cantidad
        }
    }

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
        if (respuesta != RespuestaEntitlement.NoAutorizado) return procesar(respuesta, sesion.access_token)

        val renovada = gestorSesion.obtenerToken(true) as? ResultadoToken.Vigente
            ?: return ResultadoValidacion.SinConexion
        return procesar(clientePlan.obtenerEntitlement(renovada.sesion.access_token), renovada.sesion.access_token)
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

    fun observarBodegasSoloLectura(): Flow<Set<String>> =
        observarPlan().map { it?.bodegasSoloLectura ?: emptySet() }.distinctUntilChanged()

    /** Según el último plan guardado. El servidor sigue siendo quien decide (FR-010). */
    suspend fun bodegasEnSoloLectura(): Set<String> {
        val sesion = authSessionDao.obtenerSesion() ?: return emptySet()
        val cache = planCacheDao.obtener(sesion.empresa_id) ?: return emptySet()
        if (cache.plan_efectivo == SIN_PLAN) return emptySet()
        val bodegas = bodegaDao.observarPorEmpresa(sesion.empresa_id).first()
            .mapTo(mutableSetOf()) { it.id }
        return evaluador.evaluar(aPlanValidado(cache), bodegas).bodegasSoloLectura
    }

    private suspend fun procesar(respuesta: RespuestaEntitlement, accessToken: String): ResultadoValidacion =
        when (respuesta) {
            is RespuestaEntitlement.Ok -> guardar(respuesta.dto, accessToken)
            RespuestaEntitlement.SinPlan -> marcarSinPlan()
            RespuestaEntitlement.NoAutorizado, RespuestaEntitlement.SinConexion ->
                ResultadoValidacion.SinConexion
            is RespuestaEntitlement.Error -> {
                Timber.e("PLAN: error al validar — ${respuesta.detalle}")
                ResultadoValidacion.Error(respuesta.detalle)
            }
        }

    /** Al recuperar el acceso restaura la bodega de la sesión y retoma la sincronización (FR-029). */
    private suspend fun alRecuperarAcceso(dto: EntitlementDto, previa: PlanCacheEntity?, accessToken: String) {
        if (dto.bloqueado) return
        val sesion = authSessionDao.obtenerSesion() ?: return
        if (sesion.empresa_id != dto.empresa_id) return
        if (previa?.bloqueado != true && sesion.bodega_id.isNotBlank()) return

        if (sesion.bodega_id.isBlank()) {
            val bodega = clientePlan.obtenerBodegas(accessToken)
                ?.firstOrNull { it !in dto.bodegas_solo_lectura }
                ?: return // Sin bodega_id se reintenta en la próxima validación.
            authSessionDao.guardarSesion(sesion.copy(bodega_id = bodega, rol = dto.rol))
        }
        pullTrigger.trigger()
        syncTrigger.trigger()
    }

    private suspend fun guardar(dto: EntitlementDto, accessToken: String): ResultadoValidacion {
        val previa = planCacheDao.obtener(dto.empresa_id)
        val nueva = try {
            aEntidad(dto, previa?.dispositivo_id ?: UUID.randomUUID().toString())
        } catch (e: DateTimeParseException) {
            Timber.e(e, "PLAN: fecha inválida en la respuesta")
            return ResultadoValidacion.Error("Fecha inválida en la respuesta de plan")
        }
        planCacheDao.guardar(nueva)
        // Aquí y no en SyncWorker: quien guarde primero el plan nuevo es el único que ve el cambio.
        if (previa != null && cambioElPlan(previa, nueva)) syncDao.limpiarRechazos()
        _retenidosEmpresa.value = dto.retenidos.map {
            RetenidoUsuario(it.usuario_id, it.cantidad, it.actualizado_at)
        }
        sincronizarAvisos(dto, accessToken)
        alRecuperarAcceso(dto, previa, accessToken)
        return ResultadoValidacion.Actualizado(
            empresaId = dto.empresa_id,
            rol = dto.rol,
            bloqueado = dto.bloqueado
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
        return PlanActual(
            tipo = evaluado.planEfectivo,
            estado = estado,
            diasRestantesTrial = dias,
            bodegasSoloLectura = evaluado.bodegasSoloLectura,
            bloqueado = evaluado.bloqueado,
            lotes = evaluado.lotes,
            puedeCrearBodega = evaluado.puedeCrearBodega,
            puedeCrearUsuario = evaluado.puedeCrearUsuario,
            historialDesde = evaluado.historialDesde
        )
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
        const val MENSAJE_SOLO_LECTURA = "Esta bodega está en solo lectura con el plan actual"
        const val MENSAJE_SIN_PERMISO_BODEGA = "Tu plan actual no permite crear más bodegas"
        const val MENSAJE_SIN_PERMISO_USUARIO = "Tu plan actual no permite agregar más usuarios"
        private const val PLAN_FREE = "FREE"
        private const val ESTADO_TRIAL = "TRIAL"
        private const val ROL_ADMIN = "ADMIN"
        private const val ESTADO_BLOQUEADO = "BLOQUEADO"
        private const val DIA_MS = 24L * 60 * 60 * 1_000
    }
}
