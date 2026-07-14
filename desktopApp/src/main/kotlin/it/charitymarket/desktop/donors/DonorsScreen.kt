package it.charitymarket.desktop.donors

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.desktop.api.CharityMarketApiClient
import it.charitymarket.desktop.api.CreateDonorRequest
import it.charitymarket.desktop.api.DonorResponse
import it.charitymarket.desktop.api.SaleResponse
import it.charitymarket.desktop.api.SaleStatus
import it.charitymarket.desktop.api.UpdateDonorRequest
import it.charitymarket.desktop.app.DesktopAppState
import it.charitymarket.desktop.components.ConfirmationDialog
import it.charitymarket.desktop.components.EmptyStatePanel
import it.charitymarket.desktop.components.ErrorPanel
import it.charitymarket.desktop.components.FeedbackMessage
import it.charitymarket.desktop.components.LoadingPanel
import it.charitymarket.desktop.components.ScreenHeader
import it.charitymarket.desktop.components.SearchField
import it.charitymarket.desktop.model.RoleRules
import it.charitymarket.desktop.model.formatInstant
import it.charitymarket.desktop.model.formatMoney
import kotlinx.coroutines.launch

private data class DonorSalesSummary(
    val soldItemCount: Int,
    val totalSoldCents: Long
)

@Composable
fun DonorsScreen(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    currencyCode: String
) {
    val roles = appState.uiState.authenticatedUser?.roles.orEmpty()
    val canEdit = RoleRules.canEditDonors(roles)
    val canDelete = RoleRules.canDeleteDonors(roles)

    var donors by remember {
        mutableStateOf<List<DonorResponse>>(emptyList())
    }
    var salesSummaries by remember {
        mutableStateOf<Map<String, DonorSalesSummary>?>(null)
    }
    var salesSummaryMessage by remember {
        mutableStateOf<String?>(null)
    }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedDonor by remember {
        mutableStateOf<DonorResponse?>(null)
    }
    var editTarget by remember {
        mutableStateOf<DonorResponse?>(null)
    }
    var deleteTarget by remember {
        mutableStateOf<DonorResponse?>(null)
    }
    var refreshKey by remember { mutableStateOf(0) }

    suspend fun load() {
        loading = true
        errorMessage = null
        salesSummaries = null
        salesSummaryMessage = null

        val donorsResult = runCatching {
            apiClient.listDonors()
        }

        if (donorsResult.isFailure) {
            errorMessage = appState.handleRequestFailure(
                donorsResult.exceptionOrNull()!!
            )
            loading = false
            return
        }

        donors = donorsResult.getOrThrow()

        if (!RoleRules.canAccessSales(roles)) {
            salesSummaryMessage =
                "Sales totals are unavailable for this role."
            loading = false
            return
        }

        val itemsResult = runCatching { apiClient.listItems() }
        val salesResult = runCatching { apiClient.listSales() }

        if (itemsResult.isSuccess && salesResult.isSuccess) {
            salesSummaries = buildDonorSalesSummaries(
                itemDonorById = itemsResult.getOrThrow()
                    .associate { it.id to it.donorId },
                sales = salesResult.getOrThrow()
            )
        } else {
            val failure = itemsResult.exceptionOrNull()
                ?: salesResult.exceptionOrNull()
            salesSummaryMessage = failure?.let {
                "Could not load donor sales totals: " +
                        appState.handleRequestFailure(it)
            } ?: "Could not load donor sales totals."
        }

        loading = false
    }

    LaunchedEffect(refreshKey) {
        load()
    }

    val filteredDonors = donors.filter { donor ->
        val query = search.trim()
        query.isBlank() ||
                donor.name.contains(query, ignoreCase = true) ||
                donor.email.orEmpty().contains(query, ignoreCase = true) ||
                donor.phone.orEmpty().contains(query, ignoreCase = true) ||
                donor.comments.orEmpty().contains(query, ignoreCase = true)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Donors",
            subtitle = "People and organizations donating items.",
            action = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { refreshKey += 1 },
                        enabled = !loading
                    ) {
                        Text("Refresh")
                    }

                    Button(
                        onClick = { showCreateDialog = true }
                    ) {
                        Text("Create donor")
                    }
                }
            }
        )

        SearchField(
            value = search,
            onValueChange = { search = it },
            label = "Search donors",
            modifier = Modifier.fillMaxWidth()
        )

        FeedbackMessage(feedback, false)
        FeedbackMessage(salesSummaryMessage, false)

        when {
            loading -> LoadingPanel("Loading donors...")
            errorMessage != null -> ErrorPanel(
                message = errorMessage.orEmpty(),
                onRetry = { refreshKey += 1 }
            )
            filteredDonors.isEmpty() -> EmptyStatePanel(
                title = if (donors.isEmpty()) {
                    "No donors yet"
                } else {
                    "No matching donors"
                },
                message = if (donors.isEmpty()) {
                    "Create a donor before adding donated items."
                } else {
                    "Adjust the search term to show more donors."
                }
            )
            else -> DonorList(
                donors = filteredDonors,
                salesSummaries = salesSummaries,
                currencyCode = currencyCode,
                onSelect = { selectedDonor = it }
            )
        }
    }

    if (showCreateDialog) {
        DonorFormDialog(
            title = "Create donor",
            initial = null,
            submittingLabel = "Creating donor...",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onSubmit = { name, email, phone, comments ->
                apiClient.createDonor(
                    CreateDonorRequest(
                        name = name,
                        email = email,
                        phone = phone,
                        comments = comments
                    )
                )
            },
            appState = appState,
            onSaved = { donor ->
                donors = (donors + donor)
                    .sortedBy { it.name.lowercase() }
                feedback = "Donor created: ${donor.name}"
                showCreateDialog = false
            }
        )
    }

    selectedDonor?.let { donor ->
        DonorDetailsDialog(
            donor = donor,
            salesSummary = salesSummaries?.get(donor.id),
            salesSummaryAvailable = salesSummaries != null,
            currencyCode = currencyCode,
            canEdit = canEdit,
            canDelete = canDelete,
            onEdit = {
                selectedDonor = null
                editTarget = donor
            },
            onDelete = {
                selectedDonor = null
                deleteTarget = donor
            },
            onDismiss = { selectedDonor = null }
        )
    }

    editTarget?.let { donor ->
        DonorFormDialog(
            title = "Edit donor",
            initial = donor,
            submittingLabel = "Saving donor...",
            confirmLabel = "Save",
            onDismiss = { editTarget = null },
            onSubmit = { name, email, phone, comments ->
                apiClient.updateDonor(
                    donor.id,
                    UpdateDonorRequest(
                        name = name,
                        email = email,
                        phone = phone,
                        comments = comments
                    )
                )
            },
            appState = appState,
            onSaved = { updated ->
                donors = donors.map {
                    if (it.id == updated.id) updated else it
                }.sortedBy { it.name.lowercase() }
                feedback = "Donor updated: ${updated.name}"
                editTarget = null
            }
        )
    }

    deleteTarget?.let { donor ->
        DeleteDonorDialog(
            donor = donor,
            apiClient = apiClient,
            appState = appState,
            onDismiss = { deleteTarget = null },
            onDeleted = {
                donors = donors.filterNot { it.id == donor.id }
                feedback = "Donor deleted: ${donor.name}"
                deleteTarget = null
            }
        )
    }
}

@Composable
private fun DonorList(
    donors: List<DonorResponse>,
    salesSummaries: Map<String, DonorSalesSummary>?,
    currencyCode: String,
    onSelect: (DonorResponse) -> Unit
) {
    Card {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Name", Modifier.weight(1.6f), style = MaterialTheme.typography.labelLarge)
                Text("Email", Modifier.weight(1.4f), style = MaterialTheme.typography.labelLarge)
                Text("Phone", Modifier.weight(0.9f), style = MaterialTheme.typography.labelLarge)
                Text("Sold items", Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge)
                Text("Sold total", Modifier.weight(0.9f), style = MaterialTheme.typography.labelLarge)
            }

            HorizontalDivider()

            LazyColumn {
                items(donors, key = { it.id }) { donor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(donor) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(donor.name, Modifier.weight(1.6f))
                        Text(donor.email ?: "-", Modifier.weight(1.4f))
                        Text(donor.phone ?: "-", Modifier.weight(0.9f))

                        val summary = salesSummaries?.get(donor.id)
                        Text(
                            summary?.soldItemCount?.toString()
                                ?: if (salesSummaries == null) "-" else "0",
                            Modifier.weight(0.7f)
                        )
                        Text(
                            summary?.let {
                                formatMoney(it.totalSoldCents, currencyCode)
                            } ?: if (salesSummaries == null) {
                                "-"
                            } else {
                                formatMoney(0, currencyCode)
                            },
                            Modifier.weight(0.9f)
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DonorFormDialog(
    title: String,
    initial: DonorResponse?,
    submittingLabel: String,
    confirmLabel: String,
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    onSubmit: suspend (
        name: String,
        email: String?,
        phone: String?,
        comments: String?
    ) -> DonorResponse,
    onSaved: (DonorResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var comments by remember {
        mutableStateOf(initial?.comments.orEmpty())
    }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val trimmedEmail = email.trim()
    val emailInvalid = trimmedEmail.isNotBlank() &&
            !trimmedEmail.matches(
                Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
            )

    fun submit() {
        if (submitting) return
        if (name.trim().isBlank()) {
            errorMessage = "The donor name is required."
            return
        }
        if (emailInvalid) {
            errorMessage = "The email address is invalid."
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
                    name.trim(),
                    trimmedEmail.ifBlank { null },
                    phone.trim().ifBlank { null },
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
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !submitting,
                    isError = emailInvalid,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = comments,
                    onValueChange = {
                        if (it.length <= 4000) comments = it
                    },
                    label = { Text("Comments") },
                    supportingText = {
                        Text("${comments.length}/4000")
                    },
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
                Text(if (submitting) submittingLabel else confirmLabel)
            }
        }
    )
}

@Composable
private fun DonorDetailsDialog(
    donor: DonorResponse,
    salesSummary: DonorSalesSummary?,
    salesSummaryAvailable: Boolean,
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
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Donor ID: ${donor.id}")
                Text("Email: ${donor.email ?: "-"}")
                Text("Phone: ${donor.phone ?: "-"}")
                Text("Comments: ${donor.comments ?: "-"}")
                Text(
                    "Sold items: " + if (salesSummaryAvailable) {
                        salesSummary?.soldItemCount?.toString() ?: "0"
                    } else "-"
                )
                Text(
                    "Sold total: " + if (salesSummaryAvailable) {
                        formatMoney(
                            salesSummary?.totalSoldCents ?: 0,
                            currencyCode
                        )
                    } else "Unavailable"
                )
                Text("Created: ${formatInstant(donor.createdAt)}")
                Text(
                    "Updated: ${formatInstant(donor.updatedAt ?: donor.createdAt)}"
                )
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
private fun DeleteDonorDialog(
    donor: DonorResponse,
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ConfirmationDialog(
        title = "Delete donor?",
        message = "${donor.name} can be deleted only after all of their eligible items have been removed. Sale history is never deleted.",
        confirmLabel = "Delete",
        busy = submitting,
        busyMessage = "Deleting donor...",
        onCancel = onDismiss,
        onConfirm = {
            if (!submitting) {
                coroutineScope.launch {
                    submitting = true
                    errorMessage = null
                    runCatching {
                        apiClient.deleteDonor(donor.id)
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

private fun buildDonorSalesSummaries(
    itemDonorById: Map<String, String>,
    sales: List<SaleResponse>
): Map<String, DonorSalesSummary> {
    val soldItemIdsByDonor =
        mutableMapOf<String, MutableSet<String>>()
    val soldTotalByDonor = mutableMapOf<String, Long>()

    sales
        .filter { it.status == SaleStatus.COMPLETED }
        .forEach { sale ->
            sale.lines.forEach { line ->
                val donorId = itemDonorById[line.itemId]
                    ?: return@forEach

                soldItemIdsByDonor
                    .getOrPut(donorId) { mutableSetOf() }
                    .add(line.itemId)

                soldTotalByDonor[donorId] =
                    (soldTotalByDonor[donorId] ?: 0L) +
                            line.finalPriceCents
            }
        }

    return soldTotalByDonor.mapValues { (donorId, total) ->
        DonorSalesSummary(
            soldItemCount = soldItemIdsByDonor[donorId]?.size ?: 0,
            totalSoldCents = total
        )
    }
}
