package cl.stockflow.warehouse.data.sync

import cl.stockflow.warehouse.data.local.entity.AtributoTemplateEntity
import cl.stockflow.warehouse.data.local.entity.BodegaEntity
import cl.stockflow.warehouse.data.local.entity.EmpresaEntity
import cl.stockflow.warehouse.data.local.entity.MovimientoEntity
import cl.stockflow.warehouse.data.local.entity.ProductoAtributoEntity
import cl.stockflow.warehouse.data.local.entity.ProductoEntity
import cl.stockflow.warehouse.data.local.entity.ProveedorEntity
import cl.stockflow.warehouse.data.local.entity.TipoMovimiento
import cl.stockflow.warehouse.data.local.entity.UsuarioEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.Date

private fun parseFecha(s: String?): Date = try {
    if (s == null) Date()
    else Date.from(OffsetDateTime.parse(s).toInstant())
} catch (e: Exception) {
    Date()
}

@Serializable
data class EmpresaDto(
    val id: String,
    val nombre: String,
    val rut: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toEntity() = EmpresaEntity(
        id = id, nombre = nombre, rut = rut ?: "",
        synced = true, synced_at = Date(),
        created_at = parseFecha(createdAt), updated_at = parseFecha(updatedAt)
    )
}

@Serializable
data class BodegaDto(
    val id: String,
    @SerialName("empresa_id") val empresaId: String,
    val nombre: String,
    val ubicacion: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toEntity() = BodegaEntity(
        id = id, empresa_id = empresaId, nombre = nombre, ubicacion = ubicacion,
        synced = true, synced_at = Date(),
        created_at = parseFecha(createdAt), updated_at = parseFecha(updatedAt)
    )
}

@Serializable
data class ProveedorDto(
    val id: String,
    @SerialName("empresa_id") val empresaId: String,
    val nombre: String,
    val contacto: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toEntity() = ProveedorEntity(
        id = id, empresa_id = empresaId, nombre = nombre, contacto = contacto,
        synced = true, synced_at = Date(),
        created_at = parseFecha(createdAt), updated_at = parseFecha(updatedAt)
    )
}

@Serializable
data class UsuarioDto(
    val id: String,
    @SerialName("empresa_id") val empresaId: String,
    val nombre: String? = null,
    val email: String,
    val rol: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toEntity() = UsuarioEntity(
        id = id, empresa_id = empresaId, nombre = nombre ?: "", email = email, rol = rol ?: "",
        synced = true, synced_at = Date(),
        created_at = parseFecha(createdAt), updated_at = parseFecha(updatedAt)
    )
}

@Serializable
data class ProductoDto(
    val id: String,
    @SerialName("empresa_id") val empresaId: String,
    @SerialName("bodega_id") val bodegaId: String,
    val nombre: String,
    val descripcion: String? = null,
    val sku: String? = null,
    val precio: Double = 0.0,
    @SerialName("stock_minimo") val stockMinimo: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toEntity() = ProductoEntity(
        id = id, empresa_id = empresaId, bodega_id = bodegaId,
        nombre = nombre, descripcion = descripcion, sku = sku,
        precio = precio.toInt(), stock_minimo = stockMinimo,
        synced = true, synced_at = Date(),
        created_at = parseFecha(createdAt), updated_at = parseFecha(updatedAt)
    )
}

@Serializable
data class AtributoTemplateDto(
    val id: String,
    @SerialName("empresa_id") val empresaId: String,
    val clave: String,
    val etiqueta: String,
    val tipo: String = "TEXT",
    val obligatorio: Boolean = false,
    val orden: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toEntity() = AtributoTemplateEntity(
        id = id, empresa_id = empresaId,
        clave = clave, etiqueta = etiqueta,
        tipo = tipo, obligatorio = obligatorio, orden = orden,
        synced = true, synced_at = Date(),
        created_at = parseFecha(createdAt), updated_at = parseFecha(updatedAt)
    )
}

@Serializable
data class ProductoAtributoDto(
    @SerialName("producto_id") val productoId: String,
    @SerialName("template_id") val templateId: String,
    val valor: String
) {
    fun toEntity() = ProductoAtributoEntity(
        producto_id = productoId,
        template_id = templateId,
        valor = valor
    )
}

@Serializable
data class MovimientoDto(
    val id: String,
    @SerialName("producto_id") val productoId: String,
    val tipo: String,
    val cantidad: Int,
    val nota: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toEntity() = MovimientoEntity(
        id = id, producto_id = productoId,
        tipo = TipoMovimiento.valueOf(tipo),
        cantidad = cantidad, nota = nota,
        synced = true, synced_at = Date(),
        created_at = parseFecha(createdAt), updated_at = parseFecha(updatedAt)
    )
}
