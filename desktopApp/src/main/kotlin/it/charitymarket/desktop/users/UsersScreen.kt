package it.charitymarket.desktop.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.charitymarket.desktop.api.CharityMarketApiClient
import it.charitymarket.desktop.api.CreateUserRequest
import it.charitymarket.desktop.api.UserResponse
import it.charitymarket.desktop.api.UserRole
import it.charitymarket.desktop.api.UserStatus
import it.charitymarket.desktop.app.DesktopAppState
import it.charitymarket.desktop.components.ConfirmationDialog
import it.charitymarket.desktop.components.EmptyStatePanel
import it.charitymarket.desktop.components.ErrorPanel
import it.charitymarket.desktop.components.FeedbackMessage
import it.charitymarket.desktop.components.LoadingPanel
import it.charitymarket.desktop.components.PasswordField
import it.charitymarket.desktop.components.RoleBadges
import it.charitymarket.desktop.components.ScreenHeader
import it.charitymarket.desktop.components.SearchField
import it.charitymarket.desktop.components.StatusBadge
import it.charitymarket.desktop.model.displayName
import it.charitymarket.desktop.model.formatInstant
import kotlinx.coroutines.launch

@Composable
fun UsersScreen(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState
) {
    var users by remember {
        mutableStateOf<List<UserResponse>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }
    var feedback by remember {
        mutableStateOf<String?>(null)
    }
    var search by remember { mutableStateOf("") }
    var showCreateDialog by remember {
        mutableStateOf(false)
    }
    var roleTarget by remember {
        mutableStateOf<UserResponse?>(null)
    }
    var suspendTarget by remember {
        mutableStateOf<UserResponse?>(null)
    }
    var enableTarget by remember {
        mutableStateOf<UserResponse?>(null)
    }
    var refreshKey by remember { mutableStateOf(0) }

    suspend fun load() {
        loading = true
        errorMessage = null

        runCatching {
            apiClient.listUsers()
        }.onSuccess { result ->
            users = result
        }.onFailure { exception ->
            errorMessage =
                appState.handleRequestFailure(exception)
        }

        loading = false
    }

    LaunchedEffect(refreshKey) {
        load()
    }

    val filteredUsers = users.filter { user ->
        val query = search.trim()

        query.isBlank() ||
                user.username.contains(
                    query,
                    ignoreCase = true
                ) ||
                user.displayName.contains(
                    query,
                    ignoreCase = true
                ) ||
                user.email.orEmpty()
                    .contains(query, ignoreCase = true)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Users",
            subtitle = "Administrator-only account and role management.",
            action = {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            refreshKey += 1
                        },
                        enabled = !loading
                    ) {
                        Text("Refresh")
                    }

                    Button(
                        onClick = {
                            showCreateDialog = true
                        }
                    ) {
                        Text("Create user")
                    }
                }
            }
        )

        SearchField(
            value = search,
            onValueChange = {
                search = it
            },
            label = "Search users",
            modifier = Modifier.fillMaxWidth()
        )

        FeedbackMessage(
            message = feedback,
            isError = false
        )

        if (loading) {
            LoadingPanel("Loading users...")
        } else if (errorMessage != null) {
            ErrorPanel(
                message = errorMessage.orEmpty(),
                onRetry = {
                    refreshKey += 1
                }
            )
        } else if (filteredUsers.isEmpty()) {
            EmptyStatePanel(
                title = if (users.isEmpty()) {
                    "No users found"
                } else {
                    "No matching users"
                },
                message = "Create staff accounts and assign at least one role."
            )
        } else {
            UserList(
                users = filteredUsers,
                currentUserId = appState.uiState.authenticatedUser?.id,
                onEditRoles = {
                    roleTarget = it
                },
                onSuspend = {
                    suspendTarget = it
                },
                onEnable = {
                    enableTarget = it
                }
            )
        }
    }

    if (showCreateDialog) {
        CreateUserDialog(
            apiClient = apiClient,
            appState = appState,
            onDismiss = {
                showCreateDialog = false
            },
            onCreated = { user ->
                users = (users + user)
                    .sortedBy { it.username }
                feedback =
                    "User created: ${user.username}"
                showCreateDialog = false
            }
        )
    }

    roleTarget?.let { user ->
        EditRolesDialog(
            apiClient = apiClient,
            appState = appState,
            user = user,
            onDismiss = {
                roleTarget = null
            },
            onUpdated = { updated ->
                users = users.map {
                    if (it.id == updated.id) {
                        updated
                    } else {
                        it
                    }
                }
                feedback =
                    "Roles updated for ${updated.username}"
                roleTarget = null
            }
        )
    }

    suspendTarget?.let { user ->
        SuspendUserDialog(
            apiClient = apiClient,
            appState = appState,
            user = user,
            onDismiss = {
                suspendTarget = null
            },
            onSuspended = { updated ->
                users = users.map {
                    if (it.id == updated.id) {
                        updated
                    } else {
                        it
                    }
                }
                feedback =
                    "User suspended: ${updated.username}"
                suspendTarget = null
            }
        )
    }

    enableTarget?.let { user ->
        EnableUserDialog(
            apiClient = apiClient,
            appState = appState,
            user = user,
            onDismiss = {
                enableTarget = null
            },
            onEnabled = { updated ->
                users = users.map {
                    if (it.id == updated.id) {
                        updated
                    } else {
                        it
                    }
                }
                feedback =
                    "User re-enabled: ${updated.username}"
                enableTarget = null
            }
        )
    }
}

@Composable
private fun UserList(
    users: List<UserResponse>,
    currentUserId: String?,
    onEditRoles: (UserResponse) -> Unit,
    onSuspend: (UserResponse) -> Unit,
    onEnable: (UserResponse) -> Unit
) {
    Card {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "User",
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Status",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Roles",
                    modifier = Modifier.weight(1.6f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Last login",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Actions",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            HorizontalDivider()

            LazyColumn {
                items(
                    users,
                    key = { it.id }
                ) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text(user.username)
                            Text(
                                user.displayName,
                                style =
                                    MaterialTheme.typography.bodySmall,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                            )
                            if (!user.email.isNullOrBlank()) {
                                Text(
                                    user.email,
                                    style =
                                        MaterialTheme.typography.bodySmall
                                )
                            }
                            if (user.mustChangePassword) {
                                Text(
                                    "Password change required",
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.weight(0.8f)
                        ) {
                            StatusBadge(
                                text = user.status.name,
                                color = when (user.status) {
                                    UserStatus.ACTIVE ->
                                        MaterialTheme.colorScheme.primary

                                    else ->
                                        MaterialTheme.colorScheme.error
                                }
                            )
                        }

                        RoleBadges(
                            roles = user.roles,
                            modifier = Modifier.weight(1.6f)
                        )

                        Text(
                            formatInstant(user.lastLoginAt),
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    onEditRoles(user)
                                }
                            ) {
                                Text("Roles")
                            }

                            if (user.status == UserStatus.ACTIVE) {
                                if (user.id == currentUserId) {
                                    Text(
                                        "Current account",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    TextButton(
                                        onClick = {
                                            onSuspend(user)
                                        }
                                    ) {
                                        Text("Suspend")
                                    }
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        onEnable(user)
                                    }
                                ) {
                                    Text("Re-enable")
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CreateUserDialog(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    onCreated: (UserResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember {
        mutableStateOf(false)
    }
    var roles by remember {
        mutableStateOf(setOf<UserRole>())
    }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val trimmedEmail = email.trim()
    val emailInvalid =
        trimmedEmail.isNotBlank() &&
                !trimmedEmail.matches(
                    Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                )

    fun submit() {
        if (submitting) {
            return
        }

        when {
            username.trim().isBlank() ->
                errorMessage = "The username is required."

            displayName.trim().isBlank() ->
                errorMessage = "The display name is required."

            emailInvalid ->
                errorMessage = "The email address is invalid."

            password.length < 8 ->
                errorMessage =
                    "The temporary password must contain at least 8 characters."

            roles.isEmpty() ->
                errorMessage =
                    "Select at least one role."

            else -> {
                coroutineScope.launch {
                    submitting = true
                    errorMessage = null

                    runCatching {
                        apiClient.createUser(
                            CreateUserRequest(
                                username =
                                    username.trim(),
                                displayName =
                                    displayName.trim(),
                                email = trimmedEmail
                                    .ifBlank { null },
                                temporaryPassword = password,
                                roles = roles
                            )
                        )
                    }.onSuccess { user ->
                        onCreated(user)
                    }.onFailure { exception ->
                        errorMessage =
                            appState
                                .handleRequestFailure(
                                    exception
                                )
                    }

                    submitting = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!submitting) {
                onDismiss()
            }
        },
        title = {
            Text("Create user")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                    },
                    label = {
                        Text("Username")
                    },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                    },
                    label = {
                        Text("Display name")
                    },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                    },
                    label = {
                        Text("Email")
                    },
                    singleLine = true,
                    enabled = !submitting,
                    isError = emailInvalid,
                    modifier = Modifier.fillMaxWidth()
                )

                PasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                    },
                    label = "Temporary password",
                    visible = passwordVisible,
                    onToggleVisible = {
                        passwordVisible = !passwordVisible
                    },
                    enabled = !submitting,
                    supportingText = "At least 8 characters.",
                    modifier = Modifier.fillMaxWidth()
                )

                RoleSelector(
                    selectedRoles = roles,
                    onSelectedRolesChange = {
                        roles = it
                    },
                    enabled = !submitting
                )

                FeedbackMessage(
                    message = errorMessage,
                    isError = true
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !submitting
            ) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                enabled = !submitting
            ) {
                Text("Create")
            }
        }
    )
}

@Composable
private fun EditRolesDialog(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    user: UserResponse,
    onDismiss: () -> Unit,
    onUpdated: (UserResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedRoles by remember {
        mutableStateOf(user.roles)
    }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    fun submit() {
        if (submitting) {
            return
        }

        if (selectedRoles.isEmpty()) {
            errorMessage =
                "A user must have at least one role."
            return
        }

        coroutineScope.launch {
            submitting = true
            errorMessage = null

            runCatching {
                apiClient.replaceUserRoles(
                    userId = user.id,
                    roles = selectedRoles
                )
            }.onSuccess { updated ->
                onUpdated(updated)
            }.onFailure { exception ->
                errorMessage =
                    appState.handleRequestFailure(
                        exception
                    )
            }

            submitting = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!submitting) {
                onDismiss()
            }
        },
        title = {
            Text("Edit roles")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                Text("${user.username} - ${user.displayName}")
                RoleSelector(
                    selectedRoles = selectedRoles,
                    onSelectedRolesChange = {
                        selectedRoles = it
                    },
                    enabled = !submitting
                )
                FeedbackMessage(
                    message = errorMessage,
                    isError = true
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !submitting
            ) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                enabled = !submitting
            ) {
                Text("Save roles")
            }
        }
    )
}

@Composable
private fun SuspendUserDialog(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    user: UserResponse,
    onDismiss: () -> Unit,
    onSuspended: (UserResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    ConfirmationDialog(
        title = "Suspend user?",
        message = "Suspending ${user.username} prevents that account from signing in.",
        confirmLabel = "Suspend",
        busy = submitting,
        busyMessage = "Suspending user...",
        onCancel = onDismiss,
        onConfirm = {
            if (submitting) {
                return@ConfirmationDialog
            }

            coroutineScope.launch {
                submitting = true
                errorMessage = null

                runCatching {
                    apiClient.suspendUser(user.id)
                }.onSuccess { updated ->
                    onSuspended(updated)
                }.onFailure { exception ->
                    errorMessage =
                        appState.handleRequestFailure(
                            exception
                        )
                }

                submitting = false
            }
        }
    )

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = {
                errorMessage = null
            },
            title = {
                Text("Suspend failed")
            },
            text = {
                Text(errorMessage.orEmpty())
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        errorMessage = null
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun EnableUserDialog(
    apiClient: CharityMarketApiClient,
    appState: DesktopAppState,
    user: UserResponse,
    onDismiss: () -> Unit,
    onEnabled: (UserResponse) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    ConfirmationDialog(
        title = "Re-enable user?",
        message = "Re-enabling ${user.username} restores access for that account.",
        confirmLabel = "Re-enable",
        busy = submitting,
        busyMessage = "Re-enabling user...",
        onCancel = onDismiss,
        onConfirm = {
            if (submitting) {
                return@ConfirmationDialog
            }

            coroutineScope.launch {
                submitting = true
                errorMessage = null

                runCatching {
                    apiClient.enableUser(user.id)
                }.onSuccess { updated ->
                    onEnabled(updated)
                }.onFailure { exception ->
                    errorMessage =
                        appState.handleRequestFailure(
                            exception
                        )
                }

                submitting = false
            }
        }
    )

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = {
                errorMessage = null
            },
            title = {
                Text("Re-enable failed")
            },
            text = {
                Text(errorMessage.orEmpty())
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        errorMessage = null
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun RoleSelector(
    selectedRoles: Set<UserRole>,
    onSelectedRolesChange: (Set<UserRole>) -> Unit,
    enabled: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Roles",
            style = MaterialTheme.typography.labelLarge
        )

        UserRole.entries.forEach { role ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = role in selectedRoles,
                    onCheckedChange = { checked ->
                        onSelectedRolesChange(
                            if (checked) {
                                selectedRoles + role
                            } else {
                                selectedRoles - role
                            }
                        )
                    },
                    enabled = enabled
                )
                Text(role.displayName())
            }
        }
    }
}
