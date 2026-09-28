package it.charitymarket.android.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.shared.api.CreateItemRequest
import it.charitymarket.shared.api.DonorResponse
import it.charitymarket.shared.api.ItemCondition
import it.charitymarket.shared.api.ItemResponse
import it.charitymarket.shared.api.ItemStatus
import it.charitymarket.shared.api.UpdateItemRequest
import it.charitymarket.android.app.AndroidAppState
import it.charitymarket.android.components.ConfirmationDialog
import it.charitymarket.android.components.CurrencyField
import it.charitymarket.android.components.EmptyStatePanel
import it.charitymarket.android.components.ErrorPanel
import it.charitymarket.android.components.FeedbackMessage
import it.charitymarket.android.components.LoadingPanel
import it.charitymarket.android.components.ScreenHeader
import it.charitymarket.android.components.SearchField
import it.charitymarket.android.components.StatusBadge
import it.charitymarket.shared.auth.RoleRules
import it.charitymarket.android.model.centsToInput
import it.charitymarket.android.model.formatInstant
import it.charitymarket.android.model.formatMoney
import it.charitymarket.android.model.parseMoneyToCents
import kotlinx.coroutines.launch

@Composable
fun ItemsScreen(appState: AndroidAppState) {
    val api = appState.requireApiClient()
    val roles = appState.uiState.authenticatedUser?.roles.orEmpty()
    val canEdit = RoleRules.canEditInventory(roles)
    var inventory by remember { mutableStateOf<List<ItemResponse>>(emptyList()) }
    var donors by remember { mutableStateOf<List<DonorResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<ItemStatus?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ItemResponse?>(null) }
    var editTarget by remember { mutableStateOf<ItemResponse?>(null) }
    var deleteTarget by remember { mutableStateOf<ItemResponse?>(null) }
    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, appState.uiState.autoRefreshGeneration) {
        if (!hasLoaded) loading = true
        error = null
        val itemResult = runCatching { api.listItems() }
        val donorResult = runCatching { api.listDonors() }
        itemResult.onSuccess {
            if (inventory != it) inventory = it
        }
            .onFailure { error = appState.handleRequestFailure(it) }
        donorResult.onSuccess {
            if (donors != it) donors = it
        }
            .onFailure {
                if (error == null) {
                    error = "Could not load donors: " +
                            appState.handleRequestFailure(it)
                }
            }
        loading = false
        hasLoaded = true
    }

    val filtered = inventory.filter { item ->
        val query = search.trim()
        (query.isBlank() ||
                item.code.contains(query, true) ||
                item.name.contains(query, true) ||
                item.donorName.contains(query, true)) &&
                (status == null || item.status == status)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            "Items",
            "Donated inventory and availability.",
            "Refresh",
            !loading
        ) { refreshKey++ }
        if (canEdit) {
            Button(
                onClick = { showCreate = true },
                enabled = donors.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create item") }
        }
        SearchField(
            search,
            { search = it },
            "Search code, name, or donor",
            Modifier.fillMaxWidth()
        )
        StatusFilter(status) { status = it }

        when {
            loading -> LoadingPanel("Loading items...")
            error != null -> ErrorPanel(error.orEmpty()) { refreshKey++ }
            filtered.isEmpty() -> EmptyStatePanel(
                if (inventory.isEmpty()) "No items yet" else "No matching items",
                if (inventory.isEmpty()) {
                    "Create a donor, then add an item."
                } else "Adjust the search or status filter."
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selected = item }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(item.code)
                                }
                                StatusBadge(
                                    item.status.name,
                                    if (item.status == ItemStatus.AVAILABLE) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text("Donor: ${item.donorName}")
                            Text("Condition: ${item.condition.name}")
                            Text(
                                formatMoney(
                                    item.suggestedPriceCents,
                                    appState.uiState.currencyCode
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        ItemFormDialog(
            "Create item",
            null,
            donors,
            appState,
            onDismiss = { showCreate = false },
            onSubmit = { code, name, donor, condition, cents, comments ->
                api.createItem(
                    CreateItemRequest(
                        code.ifBlank { null },
                        name,
                        donor,
                        condition,
                        cents,
                        comments
                    )
                )
            },
            onSaved = {
                showCreate = false
                refreshKey++
                appState.showMessage("Item created: ${it.code}")
            }
        )
    }
    selected?.let { item ->
        ItemDetailsDialog(
            item,
            appState.uiState.currencyCode,
            canEdit,
            onEdit = {
                selected = null
                editTarget = item
            },
            onDelete = {
                selected = null
                deleteTarget = item
            },
            onDismiss = { selected = null }
        )
    }
    editTarget?.let { item ->
        ItemFormDialog(
            "Edit item",
            item,
            donors,
            appState,
            onDismiss = { editTarget = null },
            onSubmit = { code, name, donor, condition, cents, comments ->
                api.updateItem(
                    item.id,
                    UpdateItemRequest(
                        code,
                        name,
                        donor,
                        condition,
                        cents,
                        comments
                    )
                )
            },
            onSaved = {
                editTarget = null
                refreshKey++
                appState.showMessage("Item updated: ${it.code}")
            }
        )
    }
    deleteTarget?.let { item ->
        DeleteItemDialog(
            item,
            appState,
            onDismiss = { deleteTarget = null },
            onDeleted = {
                deleteTarget = null
                refreshKey++
                appState.showMessage("Item deleted: ${item.code}")
            }
        )
    }
}

@Composable
private fun StatusFilter(
    selected: ItemStatus?,
    onSelected: (ItemStatus?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (listOf<ItemStatus?>(null) + ItemStatus.entries).forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = option == selected,
                    onClick = { onSelected(option) }
                )
                Text(option?.name ?: "ALL")
            }
        }
    }
}

@Composable
private fun ItemFormDialog(
    title: String,
    initial: ItemResponse?,
    donors: List<DonorResponse>,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onSubmit: suspend (
        String,
        String,
        String,
        ItemCondition,
        Long,
        String?
    ) -> ItemResponse,
    onSaved: (ItemResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var donorSearch by remember {
        mutableStateOf(initial?.donorName.orEmpty())
    }
    var donor by remember {
        mutableStateOf(
            initial?.let { value ->
                donors.firstOrNull { it.id == value.donorId }
            }
        )
    }
    var condition by remember {
        mutableStateOf(initial?.condition ?: ItemCondition.GOOD)
    }
    var price by remember {
        mutableStateOf(
            initial?.suggestedPriceCents?.let(::centsToInput).orEmpty()
        )
    }
    var comments by remember { mutableStateOf(initial?.comments.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val cents = parseMoneyToCents(price)
    val matchingDonors = donors.filter {
        donorSearch.isBlank() ||
                it.name.contains(donorSearch, true) ||
                it.email.orEmpty().contains(donorSearch, true)
    }

    fun submit() {
        if (busy) return
        error = when {
            initial != null && code.trim().isBlank() ->
                "The item code is required."
            code.length > 50 -> "The item code cannot exceed 50 characters."
            name.trim().isBlank() -> "The item name is required."
            donor == null -> "Select a donor."
            cents == null -> "Enter a valid non-negative price."
            comments.length > 4000 -> "Comments cannot exceed 4000 characters."
            else -> null
        }
        if (error != null) return
        scope.launch {
            busy = true
            runCatching {
                onSubmit(
                    code.trim(),
                    name.trim(),
                    donor!!.id,
                    condition,
                    cents!!,
                    comments.trim().ifBlank { null }
                )
            }.onSuccess(onSaved)
                .onFailure { error = appState.handleRequestFailure(it) }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    code,
                    { code = it.take(50) },
                    label = { Text("Item code") },
                    supportingText = if (initial == null) {
                        { Text("Leave blank to generate one.") }
                    } else null,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    name,
                    { name = it.take(200) },
                    label = { Text("Item name") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                SearchField(
                    donorSearch,
                    {
                        donorSearch = it
                        if (donor?.name != it) donor = null
                    },
                    "Search donors",
                    Modifier.fillMaxWidth(),
                    !busy
                )
                Card(
                    Modifier.fillMaxWidth().heightIn(max = 150.dp)
                ) {
                    LazyColumn {
                        items(matchingDonors, key = { it.id }) { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = !busy) {
                                        donor = option
                                        donorSearch = option.name
                                    }.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = donor?.id == option.id,
                                    onClick = {
                                        donor = option
                                        donorSearch = option.name
                                    },
                                    enabled = !busy
                                )
                                Text(option.name)
                            }
                        }
                    }
                }
                Text("Condition", style = MaterialTheme.typography.titleSmall)
                ItemCondition.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = condition == option,
                            onClick = { condition = option },
                            enabled = !busy
                        )
                        Text(option.name)
                    }
                }
                CurrencyField(
                    price,
                    { price = it },
                    "Suggested price",
                    !busy,
                    Modifier.fillMaxWidth(),
                    price.isNotBlank() && cents == null
                )
                OutlinedTextField(
                    comments,
                    { comments = it.take(4000) },
                    label = { Text("Comments") },
                    supportingText = { Text("${comments.length}/4000") },
                    minLines = 3,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                FeedbackMessage(error, true)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !busy) {
                Text(if (busy) "Saving..." else "Save")
            }
        }
    )
}

@Composable
private fun ItemDetailsDialog(
    item: ItemResponse,
    currencyCode: String,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.code) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Name: ${item.name}")
                Text("Donor: ${item.donorName}")
                Text("Condition: ${item.condition.name}")
                Text("Status: ${item.status.name}")
                Text("Price: ${formatMoney(item.suggestedPriceCents, currencyCode)}")
                Text("Comments: ${item.comments ?: "—"}")
                Text("Created: ${formatInstant(item.createdAt)}")
                Text("Updated: ${formatInstant(item.updatedAt)}")
                Text("ID: ${item.id}")
            }
        },
        dismissButton = {
            if (canEdit) {
                Row {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DeleteItemDialog(
    item: ItemResponse,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ConfirmationDialog(
        "Delete item?",
        "${item.code} can be deleted only when no sale references it.",
        "Delete",
        busy,
        "Deleting item...",
        onDismiss
    ) {
        if (!busy) {
            scope.launch {
                busy = true
                runCatching {
                    appState.requireApiClient().deleteItem(item.id)
                }.onSuccess { onDeleted() }
                    .onFailure { error = appState.handleRequestFailure(it) }
                busy = false
            }
        }
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Delete failed") },
            text = { Text(error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { error = null }) { Text("Close") }
            }
        )
    }
}
