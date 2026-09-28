package it.charitymarket.android.users

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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.shared.api.CreateUserRequest
import it.charitymarket.shared.api.UserResponse
import it.charitymarket.shared.api.UserRole
import it.charitymarket.shared.api.UserStatus
import it.charitymarket.android.app.AndroidAppState
import it.charitymarket.android.components.ConfirmationDialog
import it.charitymarket.android.components.EmptyStatePanel
import it.charitymarket.android.components.ErrorPanel
import it.charitymarket.android.components.FeedbackMessage
import it.charitymarket.android.components.LoadingPanel
import it.charitymarket.android.components.PasswordField
import it.charitymarket.android.components.RoleBadges
import it.charitymarket.android.components.ScreenHeader
import it.charitymarket.android.components.SearchField
import it.charitymarket.android.components.StatusBadge
import it.charitymarket.shared.auth.displayName
import it.charitymarket.android.model.formatInstant
import it.charitymarket.android.model.isEmailValid
import kotlinx.coroutines.launch

@Composable
fun UsersScreen(appState: AndroidAppState) {
    val api = appState.requireApiClient()
    var users by remember { mutableStateOf<List<UserResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var roleTarget by remember { mutableStateOf<UserResponse?>(null) }
    var statusTarget by remember { mutableStateOf<UserResponse?>(null) }
    var hasLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, appState.uiState.autoRefreshGeneration) {
        if (!hasLoaded) loading = true
        error = null
        runCatching { api.listUsers() }
            .onSuccess {
                if (users != it) users = it
            }
            .onFailure { error = appState.handleRequestFailure(it) }
        loading = false
        hasLoaded = true
    }

    val filtered = users.filter { user ->
        val query = search.trim()
        query.isBlank() ||
                user.username.contains(query, true) ||
                user.displayName.contains(query, true) ||
                user.email.orEmpty().contains(query, true)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenHeader(
            "Users",
            "Administrator-only account management.",
            "Refresh",
            !loading
        ) { refreshKey++ }
        Button(
            onClick = { showCreate = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create user") }
        SearchField(
            search,
            { search = it },
            "Search users",
            Modifier.fillMaxWidth()
        )

        when {
            loading -> LoadingPanel("Loading users...")
            error != null -> ErrorPanel(error.orEmpty()) { refreshKey++ }
            filtered.isEmpty() -> EmptyStatePanel(
                if (users.isEmpty()) "No users found" else "No matching users",
                "Create staff accounts and assign at least one role."
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        isCurrent = user.id ==
                                appState.uiState.authenticatedUser?.id,
                        onRoles = { roleTarget = user },
                        onStatus = { statusTarget = user }
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateUserDialog(
            appState = appState,
            onDismiss = { showCreate = false },
            onCreated = {
                showCreate = false
                refreshKey++
                appState.showMessage("User created: ${it.username}")
            }
        )
    }
    roleTarget?.let { user ->
        RolesDialog(
            user,
            appState,
            onDismiss = { roleTarget = null },
            onUpdated = {
                roleTarget = null
                users = users.map { current ->
                    if (current.id == it.id) it else current
                }
                appState.showMessage("Roles updated for ${it.username}")
            }
        )
    }
    statusTarget?.let { user ->
        UserStatusDialog(
            user,
            appState,
            onDismiss = { statusTarget = null },
            onUpdated = {
                statusTarget = null
                users = users.map { current ->
                    if (current.id == it.id) it else current
                }
                appState.showMessage("Account updated: ${it.username}")
            }
        )
    }
}

@Composable
private fun UserCard(
    user: UserResponse,
    isCurrent: Boolean,
    onRoles: () -> Unit,
    onStatus: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("@${user.username}")
                }
                StatusBadge(
                    user.status.name,
                    if (user.status == UserStatus.ACTIVE) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.error
                )
            }
            Text(user.email ?: "No email")
            RoleBadges(user.roles)
            Text("Last login: ${formatInstant(user.lastLoginAt)}")
            if (user.mustChangePassword) {
                Text(
                    "Password change required",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRoles) { Text("Edit roles") }
                if (!isCurrent) {
                    TextButton(onClick = onStatus) {
                        Text(
                            if (user.status == UserStatus.ACTIVE) {
                                "Suspend"
                            } else "Re-enable"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateUserDialog(
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onCreated: (UserResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var roles by remember { mutableStateOf(emptySet<UserRole>()) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (submitting) return
        error = when {
            username.trim().isBlank() -> "The username is required."
            displayName.trim().isBlank() -> "The display name is required."
            !isEmailValid(email.trim()) -> "The email address is invalid."
            password.length < 8 ->
                "The temporary password must contain at least 8 characters."
            roles.isEmpty() -> "Select at least one role."
            else -> null
        }
        if (error != null) return
        scope.launch {
            submitting = true
            runCatching {
                appState.requireApiClient().createUser(
                    CreateUserRequest(
                        username.trim(),
                        displayName.trim(),
                        email.trim().ifBlank { null },
                        password,
                        roles
                    )
                )
            }.onSuccess(onCreated)
                .onFailure { error = appState.handleRequestFailure(it) }
            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Create user") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    username, { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !submitting
                )
                OutlinedTextField(
                    displayName, { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    enabled = !submitting
                )
                OutlinedTextField(
                    email, { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !submitting,
                    isError = !isEmailValid(email.trim())
                )
                PasswordField(
                    password,
                    { password = it },
                    "Temporary password",
                    passwordVisible,
                    { passwordVisible = !passwordVisible },
                    !submitting,
                    Modifier.fillMaxWidth(),
                    "At least 8 characters."
                )
                RoleSelector(roles, { roles = it }, !submitting)
                FeedbackMessage(error, true)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !submitting) {
                Text(if (submitting) "Creating..." else "Create")
            }
        }
    )
}

@Composable
private fun RolesDialog(
    user: UserResponse,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onUpdated: (UserResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    var roles by remember { mutableStateOf(user.roles) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (submitting) return
        if (roles.isEmpty()) {
            error = "A user must have at least one role."
            return
        }
        scope.launch {
            submitting = true
            runCatching {
                appState.requireApiClient().replaceUserRoles(user.id, roles)
            }.onSuccess(onUpdated)
                .onFailure { error = appState.handleRequestFailure(it) }
            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Edit roles") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${user.username} — ${user.displayName}")
                RoleSelector(roles, { roles = it }, !submitting)
                FeedbackMessage(error, true)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !submitting) {
                Text("Save")
            }
        }
    )
}

@Composable
private fun UserStatusDialog(
    user: UserResponse,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onUpdated: (UserResponse) -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val suspending = user.status == UserStatus.ACTIVE

    ConfirmationDialog(
        title = if (suspending) "Suspend user?" else "Re-enable user?",
        message = if (suspending) {
            "Suspending ${user.username} prevents that account from signing in."
        } else {
            "Re-enabling ${user.username} restores access for that account."
        },
        confirmLabel = if (suspending) "Suspend" else "Re-enable",
        busy = busy,
        busyMessage = "Updating account...",
        onCancel = onDismiss,
        onConfirm = {
            if (!busy) {
                scope.launch {
                    busy = true
                    runCatching {
                        if (suspending) {
                            appState.requireApiClient().suspendUser(user.id)
                        } else {
                            appState.requireApiClient().enableUser(user.id)
                        }
                    }.onSuccess(onUpdated)
                        .onFailure {
                            error = appState.handleRequestFailure(it)
                        }
                    busy = false
                }
            }
        }
    )
    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Account update failed") },
            text = { Text(error.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { error = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun RoleSelector(
    selected: Set<UserRole>,
    onSelectedChange: (Set<UserRole>) -> Unit,
    enabled: Boolean
) {
    Column {
        Text("Roles", style = MaterialTheme.typography.titleSmall)
        UserRole.entries.forEach { role ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = role in selected,
                    onCheckedChange = { checked ->
                        onSelectedChange(
                            if (checked) selected + role else selected - role
                        )
                    },
                    enabled = enabled
                )
                Text(role.displayName())
            }
        }
    }
}
