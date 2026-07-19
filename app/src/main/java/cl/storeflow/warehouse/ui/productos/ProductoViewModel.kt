package cl.storeflow.warehouse.ui.productos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.local.entity.ProductoEntity
import cl.storeflow.warehouse.data.repository.AtributoRepository
import cl.storeflow.warehouse.data.repository.BodegaRepository
import cl.storeflow.warehouse.data.repository.ProductoRepository
import cl.storeflow.warehouse.domain.model.AtributoTemplate
import cl.storeflow.warehouse.domain.model.Bodega
import cl.storeflow.warehouse.domain.model.Producto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class ProductosUiState {
    object Cargando : ProductosUiState()
    data class Listo(val productos: List<Producto>) : ProductosUiState()
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
    private val repository: ProductoRepository,
    private val atributoRepository: AtributoRepository,
    private val bodegaRepository: BodegaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var empresa_id = ""
    private var bodega_id = ""

    private val _uiState = MutableStateFlow<ProductosUiState>(ProductosUiState.Cargando)
    val uiState: StateFlow<ProductosUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow<FormUiState>(FormUiState.Idle)
    val formState: StateFlow<FormUiState> = _formState.asStateFlow()

    // Prellenado opcional al llegar desde la búsqueda global del Dashboard
    private val _busqueda = MutableStateFlow(savedStateHandle.get<String>("busqueda") ?: "")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

    private val _todosLosProductos = MutableStateFlow<List<Producto>>(emptyList())

    val productosFiltrados: StateFlow<List<Producto>> = combine(
        _todosLosProductos, _busqueda
    ) { lista, query ->
        if (query.isBlank()) lista
        else lista.filter {
            it.nombre.contains(query, ignoreCase = true) ||
            it.sku?.contains(query, ignoreCase = true) == true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tamanioPagina = MutableStateFlow(25)
    val tamanioPagina: StateFlow<Int> = _tamanioPagina.asStateFlow()

    private val _cantidadVisible = MutableStateFlow(_tamanioPagina.value)

    val productosVisibles: StateFlow<List<Producto>> = combine(
        productosFiltrados, _cantidadVisible
    ) { lista, cantidad -> lista.take(cantidad) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hayMas: StateFlow<Boolean> = combine(
        productosFiltrados, _cantidadVisible
    ) { lista, cantidad -> cantidad < lista.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setTamanioPagina(tamanio: Int) {
        _tamanioPagina.value = tamanio
        _cantidadVisible.value = tamanio
    }

    fun cargarMas() {
        _cantidadVisible.value += _tamanioPagina.value
    }

    private val _templates = MutableStateFlow<List<AtributoTemplate>>(emptyList())
    val templates: StateFlow<List<AtributoTemplate>> = _templates.asStateFlow()

    private val _productoEditando = MutableStateFlow<Producto?>(null)
    val productoEditando: StateFlow<Producto?> = _productoEditando.asStateFlow()
    private var editJob: Job? = null

    private val _seleccionados = MutableStateFlow<Set<String>>(emptySet())
    val seleccionados: StateFlow<Set<String>> = _seleccionados.asStateFlow()

    val modoSeleccion: StateFlow<Boolean> = _seleccionados
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _bodegas = MutableStateFlow<List<Bodega>>(emptyList())
    val bodegas: StateFlow<List<Bodega>> = _bodegas.asStateFlow()

    init {
        viewModelScope.launch {
            val ctx = repository.obtenerContexto()
            if (ctx == null) {
                _uiState.value = ProductosUiState.Error("No hay sesión activa")
                return@launch
            }
            empresa_id = ctx.first
            bodega_id = ctx.second
            Timber.d("VIEWMODEL: observando bodega_id=$bodega_id")
            repository.observarProductos(bodega_id).collect { lista ->
                Timber.d("VIEWMODEL: Flow emitió ${lista.size} productos para bodega_id=$bodega_id")
                _todosLosProductos.value = lista
                _uiState.value = ProductosUiState.Listo(lista)
            }
        }
        viewModelScope.launch {
            atributoRepository.observarTemplates().collect { _templates.value = it }
        }
        viewModelScope.launch {
            bodegaRepository.observarBodegas().collect { _bodegas.value = it }
        }
    }

    fun seleccionarParaEditar(productoId: String) {
        editJob?.cancel()
        editJob = viewModelScope.launch {
            repository.observarProducto(productoId).collect {
                _productoEditando.value = it
            }
        }
    }

    fun limpiarEdicion() {
        editJob?.cancel()
        editJob = null
        _productoEditando.value = null
    }

    fun toggleSeleccion(id: String) {
        _seleccionados.update { set -> if (id in set) set - id else set + id }
    }

    fun seleccionarTodos() {
        _seleccionados.value = productosVisibles.value.map { it.id }.toSet()
    }

    fun limpiarSeleccion() { _seleccionados.value = emptySet() }

    fun eliminarSeleccionados() {
        val ids = _seleccionados.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _formState.value = FormUiState.Cargando
            repository.eliminarVarios(ids)
                .onSuccess {
                    _seleccionados.value = emptySet()
                    _formState.value = FormUiState.Guardado("${ids.size} producto${if (ids.size != 1) "s" else ""} eliminado${if (ids.size != 1) "s" else ""}")
                }
                .onFailure { _formState.value = FormUiState.Error(it.message ?: "Error al eliminar") }
        }
    }

    fun transferirSeleccionados(bodegaDestino: String, nombreBodega: String) {
        val ids = _seleccionados.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _formState.value = FormUiState.Cargando
            repository.transferirSeleccionados(ids, bodegaDestino)
                .onSuccess {
                    _seleccionados.value = emptySet()
                    _formState.value = FormUiState.Guardado("${ids.size} producto${if (ids.size != 1) "s" else ""} transferido${if (ids.size != 1) "s" else ""} a $nombreBodega")
                }
                .onFailure { _formState.value = FormUiState.Error(it.message ?: "Error al transferir") }
        }
    }

    fun setBusqueda(query: String) {
        _busqueda.value = query
        _cantidadVisible.value = _tamanioPagina.value
    }

    private fun skuYaExiste(sku: String, excludeId: String? = null): Boolean =
        _todosLosProductos.value.any {
            it.sku?.trim()?.equals(sku.trim(), ignoreCase = true) == true && it.id != excludeId
        }

    fun crear(
        nombre: String,
        descripcion: String?,
        sku: String?,
        precio: Int,
        stock_minimo: Int,
        stock_inicial: Int = 0,
        atributos: Map<String, String> = emptyMap()
    ) {
        if (stock_inicial > 0 && stock_inicial < stock_minimo) {
            _formState.value = FormUiState.Error("El stock inicial no puede ser menor al stock mínimo ($stock_minimo)")
            return
        }
        if (!sku.isNullOrBlank() && skuYaExiste(sku)) {
            _formState.value = FormUiState.Error("Ya existe un producto con el SKU \"$sku\"")
            return
        }
        viewModelScope.launch {
            _formState.value = FormUiState.Cargando
            repository.crear(empresa_id, bodega_id, nombre, descripcion, sku, precio, stock_minimo, stock_inicial, atributos)
                .onSuccess { _formState.value = FormUiState.Guardado("Producto creado") }
                .onFailure { _formState.value = FormUiState.Error(it.message ?: "Error al guardar") }
        }
    }

    fun actualizar(
        producto: Producto,
        nombre: String,
        descripcion: String?,
        sku: String?,
        precio: Int,
        stock_minimo: Int,
        atributos: Map<String, String> = emptyMap()
    ) {
        if (!sku.isNullOrBlank() && skuYaExiste(sku, excludeId = producto.id)) {
            _formState.value = FormUiState.Error("Ya existe un producto con el SKU \"$sku\"")
            return
        }
        viewModelScope.launch {
            _formState.value = FormUiState.Cargando
            val entity = ProductoEntity(
                id = producto.id,
                empresa_id = producto.empresaId,
                bodega_id = producto.bodegaId,
                nombre = nombre.trim(),
                descripcion = descripcion?.trim()?.ifBlank { null },
                sku = sku?.trim()?.ifBlank { null },
                precio = precio,
                stock_minimo = stock_minimo,
                synced = false
            )
            repository.actualizar(entity, atributos)
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
