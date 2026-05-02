package cl.stockflow.warehouse.data.repository

import cl.stockflow.warehouse.data.local.dao.AtributoTemplateDao
import cl.stockflow.warehouse.data.local.dao.AuthSessionDao
import cl.stockflow.warehouse.data.local.entity.AtributoTemplateEntity
import cl.stockflow.warehouse.domain.model.AtributoTemplate
import cl.stockflow.warehouse.domain.model.TipoAtributo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AtributoRepository @Inject constructor(
    private val atributoTemplateDao: AtributoTemplateDao,
    private val authSessionDao: AuthSessionDao
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarTemplates(): Flow<List<AtributoTemplate>> = authSessionDao.observarSesion()
        .flatMapLatest { sesion ->
            if (sesion == null) flowOf(emptyList())
            else atributoTemplateDao.observarPorEmpresa(sesion.empresa_id)
                .map { entities -> entities.map { it.toDomain() } }
        }

    suspend fun crear(
        clave: String,
        etiqueta: String,
        tipo: TipoAtributo,
        obligatorio: Boolean,
        orden: Int
    ): Result<Unit> {
        val empresaId = authSessionDao.obtenerSesion()?.empresa_id
            ?: return Result.failure(Exception("Sin sesión activa"))
        return try {
            atributoTemplateDao.insertar(
                AtributoTemplateEntity(
                    empresa_id = empresaId,
                    clave = clave.trim(),
                    etiqueta = etiqueta.trim(),
                    tipo = tipo.name,
                    obligatorio = obligatorio,
                    orden = orden
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminar(template: AtributoTemplate): Result<Unit> = try {
        atributoTemplateDao.eliminar(
            AtributoTemplateEntity(
                id = template.id,
                empresa_id = template.empresaId,
                clave = template.clave,
                etiqueta = template.etiqueta,
                tipo = template.tipo.name,
                obligatorio = template.obligatorio,
                orden = template.orden
            )
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun AtributoTemplateEntity.toDomain() = AtributoTemplate(
        id = id,
        empresaId = empresa_id,
        clave = clave,
        etiqueta = etiqueta,
        tipo = runCatching { TipoAtributo.valueOf(tipo) }.getOrDefault(TipoAtributo.TEXT),
        obligatorio = obligatorio,
        orden = orden
    )
}
