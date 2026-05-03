package cl.stockflow.warehouse.domain.model

enum class Rol {
    ADMIN, OPERADOR;

    companion object {
        fun fromString(value: String): Rol = when (value.uppercase()) {
            "ADMIN" -> ADMIN
            else -> OPERADOR
        }
    }
}
