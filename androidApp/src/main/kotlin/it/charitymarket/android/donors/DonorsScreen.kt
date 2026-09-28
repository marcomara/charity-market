package it.charitymarket.android.donors

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.shared.api.CreateDonorRequest
import it.charitymarket.shared.api.DonorResponse
import it.charitymarket.shared.api.SaleStatus
import it.charitymarket.shared.api.UpdateDonorRequest
import it.charitymarket.android.app.AndroidAppState
import it.charitymarket.android.components.ConfirmationDialog
import it.charitymarket.android.components.EmptyStatePanel
import it.charitymarket.android.components.ErrorPanel
import it.charitymarket.android.components.FeedbackMessage
import it.charitymarket.android.components.LoadingPanel
import it.charitymarket.android.components.ScreenHeader
import it.charitymarket.android.components.SearchField
import it.charitymarket.shared.auth.RoleRules
import it.charitymarket.android.model.formatInstant
import it.charitymarket.android.model.formatMoney
import it.charitymarket.android.model.isEmailValid
import kotlinx.coroutines.launch

private data class DonorSummary(
    val soldItems: Int,
    val totalCents: Long
)

@Composable
fun DonorsScreen(appState: AndroidAppState) {
    val api = appState.requireApiClient()
    val roles = appState.uiState.authenticatedUser?.roles.orEmpty()
    val canEdit = RoleRules.canEditInventory(roles)
    val canDelete = RoleRules.canDeleteDonors(roles)
    var donors by remember { mutableStateOf<List<DonorResponse>>(emptyList()) }
    var summaries by remember {
        mutableStateOf<Map<String, DonorSummary>?>(null)
    }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var supplementalMessage by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DonorResponse?>(null) }
    var editTarget by remember { mutableStateOf<DonorResponse?>(null) }
    var deleteTarget by remember { mutableStateOf<DonorResponse?>(null) }
    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, appState.uiState.autoRefreshGeneration) {
        if (!hasLoaded) loading = true
        error = null
        if (!hasLoaded) {
            summaries = null
            supplementalMessage = null
        }
        val donorsResult = runCatching { api.listDonors() }
        if (donorsResult.isFailure) {
            error = appState.handleRequestFailure(
                donorsResult.exceptionOrNull()!!
            )
            loading = false
            hasLoaded = true
            return@LaunchedEffect
        }
        val updatedDonors = donorsResult.getOrThrow()
        if (donors != updatedDonors) donors = updatedDonors

        if (RoleRules.canAccessSales(roles)) {
            val inventoryResult = runCatching { api.listItems() }
            val salesResult = runCatching { api.listSales() }
            if (inventoryResult.isSuccess && salesResult.isSuccess) {
                val donorByItem = inventoryResult.getOrThrow()
                    .associate { it.id to it.donorId }
                val next = mutableMapOf<String, DonorSummary>()
                salesResult.getOrThrow()
                    .filter { it.status == SaleStatus.COMPLETED }
                    .flatMap { it.lines }
                    .forEach { line ->
                        val donorId = donorByItem[line.itemId]
                            ?: return@forEach
                        val current = next[donorId] ?: DonorSummary(0, 0)
                        next[donorId] = current.copy(
                            soldItems = current.soldItems + 1,
                            totalCents = current.totalCents + line.finalPriceCents
                        )
                    }
                if (summaries != next) summaries = next
                supplementalMessage = null
            } else {
                supplementalMessage =
                    "Donors loaded, but sales totals are unavailable."
            }
        }
        loading = false
        hasLoaded = true
    }

    val filtered = donors.filter { donor ->
        val query = search.trim()
        query.isBlank() ||
                donor.name.contains(query, true) ||
                donor.email.orEmpty().contains(query, true) ||
                donor.phone.orEmpty().contains(query, true) ||
                donor.comments.orEmpty().contains(query, true)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            "Donors",
            "People and organizations donating items.",
            "Refresh",
            !loading
        ) { refreshKey++ }
        if (canEdit) {
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Create donor") }
        }
        SearchField(
            search,
            { search = it },
            "Search donors",
            Modifier.fillMaxWidth()
        )
        FeedbackMessage(supplementalMessage, false)

        when {
            loading -> LoadingPanel("Loading donors...")
            error != null -> ErrorPanel(error.orEmpty()) { refreshKey++ }
            filtered.isEmpty() -> EmptyStatePanel(
                if (donors.isEmpty()) "No donors yet" else "No matching donors",
                if (donors.isEmpty()) {
                    "Create a donor before adding donated items."
                } else "Adjust the search term."
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { donor ->
                    val summary = summaries?.get(donor.id)
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selected = donor }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                donor.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(donor.email ?: "No email")
                            Text(donor.phone ?: "No phone")
                            if (summaries != null) {
                                Text(
                                    "Sold: ${summary?.soldItems ?: 0} · " +
                                            formatMoney(
                                                summary?.totalCents ?: 0,
                                                appState.uiState.currencyCode
                                            )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        DonorFormDialog(
            title = "Create donor",
            initial = null,
            appState = appState,
            onDismiss = { showCreate = false },
            onSubmit = { name, email, phone, comments ->
                api.createDonor(CreateDonorRequest(name, email, phone, comments))
            },
            onSaved = {
                showCreate = false
                refreshKey++
                appState.showMessage("Donor created: ${it.name}")
            }
        )
    }
    selected?.let { donor ->
        DonorDetailsDialog(
            donor,
            summaries?.get(donor.id),
            summaries != null,
            appState.uiState.currencyCode,
            canEdit,
            canDelete,
            onEdit = {
                selected = null
                editTarget = donor
            },
            onDelete = {
                selected = null
                deleteTarget = donor
            },
            onDismiss = { selected = null }
        )
    }
    editTarget?.let { donor ->
        DonorFormDialog(
            "Edit donor",
            donor,
            appState,
            onDismiss = { editTarget = null },
            onSubmit = { name, email, phone, comments ->
                api.updateDonor(
                    donor.id,
                    UpdateDonorRequest(name, email, phone, comments)
                )
            },
            onSaved = {
                editTarget = null
                refreshKey++
                appState.showMessage("Donor updated: ${it.name}")
            }
        )
    }
    deleteTarget?.let { donor ->
        DeleteDonorDialog(
            donor,
            appState,
            onDismiss = { deleteTarget = null },
            onDeleted = {
                deleteTarget = null
                refreshKey++
                appState.showMessage("Donor deleted: ${donor.name}")
            }
        )
    }
}

@Composable
private fun DonorFormDialog(
    title: String,
    initial: DonorResponse?,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onSubmit: suspend (String, String?, String?, String?) -> DonorResponse,
    onSaved: (DonorResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var comments by remember { mutableStateOf(initial?.comments.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        error = when {
            name.trim().isBlank() -> "The donor name is required."
            !isEmailValid(email.trim()) -> "The email address is invalid."
            phone.length > 40 -> "The phone number cannot exceed 40 characters."
            comments.length > 4000 -> "Comments cannot exceed 4000 characters."
            else -> null
        }
        if (error != null) return
        scope.launch {
            busy = true
            runCatching {
                onSubmit(
                    name.trim(),
                    email.trim().ifBlank { null },
                    phone.trim().ifBlank { null },
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
                modifier = Modifier.heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    name, { name = it.take(200) },
                    label = { Text("Name") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    email, { email = it.take(320) },
                    label = { Text("Email") },
                    enabled = !busy,
                    isError = !isEmailValid(email.trim()),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    phone, { phone = it.take(40) },
                    label = { Text("Phone") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    comments, { comments = it.take(4000) },
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
private fun DonorDetailsDialog(
    donor: DonorResponse,
    summary: DonorSummary?,
    summaryAvailable: Boolean,
    currencyCode: String,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(donor.name) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("Email: ${donor.email ?: "—"}")
                Text("Phone: ${donor.phone ?: "—"}")
                Text("Comments: ${donor.comments ?: "—"}")
                if (summaryAvailable) {
                    Text("Sold items: ${summary?.soldItems ?: 0}")
                    Text(
                        "Sold total: ${formatMoney(summary?.totalCents ?: 0, currencyCode)}"
                    )
                }
                Text("Created: ${formatInstant(donor.createdAt)}")
                Text("Updated: ${formatInstant(donor.updatedAt)}")
                Text("ID: ${donor.id}")
            }
        },
        dismissButton = {
            Row {
                if (canEdit) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                }
                if (canDelete) {
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
private fun DeleteDonorDialog(
    donor: DonorResponse,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ConfirmationDialog(
        "Delete donor?",
        "${donor.name} can be deleted only when no inventory references it.",
        "Delete",
        busy,
        "Deleting donor...",
        onDismiss
    ) {
        if (!busy) {
            scope.launch {
                busy = true
                runCatching {
                    appState.requireApiClient().deleteDonor(donor.id)
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
