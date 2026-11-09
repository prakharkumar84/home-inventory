package com.prakharkumar.homeinventory.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prakharkumar.homeinventory.AppContainer
import com.prakharkumar.homeinventory.domain.model.SearchResult
import com.prakharkumar.homeinventory.domain.repository.ItemRepository
import com.prakharkumar.homeinventory.ui.components.EmptyState
import com.prakharkumar.homeinventory.ui.components.ItemThumbnail
import com.prakharkumar.homeinventory.util.formatQuantity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Spec 2.3 / 10 - offline SQLite search with full location breadcrumbs. */
@OptIn(FlowPreview::class)
class SearchViewModel(private val itemRepository: ItemRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results = _results.asStateFlow()

    private val _searched = MutableStateFlow(false)
    val searched = _searched.asStateFlow()

    init {
        viewModelScope.launch {
            _query.debounce(200).distinctUntilChanged().collect { q ->
                if (q.isBlank()) {
                    _results.value = emptyList()
                    _searched.value = false
                } else {
                    _results.value = itemRepository.search(q)
                    _searched.value = true
                }
            }
        }
    }

    fun onQueryChange(value: String) { _query.value = value }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SearchViewModel(container.itemRepository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val searched by viewModel.searched.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Item, category, note, serial or barcode") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester)
            )

            when {
                query.isBlank() -> EmptyState(
                    title = "Find anything you stored",
                    subtitle = "Try “HDMI cable”, “screwdriver” or “AA batteries”. Search works fully offline."
                )
                searched && results.isEmpty() -> EmptyState(
                    title = "No matches",
                    subtitle = "Nothing stored under that name yet. Check the spelling or add the item."
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.item.id }) { result ->
                        ListItem(
                            leadingContent = { ItemThumbnail(result.item.photoPath) },
                            headlineContent = { Text(result.item.name) },
                            supportingContent = {
                                Column {
                                    Text(
                                        result.breadcrumb,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        listOfNotNull(
                                            result.item.category,
                                            "Qty ${formatQuantity(result.item.quantity)}"
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onOpenItem(result.item.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
