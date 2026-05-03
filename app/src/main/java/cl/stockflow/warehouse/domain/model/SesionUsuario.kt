package cl.stockflow.warehouse.domain.model

data class SesionUsuario(
    val user_id: String,
    val empresa_id: String,
    val access_token: String,
    val refresh_token: String,
    val expires_at: Long,
    val rol: Rol = Rol.OPERADOR
)
