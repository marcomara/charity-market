package it.charitymarket.desktop.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.desktop.api.CharityMarketApiClient
import it.charitymarket.desktop.api.CreateItemRequest
import it.charitymarket.desktop.api.DonorResponse
import it.charitymarket.desktop.api.ItemCondition
import it.charitymarket.desktop.api.ItemResponse
import it.charitymarket.desktop.api.ItemStatus
import it.charitymarket.desktop.api.UpdateItemRequest
import it.charitymarket.desktop.app.DesktopAppState
import it.charitymarket.desktop.components.ConfirmationDialog
import it.charitymarket.desktop.components.CurrencyField
import it.charitymarket.desktop.components.EmptyStatePanel
import it.charitymarket.desktop.components.ErrorPanel
import it.charitymarket.desktop.components.FeedbackMessage
import it.charitymarket.desktop.components.LoadingPanel
import it.charitymarket.desktop.components.ScreenHeader
import it.charitymarket.desktop.components.SearchField
import it.charitymarket.desktop.components.StatusBadge
import it.charitymarket.desktop.model.RoleRules
import it.charitymarket.desktop.model.formatInstant
import it.charitymarket.desktop.model.formatMoney
import it.charitymarket.desktop.model.parseMoneyToCents
import kotlinx.coroutines.launch

@Composable
fun ItemsScreen(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    currencyCode: String
) {
    val roles = appState.uiState.authenticatedUser?.roles.orEmpty()
    val canEdit = RoleRules.canEditItems(roles)
    val canDelete = RoleRules.canDeleteItems(roles)

    var inventory by remember {
        mutableStateOf<List<ItemResponse>>(emptyList())
    }
    var donors by remember {
        mutableStateOf<List<DonorResponse>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ItemStatus?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<ItemResponse?>(null) }
    var editTarget by remember { mutableStateOf<ItemResponse?>(null) }
    var deleteTarget by remember { mutableStateOf<ItemResponse?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    suspend fun load() {
        loading = true
        errorMessage = null

        val itemResult = runCatching { apiClient.listItems() }
        val donorResult = runCatching { apiClient.listDonors() }

        itemResult.onSuccess { inventory = it }
            .onFailure {
                errorMessage = appState.handleRequestFailure(it)
            }

        donorResult.onSuccess { donors = it }
            .onFailure {
                if (errorMessage == null) {
                    errorMessage =
                        "Could not load donors: " +
                                appState.handleRequestFailure(it)
                }
            }

        loading = false
    }

    LaunchedEffect(refreshKey) {
        load()
    }

    val filteredItems = inventory.filter { item ->
        val query = search.trim()
        (query.isBlank() ||
                item.code.contains(query, ignoreCase = true) ||
                item.name.contains(query, ignoreCase = true) ||
                item.donorName.contains(query, ignoreCase = true) ||
                item.comments.orEmpty().contains(query, ignoreCase = true)) &&
                (statusFilter == null || item.status == statusFilter)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Items",
            subtitle = "Donated inventory and availability.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { refreshKey += 1 },
                        enabled = !loading
                    ) {
                        Text("Refresh")
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        enabled = donors.isNotEmpty()
                    ) {
                        Text("Create item")
                    }
                }
            }
        )

        SearchField(
            value = search,
            onValueChange = { search = it },
            label = "Search by code, name, donor, or comments",
            modifier = Modifier.fillMaxWidth()
        )

        StatusFilter(
            selected = statusFilter,
            onSelected = { statusFilter = it }
        )

        FeedbackMessage(feedback, false)

        when {
            loading -> LoadingPanel("Loading items...")
            errorMessage != null -> ErrorPanel(
                errorMessage.orEmpty(),
                onRetry = { refreshKey += 1 }
            )
            filteredItems.isEmpty() -> EmptyStatePanel(
                title = if (inventory.isEmpty()) {
                    "No items yet"
                } else {
                    "No matching items"
                },
                message = if (inventory.isEmpty()) {
                    "Create a donor and then add an item."
                } else {
                    "Adjust the search or status filter."
                }
            )
            else -> ItemList(
                items = filteredItems,
                currencyCode = currencyCode,
                onSelect = { selectedItem = it }
            )
        }
    }

    if (showCreateDialog) {
        ItemFormDialog(
            title = "Create item",
            initial = null,
            donors = donors,
            currencyCode = currencyCode,
            appState = appState,
            onDismiss = { showCreateDialog = false },
            onSubmit = { code, name, donorId, condition, cents, comments ->
                apiClient.createItem(
                    CreateItemRequest(
                        code = code.ifBlank { null },
                        name = name,
                        donorId = donorId,
                        condition = condition,
                        suggestedPriceCents = cents,
                        comments = comments
                    )
                )
            },
            onSaved = { item ->
                inventory = inventory + item
                feedback = "Item created: ${item.code}"
                showCreateDialog = false
            }
        )
    }

    selectedItem?.let { item ->
        ItemDetailsDialog(
            item = item,
            currencyCode = currencyCode,
            canEdit = canEdit,
            canDelete = canDelete,
            onEdit = {
                selectedItem = null
                editTarget = item
            },
            onDelete = {
                selectedItem = null
                deleteTarget = item
            },
            onDismiss = { selectedItem = null }
        )
    }

    editTarget?.let { item ->
        ItemFormDialog(
            title = "Edit item",
            initial = item,
            donors = donors,
            currencyCode = currencyCode,
            appState = appState,
            onDismiss = { editTarget = null },
            onSubmit = { code, name, donorId, condition, cents, comments ->
                apiClient.updateItem(
                    item.id,
                    UpdateItemRequest(
                        code = code,
                        name = name,
                        donorId = donorId,
                        condition = condition,
                        suggestedPriceCents = cents,
                        comments = comments
                    )
                )
            },
            onSaved = { updated ->
                inventory = inventory.map {
                    if (it.id == updated.id) updated else it
                }
                feedback = "Item updated: ${updated.code}"
                editTarget = null
            }
        )
    }

    deleteTarget?.let { item ->
        DeleteItemDialog(
            item = item,
            apiClient = apiClient,
            appState = appState,
            onDismiss = { deleteTarget = null },
            onDeleted = {
                inventory = inventory.filterNot { it.id == item.id }
                feedback = "Item deleted: ${item.code}"
                deleteTarget = null
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Status:")
        (listOf<ItemStatus?>(null) + ItemStatus.entries)
            .forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selected == status,
                        onClick = { onSelected(status) }
                    )
                    Text(status?.name ?: "ALL")
                }
            }
    }
}

@Composable
private fun ItemList(
    items: List<ItemResponse>,
    currencyCode: String,
    onSelect: (ItemResponse) -> Unit
) {
    Card {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Code", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                Text("Name", Modifier.weight(1.4f), style = MaterialTheme.typography.labelLarge)
                Text("Donor", Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge)
                Text("Condition", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                Text("Price", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                Text("Status", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
            }

            HorizontalDivider()

            LazyColumn {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.code, Modifier.weight(0.8f))
                        Text(item.name, Modifier.weight(1.4f))
                        Text(item.donorName, Modifier.weight(1.2f))
                        Text(item.condition.name, Modifier.weight(0.8f))
                        Text(
                            formatMoney(item.suggestedPriceCents, currencyCode),
                            Modifier.weight(0.8f)
                        )
                        Row(Modifier.weight(0.8f)) {
                            StatusBadge(
                                item.status.name,
                                if (item.status == ItemStatus.AVAILABLE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ItemFormDialog(
    title: String,
    initial: ItemResponse?,
    donors: List<DonorResponse>,
    currencyCode: String,
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    onSubmit: suspend (
        code: String,
        name: String,
        donorId: String,
        condition: ItemCondition,
        cents: Long,
        comments: String?
    ) -> ItemResponse,
    onSaved: (ItemResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var donorSearch by remember {
        mutableStateOf(initial?.donorName.orEmpty())
    }
    var selectedDonor by remember {
        mutableStateOf(
            initial?.let { current ->
                donors.firstOrNull { it.id == current.donorId }
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
    var comments by remember {
        mutableStateOf(initial?.comments.orEmpty())
    }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val priceCents = parseMoneyToCents(price)
    val filteredDonors = donors.filter { donor ->
        donorSearch.isBlank() ||
                donor.name.contains(donorSearch, ignoreCase = true) ||
                donor.email.orEmpty().contains(donorSearch, ignoreCase = true)
    }

    fun submit() {
        if (submitting) return
        if (name.trim().isBlank()) {
            errorMessage = "The item name is required."
            return
        }
        if (initial != null && code.trim().isBlank()) {
            errorMessage = "The item code is required."
            return
        }
        val donor = selectedDonor
        if (donor == null) {
            errorMessage = "Select a donor."
            return
        }
        val cents = priceCents
        if (cents == null) {
            errorMessage = "Enter a valid non-negative price."
            return
        }
        if (comments.length > 4000) {
            errorMessage = "Comments cannot exceed 4000 characters."
            return
        }

        coroutineScope.launch {
            submitting = true
            errorMessage = null
            runCatching {
                onSubmit(
                    code.trim(),
                    name.trim(),
                    donor.id,
                    condition,
                    cents,
                    comments.trim().ifBlank { null }
                )
            }.onSuccess(onSaved)
                .onFailure {
                    errorMessage = appState.handleRequestFailure(it)
                }
            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Item code") },
                    supportingText = {
                        if (initial == null) {
                            Text("Leave blank to generate one.")
                        }
                    },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                SearchField(
                    value = donorSearch,
                    onValueChange = { donorSearch = it },
                    label = "Search donors",
                    modifier = Modifier.fillMaxWidth()
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                ) {
                    LazyColumn {
                        items(filteredDonors, key = { it.id }) { donor ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !submitting) {
                                        selectedDonor = donor
                                        donorSearch = donor.name
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDonor?.id == donor.id,
                                    onClick = {
                                        selectedDonor = donor
                                        donorSearch = donor.name
                                    },
                                    enabled = !submitting
                                )
                                Text(donor.name)
                            }
                        }
                    }
                }
                Text("Condition", style = MaterialTheme.typography.labelLarge)
                ItemCondition.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = condition == option,
                            onClick = { condition = option },
                            enabled = !submitting
                        )
                        Text(option.name)
                    }
                }
                CurrencyField(
                    value = price,
                    onValueChange = { price = it },
                    label = "Suggested price",
                    enabled = !submitting,
                    isError = price.isNotBlank() && priceCents == null,
                    supportingText = priceCents?.let {
                        "Will display as ${formatMoney(it, currencyCode)}."
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = comments,
                    onValueChange = {
                        if (it.length <= 4000) comments = it
                    },
                    label = { Text("Comments") },
                    supportingText = { Text("${comments.length}/4000") },
                    minLines = 4,
                    maxLines = 8,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                FeedbackMessage(errorMessage, true)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !submitting) {
                Text(if (initial == null) "Create" else "Save")
            }
        }
    )
}

@Composable
private fun ItemDetailsDialog(
    item: ItemResponse,
    currencyCode: String,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.code) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Name: ${item.name}")
                Text("Donor: ${item.donorName}")
                Text("Condition: ${item.condition.name}")
                Text("Status: ${item.status.name}")
                Text(
                    "Suggested price: ${formatMoney(item.suggestedPriceCents, currencyCode)}"
                )
                Text("Comments: ${item.comments ?: "-"}")
                Text("Created: ${formatInstant(item.createdAt)}")
                Text("Updated: ${formatInstant(item.updatedAt)}")
                Text("Item ID: ${item.id}")
            }
        },
        dismissButton = {
            if (canEdit || canDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canEdit) {
                        TextButton(onClick = onEdit) { Text("Edit") }
                    }
                    if (canDelete) {
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
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
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ConfirmationDialog(
        title = "Delete item?",
        message = "${item.code} can be deleted only if no sale references it. Sale history is never deleted.",
        confirmLabel = "Delete",
        busy = submitting,
        busyMessage = "Deleting item...",
        onCancel = onDismiss,
        onConfirm = {
            if (!submitting) {
                coroutineScope.launch {
                    submitting = true
                    errorMessage = null
                    runCatching {
                        apiClient.deleteItem(item.id)
                    }.onSuccess {
                        onDeleted()
                    }.onFailure {
                        errorMessage = appState.handleRequestFailure(it)
                    }
                    submitting = false
                }
            }
        }
    )

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Delete failed") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun centsToInput(cents: Long): String =
    java.math.BigDecimal(cents)
        .movePointLeft(2)
        .setScale(2)
        .toPlainString()
