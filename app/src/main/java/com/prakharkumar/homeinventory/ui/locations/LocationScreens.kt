package com.prakharkumar.homeinventory.ui.locations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prakharkumar.homeinventory.AppContainer
import com.prakharkumar.homeinventory.domain.model.InventoryItem
import com.prakharkumar.homeinventory.domain.model.LocationNode
import com.prakharkumar.homeinventory.domain.model.LocationType
import com.prakharkumar.homeinventory.domain.model.StorageLocation
import com.prakharkumar.homeinventory.domain.repository.DeleteCheck
import com.prakharkumar.homeinventory.domain.repository.ItemRepository
import com.prakharkumar.homeinventory.domain.repository.LocationRepository
import com.prakharkumar.homeinventory.ui.components.EmptyState
import com.prakharkumar.homeinventory.ui.components.ItemThumbnail
import com.prakharkumar.homeinventory.ui.components.label
import com.prakharkumar.homeinventory.ui.home.CreateLocationDialog
import com.prakharkumar.homeinventory.util.formatQuantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- tree screen

class LocationTreeViewModel(
    private val locationRepository: LocationRepository,
    private val itemRepository: ItemRepository
) : ViewModel() {

    val tree: StateFlow<List<LocationNode>> = locationRepository.observeTree()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingDelete = MutableStateFlow<Pair<StorageLocation, DeleteCheck>?>(null)
    val pendingDelete = _pendingDelete.asStateFlow()

    fun addChild(parentId: String?, name: String, type: LocationType) {
        viewModelScope.launch { locationRepository.create(name, parentId, type, null) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { locationRepository.rename(id, name, null) }
    }

    fun requestDelete(location: StorageLocation) {
        viewModelScope.launch {
            _pendingDelete.value = location to locationRepository.canDelete(location.id)
        }
    }

    fun dismissDelete() { _pendingDelete.value = null }

    /** Spec 2.1 - never silently orphan items; move them to "Unassigned" instead. */
    fun confirmDeleteMovingItems() {
        val target = _pendingDelete.value ?: return
        viewModelScope.launch {
            val unassigned = locationRepository.ensureUnassignedLocation()
            itemRepository.reassignAll(target.first.id, unassigned)
            locationRepository.deleteMovingItemsTo(target.first.id, unassigned)
            _pendingDelete.value = null
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LocationTreeViewModel(container.locationRepository, container.itemRepository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTreeScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenLocation: (String) -> Unit,
    viewModel: LocationTreeViewModel = viewModel(factory = LocationTreeViewModel.factory(container))
) {
    val tree by viewModel.tree.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var addChildTo by remember { mutableStateOf<StorageLocation?>(null) }
    var addRoot by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addRoot = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add space")
            }
        }
    ) { padding ->
        if (tree.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState(
                    title = "No locations yet",
                    subtitle = "Start with a space like “My Home”, then nest rooms, cupboards, drawers and boxes inside it."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val flattened = flatten(tree, expanded)
                items(flattened, key = { it.location.id }) { node ->
                    LocationRow(
                        node = node,
                        isExpanded = expanded[node.location.id] ?: true,
                        onToggle = {
                            expanded[node.location.id] = !(expanded[node.location.id] ?: true)
                        },
                        onOpen = { onOpenLocation(node.location.id) },
                        onAddChild = { addChildTo = node.location },
                        onDelete = { viewModel.requestDelete(node.location) }
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (addRoot) {
        CreateLocationDialog(
            title = "New space",
            defaultType = LocationType.HOME,
            onDismiss = { addRoot = false },
            onConfirm = { name, type ->
                viewModel.addChild(null, name, type)
                addRoot = false
            }
        )
    }

    addChildTo?.let { parent ->
        CreateLocationDialog(
            title = "Add inside ${parent.name}",
            defaultType = LocationType.DRAWER,
            onDismiss = { addChildTo = null },
            onConfirm = { name, type ->
                viewModel.addChild(parent.id, name, type)
                addChildTo = null
            }
        )
    }

    pendingDelete?.let { (location, check) ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete ${location.name}?") },
            text = {
                Text(
                    when {
                        check.isSafe -> "This location is empty and will be removed."
                        check.childLocations > 0 ->
                            "This location still contains ${check.childLocations} sub-location(s). Remove or move them first so nothing is lost."
                        else ->
                            "${check.items} item(s) are stored here. They will be moved to “Unassigned” instead of being deleted."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteMovingItems() },
                    enabled = check.childLocations == 0
                ) { Text(if (check.items > 0) "Move items & delete" else "Delete") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") } }
        )
    }
}

private fun flatten(
    nodes: List<LocationNode>,
    expanded: Map<String, Boolean>
): List<LocationNode> {
    val out = mutableListOf<LocationNode>()
    fun walk(list: List<LocationNode>) {
        list.forEach { node ->
            out += node
            if (expanded[node.location.id] ?: true) walk(node.children)
        }
    }
    walk(nodes)
    return out
}

@Composable
private fun LocationRow(
    node: LocationNode,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = (8 + node.depth * 16).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.children.isNotEmpty()) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(node.location.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${node.location.type.label()} · ${node.location.itemCount} items",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onAddChild) {
            Icon(Icons.Default.Add, contentDescription = "Add sub-location")
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Delete location") },
                onClick = { menu = false; onDelete() }
            )
        }
    }
}

// -------------------------------------------------------------- detail screen

data class LocationDetailState(
    val location: StorageLocation? = null,
    val breadcrumb: String = "",
    val children: List<StorageLocation> = emptyList(),
    val items: List<InventoryItem> = emptyList()
)

class LocationDetailViewModel(
    private val locationId: String,
    private val locationRepository: LocationRepository,
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val breadcrumb = MutableStateFlow("")

    val state = combine(
        locationRepository.observeLocation(locationId),
        locationRepository.observeChildren(locationId),
        itemRepository.observeByLocation(locationId),
        breadcrumb
    ) { location, children, items, crumb ->
        LocationDetailState(location, crumb, children, items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationDetailState())

    init {
        viewModelScope.launch { breadcrumb.value = locationRepository.breadcrumb(locationId) }
    }

    fun addChild(name: String, type: LocationType) {
        viewModelScope.launch { locationRepository.create(name, locationId, type, null) }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            locationRepository.rename(locationId, name, null)
            breadcrumb.value = locationRepository.breadcrumb(locationId)
        }
    }

    companion object {
        fun factory(container: AppContainer, locationId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LocationDetailViewModel(
                locationId, container.locationRepository, container.itemRepository
            ) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailScreen(
    container: AppContainer,
    locationId: String,
    onBack: () -> Unit,
    onOpenLocation: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onAddItems: () -> Unit,
    viewModel: LocationDetailViewModel = viewModel(
        key = "location-$locationId",
        factory = LocationDetailViewModel.factory(container, locationId)
    )
) {
    val state by viewModel.state.collectAsState()
    var addChild by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.location?.name ?: "Location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { addChild = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add sub-location")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItems,
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                text = { Text("Add items here") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    state.breadcrumb,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (state.children.isNotEmpty()) {
                item {
                    Text(
                        "Inside this location",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(state.children, key = { "child-${it.id}" }) { child ->
                    ListItem(
                        headlineContent = { Text(child.name) },
                        supportingContent = { Text("${child.type.label()} · ${child.itemCount} items") },
                        modifier = Modifier.clickable { onOpenLocation(child.id) }
                    )
                    HorizontalDivider()
                }
            }

            item {
                Text(
                    "Items stored here (${state.items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }

            if (state.items.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing here yet",
                        subtitle = "Photograph what you put away and confirm the suggestions."
                    )
                }
            } else {
                items(state.items, key = { "item-${it.id}" }) { item ->
                    ListItem(
                        leadingContent = { ItemThumbnail(item.photoPath) },
                        headlineContent = { Text(item.name) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    item.category,
                                    "Qty ${formatQuantity(item.quantity)}"
                                ).joinToString(" · ")
                            )
                        },
                        modifier = Modifier.clickable { onOpenItem(item.id) }
                    )
                    HorizontalDivider()
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (addChild) {
        CreateLocationDialog(
            title = "Add inside ${state.location?.name.orEmpty()}",
            defaultType = LocationType.DRAWER,
            onDismiss = { addChild = false },
            onConfirm = { name, type ->
                viewModel.addChild(name, type)
                addChild = false
            }
        )
    }

    if (renaming) {
        var newName by remember { mutableStateOf(state.location?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename location") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.rename(newName.trim()); renaming = false },
                    enabled = newName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }
}
