package cl.storeflow.warehouse.domain.model

data class AtributoTemplate(
    val id: String,
    val empresaId: String,
    val clave: String,
    val etiqueta: String,
    val tipo: TipoAtributo,
    val obligatorio: Boolean,
    val orden: Int
)

enum class TipoAtributo { TEXT, NUMBER, DATE }
