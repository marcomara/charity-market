package it.charitymarket.android.sales

import androidx.compose.foundation.clickable
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
import it.charitymarket.shared.api.CreateSaleRequest
import it.charitymarket.shared.api.ItemResponse
import it.charitymarket.shared.api.ItemStatus
import it.charitymarket.shared.api.PaymentMethod
import it.charitymarket.shared.api.SaleLineRequest
import it.charitymarket.shared.api.SaleLineResponse
import it.charitymarket.shared.api.SaleResponse
import it.charitymarket.shared.api.SaleStatus
import it.charitymarket.shared.api.UpdateSaleRequest
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

private data class DraftLine(
    val item: ItemResponse,
    val priceInput: String
)

private data class EditLine(
    val line: SaleLineResponse,
    val priceInput: String
)

@Composable
fun SalesScreen(appState: AndroidAppState) {
    val api = appState.requireApiClient()
    val roles = appState.uiState.authenticatedUser?.roles.orEmpty()
    val canManage = RoleRules.canManageSales(roles)
    var sales by remember { mutableStateOf<List<SaleResponse>>(emptyList()) }
    var inventory by remember { mutableStateOf<List<ItemResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SaleResponse?>(null) }
    var editTarget by remember { mutableStateOf<SaleResponse?>(null) }
    var voidTarget by remember { mutableStateOf<SaleResponse?>(null) }
    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, appState.uiState.autoRefreshGeneration) {
        if (!hasLoaded) loading = true
        error = null
        val salesResult = runCatching { api.listSales() }
        val inventoryResult = runCatching { api.listItems() }
        salesResult.onSuccess {
            if (sales != it) sales = it
        }
            .onFailure { error = appState.handleRequestFailure(it) }
        inventoryResult.onSuccess {
            if (inventory != it) inventory = it
        }
            .onFailure {
                if (error == null) {
                    error = "Could not load inventory: " +
                            appState.handleRequestFailure(it)
                }
            }
        loading = false
        hasLoaded = true
    }

    val filtered = sales.filter { sale ->
        val query = search.trim()
        query.isBlank() ||
                sale.id.contains(query, true) ||
                sale.paymentMethod.name.contains(query, true) ||
                sale.status.name.contains(query, true) ||
                sale.comments.orEmpty().contains(query, true) ||
                sale.lines.any {
                    it.itemCode.contains(query, true) ||
                            it.itemName.contains(query, true)
                }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            "Transactions",
            "Completed and voided sales.",
            "Refresh",
            !loading
        ) { refreshKey++ }
        Button(
            onClick = { showCreate = true },
            enabled = inventory.any { it.status == ItemStatus.AVAILABLE },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create transaction") }
        SearchField(
            search,
            { search = it },
            "Search transactions",
            Modifier.fillMaxWidth()
        )

        when {
            loading -> LoadingPanel("Loading transactions...")
            error != null -> ErrorPanel(error.orEmpty()) { refreshKey++ }
            filtered.isEmpty() -> EmptyStatePanel(
                if (sales.isEmpty()) "No transactions yet"
                else "No matching transactions",
                if (sales.isEmpty()) {
                    "Create a transaction after adding available items."
                } else "Adjust the search term."
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { sale ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selected = sale }
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
                                Text(
                                    formatInstant(sale.soldAt),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                StatusBadge(
                                    sale.status.name,
                                    if (sale.status == SaleStatus.COMPLETED) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.error
                                )
                            }
                            Text("${sale.itemCount} items · ${sale.paymentMethod.name}")
                            Text(
                                formatMoney(
                                    sale.totalCents,
                                    appState.uiState.currencyCode
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateSaleDialog(
            availableItems = inventory.filter {
                it.status == ItemStatus.AVAILABLE
            },
            appState = appState,
            onDismiss = { showCreate = false },
            onCreated = {
                showCreate = false
                refreshKey++
                appState.showMessage("Transaction created: ${it.id}")
            }
        )
    }
    selected?.let { sale ->
        SaleDetailsDialog(
            sale,
            appState.uiState.currencyCode,
            canManage,
            onEdit = {
                selected = null
                editTarget = sale
            },
            onVoid = {
                selected = null
                voidTarget = sale
            },
            onDismiss = { selected = null }
        )
    }
    editTarget?.let { sale ->
        EditSaleDialog(
            sale,
            appState,
            onDismiss = { editTarget = null },
            onUpdated = {
                editTarget = null
                refreshKey++
                appState.showMessage("Transaction updated: ${it.id}")
            }
        )
    }
    voidTarget?.let { sale ->
        VoidSaleDialog(
            sale,
            appState,
            onDismiss = { voidTarget = null },
            onVoided = {
                voidTarget = null
                refreshKey++
                appState.showMessage("Transaction voided: ${it.id}")
            }
        )
    }
}

@Composable
private fun CreateSaleDialog(
    availableItems: List<ItemResponse>,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onCreated: (SaleResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currency = appState.uiState.currencyCode
    var search by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<DraftLine>>(emptyList()) }
    var payment by remember { mutableStateOf(PaymentMethod.CASH) }
    var comments by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedIds = lines.mapTo(mutableSetOf()) { it.item.id }
    val matchingItems = availableItems.filter { item ->
        item.id !in selectedIds &&
                (search.isBlank() ||
                        item.code.contains(search, true) ||
                        item.name.contains(search, true))
    }
    val parsed = lines.mapNotNull { draft ->
        parseMoneyToCents(draft.priceInput)?.let { draft.item.id to it }
    }
    val total = parsed.sumOf { it.second }
    val valid = lines.isNotEmpty() &&
            parsed.size == lines.size &&
            comments.length <= 4000

    fun submit() {
        if (busy || !valid) return
        scope.launch {
            busy = true
            error = null
            runCatching {
                appState.requireApiClient().createSale(
                    CreateSaleRequest(
                        parsed.map { SaleLineRequest(it.first, it.second) },
                        payment,
                        comments.trim().ifBlank { null }
                    )
                )
            }.onSuccess(onCreated)
                .onFailure {
                    error = appState.handleRequestFailure(it)
                    confirming = false
                }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Create transaction") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchField(
                    search,
                    { search = it },
                    "Find an available item",
                    Modifier.fillMaxWidth(),
                    !busy
                )
                Card(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                    LazyColumn {
                        items(matchingItems, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(7.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${item.code} — ${item.name}")
                                    Text(
                                        formatMoney(item.suggestedPriceCents, currency)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        lines = lines + DraftLine(
                                            item,
                                            centsToInput(item.suggestedPriceCents)
                                        )
                                    },
                                    enabled = !busy
                                ) { Text("Add") }
                            }
                        }
                    }
                }

                Text("Selected items", style = MaterialTheme.typography.titleSmall)
                if (lines.isEmpty()) {
                    Text("Add at least one available item.")
                }
                lines.forEach { draft ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${draft.item.code} — ${draft.item.name}")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CurrencyField(
                                draft.priceInput,
                                { value ->
                                    lines = lines.map {
                                        if (it.item.id == draft.item.id) {
                                            it.copy(priceInput = value)
                                        } else it
                                    }
                                },
                                "Final price",
                                !busy,
                                Modifier.weight(1f),
                                parseMoneyToCents(draft.priceInput) == null
                            )
                            TextButton(
                                onClick = {
                                    lines = lines.filterNot {
                                        it.item.id == draft.item.id
                                    }
                                },
                                enabled = !busy
                            ) { Text("Remove") }
                        }
                    }
                }

                PaymentSelector(payment, { payment = it }, !busy)
                OutlinedTextField(
                    comments,
                    { comments = it.take(4000) },
                    label = { Text("Comments") },
                    supportingText = { Text("${comments.length}/4000") },
                    minLines = 3,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Total: ${formatMoney(total, currency)}",
                    style = MaterialTheme.typography.titleMedium
                )
                FeedbackMessage(error, true)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = { confirming = true },
                enabled = !busy && valid
            ) { Text("Complete sale") }
        }
    )

    if (confirming) {
        ConfirmationDialog(
            "Complete transaction?",
            "Complete this transaction for ${formatMoney(total, currency)}?",
            "Complete",
            busy,
            "Completing transaction...",
            onCancel = { if (!busy) confirming = false },
            onConfirm = ::submit
        )
    }
}

@Composable
private fun EditSaleDialog(
    sale: SaleResponse,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onUpdated: (SaleResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    var lines by remember {
        mutableStateOf(
            sale.lines.map { EditLine(it, centsToInput(it.finalPriceCents)) }
        )
    }
    var payment by remember { mutableStateOf(sale.paymentMethod) }
    var comments by remember { mutableStateOf(sale.comments.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed = lines.mapNotNull { item ->
        parseMoneyToCents(item.priceInput)?.let { item.line.itemId to it }
    }
    val valid = lines.isNotEmpty() && parsed.size == lines.size
    val total = parsed.sumOf { it.second }

    fun submit() {
        if (busy || !valid) return
        scope.launch {
            busy = true
            runCatching {
                appState.requireApiClient().updateSale(
                    sale.id,
                    UpdateSaleRequest(
                        parsed.map { SaleLineRequest(it.first, it.second) },
                        payment,
                        comments.trim().ifBlank { null }
                    )
                )
            }.onSuccess(onUpdated)
                .onFailure { error = appState.handleRequestFailure(it) }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Edit transaction") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                lines.forEach { item ->
                    Text("${item.line.itemCode} — ${item.line.itemName}")
                    CurrencyField(
                        item.priceInput,
                        { value ->
                            lines = lines.map {
                                if (it.line.itemId == item.line.itemId) {
                                    it.copy(priceInput = value)
                                } else it
                            }
                        },
                        "Final price",
                        !busy,
                        Modifier.fillMaxWidth(),
                        parseMoneyToCents(item.priceInput) == null
                    )
                }
                PaymentSelector(payment, { payment = it }, !busy)
                OutlinedTextField(
                    comments,
                    { comments = it.take(4000) },
                    label = { Text("Comments") },
                    minLines = 3,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Updated total: ${formatMoney(total, appState.uiState.currencyCode)}"
                )
                FeedbackMessage(error, true)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !busy && valid) {
                Text("Save")
            }
        }
    )
}

@Composable
private fun PaymentSelector(
    selected: PaymentMethod,
    onSelected: (PaymentMethod) -> Unit,
    enabled: Boolean
) {
    Column {
        Text("Payment method", style = MaterialTheme.typography.titleSmall)
        PaymentMethod.entries.forEach { method ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == method,
                    onClick = { onSelected(method) },
                    enabled = enabled
                )
                Text(method.name)
            }
        }
    }
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
                modifier = Modifier.heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Status: ${sale.status.name}")
                Text("Date: ${formatInstant(sale.soldAt)}")
                Text("Payment: ${sale.paymentMethod.name}")
                Text("Comments: ${sale.comments ?: "—"}")
                sale.lines.forEach { line ->
                    Text(
                        "${line.itemCode} — ${line.itemName}: " +
                                formatMoney(line.finalPriceCents, currencyCode)
                    )
                }
                Text(
                    "Total: ${formatMoney(sale.totalCents, currencyCode)}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (sale.status == SaleStatus.VOIDED) {
                    Text("Voided: ${formatInstant(sale.voidedAt)}")
                    Text("Reason: ${sale.voidReason ?: "—"}")
                }
            }
        },
        dismissButton = {
            if (canManage && sale.status == SaleStatus.COMPLETED) {
                Row {
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
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onVoided: (SaleResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Void transaction?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "The sale remains in history and its items return to available inventory."
                )
                OutlinedTextField(
                    reason,
                    { reason = it.take(4000) },
                    label = { Text("Reason") },
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
            Button(
                onClick = {
                    if (!busy) {
                        scope.launch {
                            busy = true
                            runCatching {
                                appState.requireApiClient().voidSale(
                                    sale.id,
                                    reason.trim().ifBlank { null }
                                )
                            }.onSuccess(onVoided)
                                .onFailure {
                                    error = appState.handleRequestFailure(it)
                                }
                            busy = false
                        }
                    }
                },
                enabled = !busy
            ) { Text(if (busy) "Voiding..." else "Void sale") }
        }
    )
}
