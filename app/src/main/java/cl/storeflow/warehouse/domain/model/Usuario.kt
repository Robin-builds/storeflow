package cl.storeflow.warehouse.domain.model

data class Usuario(
    val id: String,
    val nombre: String,
    val email: String,
    val rol: Rol,
    val empresaId: String
) {
    fun esAdmin(): Boolean = rol == Rol.ADMIN
    fun puedeGestionarBodegas(): Boolean = esAdmin()
    fun puedeEliminarProductos(): Boolean = esAdmin()
    fun puedeRegistrarMovimientos(): Boolean = true
}
