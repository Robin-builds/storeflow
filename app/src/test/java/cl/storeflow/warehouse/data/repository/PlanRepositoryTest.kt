package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.AvisoPlanDao
import cl.storeflow.warehouse.data.local.entity.AvisoPlanEntity
import cl.storeflow.warehouse.data.local.dao.BodegaDao
import cl.storeflow.warehouse.data.local.dao.PlanCacheDao
import cl.storeflow.warehouse.data.local.dao.RetenidoPorTipo
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.local.entity.OperacionSync
import cl.storeflow.warehouse.data.local.entity.PlanCacheEntity
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import cl.storeflow.warehouse.data.remote.CapacidadesDto
import cl.storeflow.warehouse.data.remote.ClientePlan
import cl.storeflow.warehouse.data.remote.DegradacionDto
import cl.storeflow.warehouse.data.remote.EntitlementDto
import cl.storeflow.warehouse.data.remote.PermisosDto
import cl.storeflow.warehouse.data.remote.RespuestaEntitlement
import cl.storeflow.warehouse.data.remote.clasificarRespuestaEntitlement
import cl.storeflow.warehouse.data.sync.GestorSesion
import cl.storeflow.warehouse.data.sync.ResultadoToken
import cl.storeflow.warehouse.data.sync.SyncTrigger
import cl.storeflow.warehouse.domain.model.RelojPlan
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.UUID

class PlanRepositoryTest {

    private val planCacheDao = mockk<PlanCacheDao>()
    private val authSessionDao = mockk<AuthSessionDao>()
    private val bodegaDao = mockk<BodegaDao>()
    private val gestorSesion = mockk<GestorSesion>()
    private val clientePlan = mockk<ClientePlan>()
    private val avisoPlanDao = mockk<AvisoPlanDao>(relaxed = true)
    private val syncDao = mockk<SyncDao>(relaxed = true)
    private val syncTrigger = mockk<SyncTrigger>(relaxed = true)
    private val reloj = object : RelojPlan {
        override fun ahoraMs() = AHORA
        override fun elapsedMs() = 5_000L
        override fun bootCount() = 2
    }
    private lateinit var repo: PlanRepository

    private val sesion = AuthSessionEntity(
        access_token = "token",
        refresh_token = "refresh",
        user_id = "usuario-1",
        empresa_id = "empresa-1",
        bodega_id = "b1",
        rol = "ADMIN",
        expires_at = Date(AHORA + 3_600_000),
        correo = "a@test.invalid"
    )

    private val dto = EntitlementDto(
        bloqueado = false,
        empresa_id = "empresa-1",
        rol = "ADMIN",
        plan_efectivo = "STANDARD",
        estado = "TRIAL",
        capacidades = CapacidadesDto(export = true, lotes = false),
        permisos = PermisosDto(crear_bodega = true, crear_usuario = false),
        historial_desde = "2026-01-01T00:00:00+00:00",
        trial_fin = "2026-12-21T12:00:00+00:00",
        vencimiento = "2026-12-21T12:00:00+00:00",
        bodegas_solo_lectura = listOf("b2"),
        degradacion_free = DegradacionDto(
            bodegas_activas = listOf("b1"),
            bloqueado = false,
            capacidades = CapacidadesDto(export = false, lotes = false),
            permisos = PermisosDto(crear_bodega = false, crear_usuario = false),
            historial_desde = "2026-08-23T12:00:00+00:00"
        ),
        ahora_servidor = "2026-11-21T12:00:00+00:00"
    )

    private fun cache(
        plan: String = "STANDARD",
        estado: String = "TRIAL",
        trialFin: Long? = null,
        dispositivo: String = "dispositivo-1"
    ) = PlanCacheEntity(
        empresa_id = "empresa-1",
        plan_efectivo = plan,
        estado = estado,
        bloqueado = false,
        puede_crear_bodega = true,
        puede_crear_usuario = false,
        historial_desde = null,
        lotes = false,
        degradacion_free = """{"bodegas_activas":["b1"],"bloqueado":false,"capacidades":{"export":false,"lotes":false},"permisos":{"crear_bodega":false,"crear_usuario":false},"historial_desde":null}""",
        bodegas_solo_lectura = """["b2"]""",
        dispositivo_id = dispositivo,
        trial_fin = trialFin,
        vencimiento = trialFin,
        validado_en_servidor = AHORA,
        reloj_en_validacion = AHORA,
        mayor_reloj_observado = AHORA,
        elapsed_en_validacion = 5_000L,
        boot_count = 2
    )

    @Before
    fun preparar() {
        coEvery { planCacheDao.obtener(any()) } returns null
        coEvery { planCacheDao.guardar(any()) } just Runs
        coEvery { planCacheDao.actualizarMayorReloj(any(), any()) } just Runs
        coEvery { authSessionDao.obtenerSesion() } returns sesion
        every { authSessionDao.observarSesion() } returns flowOf(sesion)
        repo = PlanRepository(
            planCacheDao, authSessionDao, bodegaDao, gestorSesion, clientePlan, reloj,
            syncDao, mockk(relaxed = true), syncTrigger, avisoPlanDao
        )
    }

    @Test
    fun `guarda la respuesta del servidor con la referencia de reloj`() = runTest {
        coEvery { clientePlan.obtenerEntitlement("token") } returns RespuestaEntitlement.Ok(dto)
        val guardado = slot<PlanCacheEntity>()
        coEvery { planCacheDao.guardar(capture(guardado)) } just Runs

        val r = repo.validar("token")

        assertTrue(r is ResultadoValidacion.Actualizado)
        with(guardado.captured) {
            assertEquals("empresa-1", empresa_id)
            assertEquals("STANDARD", plan_efectivo)
            assertEquals("TRIAL", estado)
            assertTrue(puede_crear_bodega)
            assertFalse(puede_crear_usuario)
            assertEquals(AHORA, reloj_en_validacion)
            assertEquals(AHORA, mayor_reloj_observado)
            assertEquals(5_000L, elapsed_en_validacion)
            assertEquals(2, boot_count)
            assertEquals(java.time.Instant.parse("2026-12-21T12:00:00Z").toEpochMilli(), trial_fin)
            UUID.fromString(dispositivo_id)
        }
    }

    @Test
    fun `conserva el dispositivo_id al revalidar`() = runTest {
        coEvery { planCacheDao.obtener("empresa-1") } returns cache()
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.Ok(dto)
        val guardado = slot<PlanCacheEntity>()
        coEvery { planCacheDao.guardar(capture(guardado)) } just Runs

        repo.validar("token")

        assertEquals("dispositivo-1", guardado.captured.dispositivo_id)
    }

    @Test
    fun `sin red conserva la cache`() = runTest {
        coEvery { planCacheDao.obtener("empresa-1") } returns cache()
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.SinConexion

        assertEquals(ResultadoValidacion.SinConexion, repo.validar("token"))
        coVerify(exactly = 0) { planCacheDao.guardar(any()) }
    }

    @Test
    fun `PGRST202 sin cache queda sin plan y no escribe`() = runTest {
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.SinPlan

        assertEquals(ResultadoValidacion.SinPlan, repo.validar("token"))
        coVerify(exactly = 0) { planCacheDao.guardar(any()) }
    }

    @Test
    fun `PGRST202 con cache deja de restringir y conserva el dispositivo`() = runTest {
        coEvery { planCacheDao.obtener("empresa-1") } returns cache()
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.SinPlan
        val guardado = slot<PlanCacheEntity>()
        coEvery { planCacheDao.guardar(capture(guardado)) } just Runs

        repo.validar("token")

        with(guardado.captured) {
            assertEquals(PlanRepository.SIN_PLAN, plan_efectivo)
            assertEquals("dispositivo-1", dispositivo_id)
            assertTrue(puede_crear_bodega)
            assertTrue(puede_crear_usuario)
            assertFalse(bloqueado)
            assertNull(degradacion_free)
            assertEquals("[]", bodegas_solo_lectura)
        }
    }

    @Test
    fun `guarda a un usuario bloqueado con su empresa y rol`() = runTest {
        val bloqueado = EntitlementDto(
            bloqueado = true,
            empresa_id = "empresa-1",
            rol = "OPERADOR",
            ahora_servidor = "2026-11-21T12:00:00+00:00"
        )
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.Ok(bloqueado)
        val guardado = slot<PlanCacheEntity>()
        coEvery { planCacheDao.guardar(capture(guardado)) } just Runs

        val r = repo.validar("token") as ResultadoValidacion.Actualizado

        assertTrue(r.bloqueado)
        assertEquals("empresa-1", r.empresaId)
        assertEquals("OPERADOR", r.rol)
        assertTrue(guardado.captured.bloqueado)
        assertFalse(guardado.captured.puede_crear_bodega)
    }

    @Test
    fun `libera los retenidos cuando el plan cambio respecto de la cache`() = runTest {
        coEvery { planCacheDao.obtener("empresa-1") } returns cache(plan = "FREE", estado = "FREE")
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.Ok(dto)

        repo.validar("token")

        coVerify(exactly = 1) { syncDao.limpiarRechazos(any()) }
    }

    @Test
    fun `la primera validacion no libera retenidos`() = runTest {
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.Ok(dto)

        repo.validar("token")

        coVerify(exactly = 0) { syncDao.limpiarRechazos(any()) }
    }

    @Test
    fun `sin cambio de plan no libera retenidos`() = runTest {
        coEvery { planCacheDao.obtener("empresa-1") } returns cache()
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.Ok(dto)

        repo.validar("token")

        coVerify(exactly = 0) { syncDao.limpiarRechazos(any()) }
    }

    @Test
    fun `al recuperar el acceso libera los retenidos antes de sincronizar`() = runTest {
        coEvery { planCacheDao.obtener("empresa-1") } returns cache().copy(bloqueado = true)
        coEvery { clientePlan.obtenerEntitlement(any()) } returns RespuestaEntitlement.Ok(dto)

        repo.validar("token")

        coVerifyOrder {
            planCacheDao.guardar(any())
            syncDao.limpiarRechazos(any())
            syncTrigger.trigger()
        }
    }

    @Test
    fun `validarActual refresca el token y reintenta una vez ante 401`() = runTest {
        val renovada = sesion.copy(access_token = "token-nuevo")
        coEvery { gestorSesion.obtenerToken(false) } returns ResultadoToken.Vigente(sesion)
        coEvery { gestorSesion.obtenerToken(true) } returns ResultadoToken.Vigente(renovada)
        coEvery { clientePlan.obtenerEntitlement("token") } returns RespuestaEntitlement.NoAutorizado
        coEvery { clientePlan.obtenerEntitlement("token-nuevo") } returns RespuestaEntitlement.Ok(dto)

        assertTrue(repo.validarActual() is ResultadoValidacion.Actualizado)
    }

    @Test
    fun `validarActual sin sesion no consulta al servidor`() = runTest {
        coEvery { gestorSesion.obtenerToken(false) } returns ResultadoToken.SinSesion

        assertEquals(ResultadoValidacion.SinConexion, repo.validarActual())
        coVerify(exactly = 0) { clientePlan.obtenerEntitlement(any()) }
    }

    @Test
    fun `observarPlan sin cache es nulo`() = runTest {
        every { planCacheDao.observar("empresa-1") } returns flowOf(null)
        every { bodegaDao.observarPorEmpresa("empresa-1") } returns flowOf(emptyList())

        assertNull(repo.observarPlan().first())
    }

    @Test
    fun `observarPlan entrega tipo, estado y dias restantes`() = runTest {
        val enDiezDias = AHORA + 10L * 24 * 60 * 60 * 1_000
        every { planCacheDao.observar("empresa-1") } returns flowOf(cache(trialFin = enDiezDias))
        every { bodegaDao.observarPorEmpresa("empresa-1") } returns flowOf(emptyList())

        val plan = repo.observarPlan().first()!!

        assertEquals("STANDARD", plan.tipo)
        assertEquals("TRIAL", plan.estado)
        assertEquals(10, plan.diasRestantesTrial)
    }

    @Test
    fun `una fraccion de dia cuenta como dia restante`() = runTest {
        every { planCacheDao.observar("empresa-1") } returns flowOf(cache(trialFin = AHORA + 1_000))
        every { bodegaDao.observarPorEmpresa("empresa-1") } returns flowOf(emptyList())

        assertEquals(1, repo.observarPlan().first()!!.diasRestantesTrial)
    }

    @Test
    fun `fuera de trial no hay dias restantes`() = runTest {
        every { planCacheDao.observar("empresa-1") } returns
            flowOf(cache(plan = "FREE", estado = "FREE", trialFin = AHORA - 1_000))
        every { bodegaDao.observarPorEmpresa("empresa-1") } returns flowOf(emptyList())

        assertNull(repo.observarPlan().first()!!.diasRestantesTrial)
    }

    private fun aviso(momento: String, cerrado: Boolean = false) =
        AvisoPlanEntity(empresa_id = "empresa-1", momento = momento, cerrado = cerrado)

    private fun prepararAvisos(cacheActual: PlanCacheEntity, vararg avisos: AvisoPlanEntity) {
        every { planCacheDao.observar("empresa-1") } returns flowOf(cacheActual)
        every { avisoPlanDao.observar("empresa-1") } returns flowOf(avisos.toList())
    }

    @Test
    fun `FR-017 - muestra solo el aviso mas reciente sin cerrar`() = runTest {
        prepararAvisos(
            cache(plan = "FREE", estado = "FREE", trialFin = AHORA - 1_000),
            aviso("DIA_0"), aviso("MENOS_7"), aviso("TERMINO")
        )

        assertEquals("TERMINO", repo.observarAvisoActual().first())
    }

    @Test
    fun `FR-017 - un aviso anterior a uno cerrado no reaparece`() = runTest {
        prepararAvisos(
            cache(plan = "FREE", estado = "FREE", trialFin = AHORA - 1_000),
            aviso("DIA_0"), aviso("MENOS_7", cerrado = true)
        )

        assertNull(repo.observarAvisoActual().first())
    }

    @Test
    fun `FR-017 - un OPERADOR no ve avisos`() = runTest {
        every { authSessionDao.observarSesion() } returns flowOf(sesion.copy(rol = "OPERADOR"))
        prepararAvisos(cache(), aviso("DIA_0"))

        assertNull(repo.observarAvisoActual().first())
    }

    @Test
    fun `FR-017 - sin senal calcula el momento desde el plan guardado`() = runTest {
        val enSeisDias = AHORA + 6L * 24 * 60 * 60 * 1_000
        prepararAvisos(cache(trialFin = enSeisDias), aviso("DIA_0"))

        assertEquals("MENOS_7", repo.observarAvisoActual().first())
    }

    @Test
    fun `FR-017 - una cuenta pagada no calcula avisos de vencimiento`() = runTest {
        prepararAvisos(cache(plan = "STANDARD", estado = "PAGADO", trialFin = AHORA - 1_000))

        assertNull(repo.observarAvisoActual().first())
    }

    @Test
    fun `FR-017 - una cuenta pagada no muestra avisos ya guardados`() = runTest {
        prepararAvisos(
            cache(plan = "PREMIUM", estado = "PAGADO", trialFin = AHORA - 1_000),
            aviso("DIA_0"), aviso("TERMINO"), aviso("MAS_2")
        )

        assertNull(repo.observarAvisoActual().first())
    }

    @Test
    fun `FR-017 - una cuenta de pruebas no muestra avisos ya guardados`() = runTest {
        prepararAvisos(cache(plan = "PREMIUM", estado = "TESTER"), aviso("MENOS_7"))

        assertNull(repo.observarAvisoActual().first())
    }

    @Test
    fun `FR-017 - en Free no reaparece un aviso previo al vencimiento`() = runTest {
        prepararAvisos(
            cache(plan = "FREE", estado = "FREE", trialFin = AHORA - 1_000),
            aviso("DIA_0"), aviso("MENOS_1")
        )

        assertNull(repo.observarAvisoActual().first())
    }

    private fun itemCola(id: String, tabla: String, payload: String, rechazo: String? = null) = SyncEntity(
        id = id, entidad_tipo = tabla, entidad_id = id, operacion = OperacionSync.INSERT,
        payload = payload, rechazo_codigo = rechazo
    )

    private val colaRetenida = listOf(
        itemCola("b9", "bodegas", "{}", "SFP01"),
        itemCola("p9", "productos", """{"bodega_id":"b9"}"""),
        itemCola("m1", "movimientos", """{"producto_id":"p9"}"""),
        itemCola("m2", "movimientos", """{"producto_id":"p9"}""")
    )

    @Test
    fun `FR-023 - el resumen propio incluye a los dependientes del retenido`() = runTest {
        every { syncDao.observarCola() } returns flowOf(colaRetenida)

        assertEquals(
            listOf(
                RetenidoPorTipo("bodegas", 1),
                RetenidoPorTipo("movimientos", 2),
                RetenidoPorTipo("productos", 1)
            ),
            repo.observarRetenidosPropios().first()
        )
    }

    @Test
    fun `FR-023 - informa al servidor la cantidad con dependientes`() = runTest {
        coEvery { planCacheDao.obtener("empresa-1") } returns cache()
        coEvery { syncDao.obtenerCola() } returns colaRetenida
        coEvery { clientePlan.reportarRetenidos(any(), any(), any()) } returns true

        repo.informarRetenidos("token")

        coVerify(exactly = 1) { clientePlan.reportarRetenidos("token", "dispositivo-1", 4) }
    }

    @Test
    fun `FR-017 - cerrar marca este aviso y los anteriores y confirma en el servidor`() = runTest {
        coEvery { gestorSesion.obtenerToken(false) } returns ResultadoToken.Vigente(sesion)
        coEvery { avisoPlanDao.obtenerCierresPendientes("empresa-1") } returns
            listOf(aviso("DIA_0", cerrado = true), aviso("MENOS_7", cerrado = true))
        coEvery { clientePlan.cerrarAviso("token", "MENOS_7") } returns true

        repo.cerrarAviso("MENOS_7")

        coVerify { avisoPlanDao.cerrarLocal("empresa-1", listOf("DIA_0", "MENOS_7")) }
        coVerify(exactly = 1) { clientePlan.cerrarAviso("token", "MENOS_7") }
        coVerify { avisoPlanDao.confirmarCierre("empresa-1", "DIA_0") }
        coVerify { avisoPlanDao.confirmarCierre("empresa-1", "MENOS_7") }
    }

    @Test
    fun `FR-017 - si el servidor no responde el cierre queda pendiente`() = runTest {
        coEvery { gestorSesion.obtenerToken(false) } returns ResultadoToken.Vigente(sesion)
        coEvery { avisoPlanDao.obtenerCierresPendientes("empresa-1") } returns listOf(aviso("DIA_0", cerrado = true))
        coEvery { clientePlan.cerrarAviso(any(), any()) } returns false

        repo.cerrarAviso("DIA_0")

        coVerify(exactly = 0) { avisoPlanDao.confirmarCierre(any(), any()) }
    }

    @Test
    fun `clasifica las respuestas del servidor`() {
        val cuerpo = """{"bloqueado":false,"empresa_id":"e","rol":"ADMIN","plan_efectivo":"FREE","estado":"FREE","campo_nuevo":1,"ahora_servidor":"2026-11-21T12:00:00+00:00"}"""
        assertEquals("e", (clasificarRespuestaEntitlement(200, cuerpo) as RespuestaEntitlement.Ok).dto.empresa_id)
        assertEquals(RespuestaEntitlement.SinPlan, clasificarRespuestaEntitlement(404, """{"code":"PGRST202"}"""))
        assertEquals(RespuestaEntitlement.NoAutorizado, clasificarRespuestaEntitlement(401, "{}"))
        listOf(408, 429, 500, 503).forEach {
            assertEquals(RespuestaEntitlement.SinConexion, clasificarRespuestaEntitlement(it, "{}"))
        }
        assertTrue(clasificarRespuestaEntitlement(400, """{"code":"P0001"}""") is RespuestaEntitlement.Error)
        assertTrue(clasificarRespuestaEntitlement(200, "no-json") is RespuestaEntitlement.Error)
    }

    private companion object {
        const val AHORA = 1_800_000_000_000L
    }
}
