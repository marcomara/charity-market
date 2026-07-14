package it.charitymarket.desktop.sales

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
import it.charitymarket.desktop.api.CreateSaleRequest
import it.charitymarket.desktop.api.ItemResponse
import it.charitymarket.desktop.api.ItemStatus
import it.charitymarket.desktop.api.PaymentMethod
import it.charitymarket.desktop.api.SaleLineRequest
import it.charitymarket.desktop.api.SaleLineResponse
import it.charitymarket.desktop.api.SaleResponse
import it.charitymarket.desktop.api.SaleStatus
import it.charitymarket.desktop.api.UpdateSaleRequest
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

private data class SaleDraftLine(
    val item: ItemResponse,
    val priceInput: String
)

private data class SaleEditLine(
    val line: SaleLineResponse,
    val priceInput: String
)

@Composable
fun SalesScreen(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    currencyCode: String
) {
    val roles = appState.uiState.authenticatedUser?.roles.orEmpty()
    val canManageSales = RoleRules.canManageSales(roles)

    var sales by remember {
        mutableStateOf<List<SaleResponse>>(emptyList())
    }
    var inventory by remember {
        mutableStateOf<List<ItemResponse>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedSale by remember { mutableStateOf<SaleResponse?>(null) }
    var editTarget by remember { mutableStateOf<SaleResponse?>(null) }
    var voidTarget by remember { mutableStateOf<SaleResponse?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    suspend fun load() {
        loading = true
        errorMessage = null

        val salesResult = runCatching { apiClient.listSales() }
        val itemsResult = runCatching { apiClient.listItems() }

        salesResult.onSuccess { sales = it }
            .onFailure {
                errorMessage = appState.handleRequestFailure(it)
            }

        itemsResult.onSuccess { inventory = it }
            .onFailure {
                if (errorMessage == null) {
                    errorMessage =
                        "Could not load inventory: " +
                                appState.handleRequestFailure(it)
                }
            }

        loading = false
    }

    LaunchedEffect(refreshKey) {
        load()
    }

    val filteredSales = sales.filter { sale ->
        val query = search.trim()
        query.isBlank() ||
                sale.id.contains(query, ignoreCase = true) ||
                sale.paymentMethod.name.contains(query, ignoreCase = true) ||
                sale.status.name.contains(query, ignoreCase = true) ||
                sale.comments.orEmpty().contains(query, ignoreCase = true) ||
                sale.lines.any {
                    it.itemCode.contains(query, ignoreCase = true) ||
                            it.itemName.contains(query, ignoreCase = true)
                }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Transactions",
            subtitle = "Completed and voided sales. Sales are never hard-deleted.",
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
                        enabled = inventory.any {
                            it.status == ItemStatus.AVAILABLE
                        }
                    ) {
                        Text("Create transaction")
                    }
                }
            }
        )

        SearchField(
            value = search,
            onValueChange = { search = it },
            label = "Search transactions",
            modifier = Modifier.fillMaxWidth()
        )

        FeedbackMessage(feedback, false)

        when {
            loading -> LoadingPanel("Loading transactions...")
            errorMessage != null -> ErrorPanel(
                errorMessage.orEmpty(),
                onRetry = { refreshKey += 1 }
            )
            filteredSales.isEmpty() -> EmptyStatePanel(
                title = if (sales.isEmpty()) {
                    "No transactions yet"
                } else {
                    "No matching transactions"
                },
                message = if (sales.isEmpty()) {
                    "Create a transaction after adding available items."
                } else {
                    "Adjust the search term."
                }
            )
            else -> SaleList(
                sales = filteredSales,
                currencyCode = currencyCode,
                onSelect = { selectedSale = it }
            )
        }
    }

    if (showCreateDialog) {
        CreateSaleDialog(
            availableItems = inventory.filter {
                it.status == ItemStatus.AVAILABLE
            },
            currencyCode = currencyCode,
            apiClient = apiClient,
            appState = appState,
            onDismiss = { showCreateDialog = false },
            onCreated = { sale ->
                sales = listOf(sale) + sales
                inventory = inventory.map { item ->
                    if (sale.lines.any { it.itemId == item.id }) {
                        item.copy(status = ItemStatus.SOLD)
                    } else item
                }
                feedback = "Transaction created: ${sale.id}"
                showCreateDialog = false
            }
        )
    }

    selectedSale?.let { sale ->
        SaleDetailsDialog(
            sale = sale,
            currencyCode = currencyCode,
            canManage = canManageSales,
            onEdit = {
                selectedSale = null
                editTarget = sale
            },
            onVoid = {
                selectedSale = null
                voidTarget = sale
            },
            onDismiss = { selectedSale = null }
        )
    }

    editTarget?.let { sale ->
        EditSaleDialog(
            sale = sale,
            currencyCode = currencyCode,
            apiClient = apiClient,
            appState = appState,
            onDismiss = { editTarget = null },
            onUpdated = { updated ->
                sales = sales.map {
                    if (it.id == updated.id) updated else it
                }
                feedback = "Transaction updated: ${updated.id}"
                editTarget = null
            }
        )
    }

    voidTarget?.let { sale ->
        VoidSaleDialog(
            sale = sale,
            apiClient = apiClient,
            appState = appState,
            onDismiss = { voidTarget = null },
            onVoided = { updated ->
                sales = sales.map {
                    if (it.id == updated.id) updated else it
                }
                refreshKey += 1
                feedback = "Transaction voided: ${updated.id}"
                voidTarget = null
            }
        )
    }
}

@Composable
private fun SaleList(
    sales: List<SaleResponse>,
    currencyCode: String,
    onSelect: (SaleResponse) -> Unit
) {
    Card {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Date", Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge)
                Text("Payment", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                Text("Items", Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                Text("Total", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                Text("Status", Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider()

            LazyColumn {
                items(sales, key = { it.id }) { sale ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(sale) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatInstant(sale.soldAt), Modifier.weight(1.2f))
                        Text(sale.paymentMethod.name, Modifier.weight(0.8f))
                        Text(sale.itemCount.toString(), Modifier.weight(0.6f))
                        Text(
                            formatMoney(sale.totalCents, currencyCode),
                            Modifier.weight(0.8f)
                        )
                        Row(Modifier.weight(0.8f)) {
                            StatusBadge(
                                sale.status.name,
                                if (sale.status == SaleStatus.COMPLETED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
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
private fun CreateSaleDialog(
    availableItems: List<ItemResponse>,
    currencyCode: String,
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    onCreated: (SaleResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<SaleDraftLine>>(emptyList()) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var comments by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filteredItems = availableItems.filter { item ->
        item.id !in lines.map { it.item.id }.toSet() &&
                (search.isBlank() ||
                        item.code.contains(search, ignoreCase = true) ||
                        item.name.contains(search, ignoreCase = true))
    }

    val parsedLines = lines.mapNotNull { draft ->
        parseMoneyToCents(draft.priceInput)?.let {
            draft.item.id to it
        }
    }
    val totalCents = parsedLines.sumOf { it.second }
    val formValid = lines.isNotEmpty() &&
            parsedLines.size == lines.size &&
            comments.length <= 4000

    fun completeSale() {
        if (submitting || !formValid) return

        coroutineScope.launch {
            submitting = true
            errorMessage = null
            runCatching {
                apiClient.createSale(
                    CreateSaleRequest(
                        lines = parsedLines.map {
                            SaleLineRequest(it.first, it.second)
                        },
                        paymentMethod = paymentMethod,
                        comments = comments.trim().ifBlank { null }
                    )
                )
            }.onSuccess(onCreated)
                .onFailure {
                    errorMessage = appState.handleRequestFailure(it)
                    confirming = false
                }
            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Create transaction") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SearchField(
                    value = search,
                    onValueChange = { search = it },
                    label = "Find an available item",
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                ) {
                    LazyColumn {
                        items(filteredItems, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${item.code} — ${item.name}")
                                    Text(
                                        formatMoney(
                                            item.suggestedPriceCents,
                                            currencyCode
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        lines = lines + SaleDraftLine(
                                            item,
                                            centsToInput(
                                                item.suggestedPriceCents
                                            )
                                        )
                                    }
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }

                Text("Selected items", style = MaterialTheme.typography.titleMedium)
                lines.forEach { draft ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${draft.item.code} — ${draft.item.name}",
                            Modifier.weight(1f)
                        )
                        CurrencyField(
                            value = draft.priceInput,
                            onValueChange = { newValue ->
                                lines = lines.map {
                                    if (it.item.id == draft.item.id) {
                                        it.copy(priceInput = newValue)
                                    } else it
                                }
                            },
                            label = "Final price",
                            enabled = !submitting,
                            isError = parseMoneyToCents(
                                draft.priceInput
                            ) == null,
                            modifier = Modifier.weight(0.8f)
                        )
                        TextButton(
                            onClick = {
                                lines = lines.filterNot {
                                    it.item.id == draft.item.id
                                }
                            }
                        ) {
                            Text("Remove")
                        }
                    }
                }

                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                PaymentMethod.entries.forEach { method ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            enabled = !submitting
                        )
                        Text(method.name)
                    }
                }

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

                Text(
                    "Transaction total: ${formatMoney(totalCents, currencyCode)}",
                    style = MaterialTheme.typography.titleMedium
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
            Button(
                onClick = { confirming = true },
                enabled = !submitting && formValid
            ) {
                Text("Complete sale")
            }
        }
    )

    if (confirming) {
        ConfirmationDialog(
            title = "Complete transaction?",
            message = "Complete this transaction for ${formatMoney(totalCents, currencyCode)}?",
            confirmLabel = "Complete",
            busy = submitting,
            busyMessage = "Completing transaction...",
            onCancel = { if (!submitting) confirming = false },
            onConfirm = ::completeSale
        )
    }
}

@Composable
private fun EditSaleDialog(
    sale: SaleResponse,
    currencyCode: String,
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    onUpdated: (SaleResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var lines by remember {
        mutableStateOf(
            sale.lines.map {
                SaleEditLine(it, centsToInput(it.finalPriceCents))
            }
        )
    }
    var paymentMethod by remember { mutableStateOf(sale.paymentMethod) }
    var comments by remember { mutableStateOf(sale.comments.orEmpty()) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsedLines = lines.mapNotNull { draft ->
        parseMoneyToCents(draft.priceInput)?.let {
            draft.line.itemId to it
        }
    }
    val totalCents = parsedLines.sumOf { it.second }
    val valid = parsedLines.size == lines.size &&
            lines.isNotEmpty() && comments.length <= 4000

    fun submit() {
        if (submitting || !valid) return
        coroutineScope.launch {
            submitting = true
            errorMessage = null
            runCatching {
                apiClient.updateSale(
                    sale.id,
                    UpdateSaleRequest(
                        lines = parsedLines.map {
                            SaleLineRequest(it.first, it.second)
                        },
                        paymentMethod = paymentMethod,
                        comments = comments.trim().ifBlank { null }
                    )
                )
            }.onSuccess(onUpdated)
                .onFailure {
                    errorMessage = appState.handleRequestFailure(it)
                }
            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Edit transaction") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                lines.forEach { draft ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${draft.line.itemCode} — ${draft.line.itemName}",
                            Modifier.weight(1f)
                        )
                        CurrencyField(
                            value = draft.priceInput,
                            onValueChange = { newValue ->
                                lines = lines.map {
                                    if (it.line.itemId ==
                                        draft.line.itemId
                                    ) {
                                        it.copy(priceInput = newValue)
                                    } else it
                                }
                            },
                            label = "Final price",
                            enabled = !submitting,
                            isError = parseMoneyToCents(
                                draft.priceInput
                            ) == null,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                }

                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                PaymentMethod.entries.forEach { method ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            enabled = !submitting
                        )
                        Text(method.name)
                    }
                }

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
                Text("Updated total: ${formatMoney(totalCents, currencyCode)}")
                FeedbackMessage(errorMessage, true)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !submitting && valid) {
                Text("Save")
            }
        }
    )
}

@Composable
private fun SaleDetailsDialog(
    sale: SaleResponse,
    currencyCode: String,
    canManage: Boolean,
    onEdit: () -> Unit,
    onVoid: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction ${sale.id}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Status: ${sale.status.name}")
                Text("Date: ${formatInstant(sale.soldAt)}")
                Text("Payment: ${sale.paymentMethod.name}")
                Text("Comments: ${sale.comments ?: "-"}")
                sale.lines.forEach { line ->
                    Text(
                        "${line.itemCode} — ${line.itemName}: " +
                                formatMoney(
                                    line.finalPriceCents,
                                    currencyCode
                                )
                    )
                }
                HorizontalDivider()
                Text(
                    "Total: ${formatMoney(sale.totalCents, currencyCode)}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (sale.status == SaleStatus.VOIDED) {
                    Text("Voided: ${formatInstant(sale.voidedAt)}")
                    Text("Void reason: ${sale.voidReason ?: "-"}")
                }
            }
        },
        dismissButton = {
            if (canManage && sale.status == SaleStatus.COMPLETED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onVoid) { Text("Void") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun VoidSaleDialog(
    sale: SaleResponse,
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    onVoided: (SaleResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Void transaction?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The sale will remain in history and will be excluded from revenue totals. Its items will return to available inventory."
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        if (it.length <= 4000) reason = it
                    },
                    label = { Text("Reason") },
                    supportingText = { Text("${reason.length}/4000") },
                    minLines = 3,
                    maxLines = 6,
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
            Button(
                onClick = {
                    if (!submitting) {
                        coroutineScope.launch {
                            submitting = true
                            errorMessage = null
                            runCatching {
                                apiClient.voidSale(
                                    sale.id,
                                    reason.trim().ifBlank { null }
                                )
                            }.onSuccess(onVoided)
                                .onFailure {
                                    errorMessage =
                                        appState.handleRequestFailure(it)
                                }
                            submitting = false
                        }
                    }
                },
                enabled = !submitting
            ) {
                Text("Void sale")
            }
        }
    )
}

private fun centsToInput(cents: Long): String =
    java.math.BigDecimal(cents)
        .movePointLeft(2)
        .setScale(2)
        .toPlainString()
