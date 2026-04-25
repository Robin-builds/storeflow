package cl.stockflow.warehouse.ui.productos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.stockflow.warehouse.data.local.entity.ProductoEntity
import cl.stockflow.warehouse.data.repository.ProductoRepository
import cl.stockflow.warehouse.domain.model.ProductoConStock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProductosUiState {
    object Cargando : ProductosUiState()
    data class Listo(val productos: List<ProductoConStock>) : ProductosUiState()
    data class Error(val mensaje: String) : ProductosUiState()
}

sealed class FormUiState {
    object Idle : FormUiState()
    object Cargando : FormUiState()
    data class Guardado(val mensaje: String) : FormUiState()
    data class Error(val mensaje: String) : FormUiState()
}

@HiltViewModel
class ProductoViewModel @Inject constructor(
    private val repository: ProductoRepository
) : ViewModel() {

    private var empresa_id = ""
    private var bodega_id = ""

    private val _uiState = MutableStateFlow<ProductosUiState>(ProductosUiState.Cargando)
    val uiState: StateFlow<ProductosUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow<FormUiState>(FormUiState.Idle)
    val formState: StateFlow<FormUiState> = _formState.asStateFlow()

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

    private val _todosLosProductos = MutableStateFlow<List<ProductoConStock>>(emptyList())

    val productosFiltrados: StateFlow<List<ProductoConStock>> = combine(
        _todosLosProductos, _busqueda
    ) { lista, query ->
        if (query.isBlank()) lista
        else lista.filter { it.nombre.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val ctx = repository.obtenerContexto()
            if (ctx == null) {
                _uiState.value = ProductosUiState.Error("No hay sesión activa")
                return@launch
            }
            empresa_id = ctx.first
            bodega_id = ctx.second
            repository.observarProductos(bodega_id).collect { lista ->
                _todosLosProductos.value = lista
                _uiState.value = ProductosUiState.Listo(lista)
            }
        }
    }

    fun setBusqueda(query: String) { _busqueda.value = query }

    fun crear(
        nombre: String,
        descripcion: String?,
        sku: String?,
        precio: Double,
        stock_minimo: Int,
        stock_inicial: Int = 0
    ) {
        if (stock_inicial > 0 && stock_inicial < stock_minimo) {
            _formState.value = FormUiState.Error("El stock inicial no puede ser menor al stock mínimo ($stock_minimo)")
            return
        }
        viewModelScope.launch {
            _formState.value = FormUiState.Cargando
            repository.crear(empresa_id, bodega_id, nombre, descripcion, sku, precio, stock_minimo, stock_inicial)
                .onSuccess { _formState.value = FormUiState.Guardado("Producto creado") }
                .onFailure { _formState.value = FormUiState.Error(it.message ?: "Error al guardar") }
        }
    }

    fun actualizar(
        producto: ProductoConStock,
        nombre: String,
        descripcion: String?,
        sku: String?,
        precio: Double,
        stock_minimo: Int
    ) {
        viewModelScope.launch {
            _formState.value = FormUiState.Cargando
            val entity = ProductoEntity(
                id = producto.id,
                empresa_id = producto.empresa_id,
                bodega_id = producto.bodega_id,
                nombre = nombre.trim(),
                descripcion = descripcion?.trim()?.ifBlank { null },
                sku = sku?.trim()?.ifBlank { null },
                precio = precio,
                stock_minimo = stock_minimo,
                synced = false
            )
            repository.actualizar(entity)
                .onSuccess { _formState.value = FormUiState.Guardado("Producto actualizado") }
                .onFailure { _formState.value = FormUiState.Error(it.message ?: "Error al actualizar") }
        }
    }

    fun eliminar(productoId: String) {
        viewModelScope.launch {
            _formState.value = FormUiState.Cargando
            val producto = repository.obtenerPorId(productoId)
            if (producto == null) {
                _formState.value = FormUiState.Error("Producto no encontrado")
                return@launch
            }
            repository.eliminar(producto)
                .onSuccess { _formState.value = FormUiState.Guardado("Producto eliminado") }
                .onFailure { _formState.value = FormUiState.Error(it.message ?: "Error al eliminar") }
        }
    }

    fun limpiarFormState() { _formState.value = FormUiState.Idle }
}
