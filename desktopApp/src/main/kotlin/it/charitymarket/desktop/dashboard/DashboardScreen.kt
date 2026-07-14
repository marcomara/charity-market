package it.charitymarket.desktop.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.desktop.api.CharityMarketApiClient
import it.charitymarket.desktop.api.ItemStatus
import it.charitymarket.desktop.api.SaleStatus
import it.charitymarket.desktop.api.UserRole
import it.charitymarket.desktop.app.DesktopAppState
import it.charitymarket.desktop.components.EmptyStatePanel
import it.charitymarket.desktop.components.ErrorPanel
import it.charitymarket.desktop.components.LoadingPanel
import it.charitymarket.desktop.components.ScreenHeader
import it.charitymarket.desktop.model.RoleRules
import it.charitymarket.desktop.model.formatMoney

private data class DashboardSummary(
    val donors: Int? = null,
    val items: Int? = null,
    val availableItems: Int? = null,
    val soldItems: Int? = null,
    val transactions: Int? = null,
    val salesTotalCents: Long? = null
)

@Composable
fun DashboardScreen(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    roles: Set<UserRole>,
    currencyCode: String
) {
    var loading by remember { mutableStateOf(true) }
    var summary by remember {
        mutableStateOf(DashboardSummary())
    }
    var errors by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var refreshKey by remember { mutableStateOf(0) }

    suspend fun load() {
        loading = true
        errors = emptyList()

        var nextSummary = DashboardSummary()
        val nextErrors = mutableListOf<String>()

        if (RoleRules.canAccessDonors(roles)) {
            runCatching {
                apiClient.listDonors()
            }.onSuccess { donors ->
                nextSummary = nextSummary.copy(
                    donors = donors.size
                )
            }.onFailure { exception ->
                nextErrors +=
                    "Donors: ${appState.handleRequestFailure(exception)}"
            }
        }

        if (RoleRules.canAccessItems(roles)) {
            runCatching {
                apiClient.listItems()
            }.onSuccess { items ->
                nextSummary = nextSummary.copy(
                    items = items.size,
                    availableItems =
                        items.count {
                            it.status == ItemStatus.AVAILABLE
                        },
                    soldItems =
                        items.count {
                            it.status == ItemStatus.SOLD
                        }
                )
            }.onFailure { exception ->
                nextErrors +=
                    "Items: ${appState.handleRequestFailure(exception)}"
            }
        }

        if (RoleRules.canAccessSales(roles)) {
            runCatching {
                apiClient.listSales()
            }.onSuccess { sales ->
                val completedSales = sales.filter {
                    it.status == SaleStatus.COMPLETED
                }

                nextSummary = nextSummary.copy(
                    transactions = completedSales.size,
                    salesTotalCents =
                        completedSales.sumOf { it.totalCents }
                )
            }.onFailure { exception ->
                nextErrors +=
                    "Transactions: ${appState.handleRequestFailure(exception)}"
            }
        }

        summary = nextSummary
        errors = nextErrors
        loading = false
    }

    LaunchedEffect(refreshKey, roles) {
        load()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Dashboard",
            subtitle = "Live totals from the connected Charity Market server.",
            action = {
                Button(
                    onClick = {
                        refreshKey += 1
                    },
                    enabled = !loading
                ) {
                    Text("Refresh")
                }
            }
        )

        if (loading) {
            LoadingPanel("Loading dashboard...")
            return@Column
        }

        if (errors.isNotEmpty()) {
            ErrorPanel(
                message = errors.joinToString("\n"),
                onRetry = {
                    refreshKey += 1
                }
            )
        }

        val hasAnyValue = listOf(
            summary.donors,
            summary.items,
            summary.availableItems,
            summary.soldItems,
            summary.transactions,
            summary.salesTotalCents
        ).any { it != null }

        if (!hasAnyValue) {
            EmptyStatePanel(
                title = "No dashboard data available",
                message = "Your current role does not expose donor, item, or transaction summaries."
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "Donors",
                value = summary.donors?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Items",
                value = summary.items?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Available",
                value = summary.availableItems?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = "Sold",
                value = summary.soldItems?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Transactions",
                value = summary.transactions?.toString() ?: "-",
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Recorded sales",
                value = summary.salesTotalCents?.let {
                    formatMoney(it, currencyCode)
                } ?: "-",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style =
                    MaterialTheme.typography.headlineMedium
            )
        }
    }
}
