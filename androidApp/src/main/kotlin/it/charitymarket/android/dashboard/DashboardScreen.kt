package it.charitymarket.android.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.shared.api.ItemStatus
import it.charitymarket.shared.api.SaleStatus
import it.charitymarket.android.app.AndroidAppState
import it.charitymarket.android.components.EmptyStatePanel
import it.charitymarket.android.components.ErrorPanel
import it.charitymarket.android.components.LoadingPanel
import it.charitymarket.android.components.ScreenHeader
import it.charitymarket.shared.auth.RoleRules
import it.charitymarket.android.model.formatMoney

private data class SummaryValue(val label: String, val value: String)

@Composable
fun DashboardScreen(appState: AndroidAppState) {
    val api = appState.requireApiClient()
    val roles = appState.uiState.authenticatedUser?.roles.orEmpty()
    val currency = appState.uiState.currencyCode
    var loading by remember { mutableStateOf(true) }
    var values by remember { mutableStateOf<List<SummaryValue>>(emptyList()) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(
        refreshKey,
        roles,
        currency,
        appState.uiState.autoRefreshGeneration
    ) {
        if (!hasLoaded) loading = true
        val next = mutableListOf<SummaryValue>()
        val failures = mutableListOf<String>()

        if (RoleRules.canAccessDonors(roles)) {
            runCatching { api.listDonors() }
                .onSuccess { next += SummaryValue("Donors", it.size.toString()) }
                .onFailure {
                    failures += "Donors: ${appState.handleRequestFailure(it)}"
                }
        }
        if (RoleRules.canAccessItems(roles)) {
            runCatching { api.listItems() }
                .onSuccess { inventory ->
                    next += SummaryValue("Items", inventory.size.toString())
                    next += SummaryValue(
                        "Available items",
                        inventory.count { it.status == ItemStatus.AVAILABLE }
                            .toString()
                    )
                    next += SummaryValue(
                        "Sold items",
                        inventory.count { it.status == ItemStatus.SOLD }
                            .toString()
                    )
                }.onFailure {
                    failures += "Items: ${appState.handleRequestFailure(it)}"
                }
        }
        if (RoleRules.canAccessSales(roles)) {
            runCatching { api.listSales() }
                .onSuccess { sales ->
                    val completed = sales.filter {
                        it.status == SaleStatus.COMPLETED
                    }
                    next += SummaryValue(
                        "Transactions",
                        completed.size.toString()
                    )
                    next += SummaryValue(
                        "Recorded sales",
                        formatMoney(completed.sumOf { it.totalCents }, currency)
                    )
                }.onFailure {
                    failures +=
                        "Transactions: ${appState.handleRequestFailure(it)}"
                }
        }
        if (values != next) values = next
        if (errors != failures) errors = failures
        loading = false
        hasLoaded = true
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "Dashboard",
            subtitle = "Live totals from the connected server.",
            actionLabel = "Refresh",
            actionEnabled = !loading,
            onAction = { refreshKey++ }
        )

        when {
            loading -> LoadingPanel("Loading dashboard...")
            values.isEmpty() && errors.isEmpty() -> EmptyStatePanel(
                "No dashboard data",
                "Your role does not expose business summaries."
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errors.isNotEmpty()) {
                    item {
                        ErrorPanel(
                            errors.joinToString("\n"),
                            onRetry = { refreshKey++ }
                        )
                    }
                }
                items(values, key = { it.label }) { summary ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                summary.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                summary.value,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
