package it.charitymarket.shared.auth

import it.charitymarket.shared.api.UserRole

enum class AppDestination(val title: String) {
    DASHBOARD("Dashboard"),
    USERS("Users"),
    DONORS("Donors"),
    ITEMS("Items"),
    SALES("Transactions"),
    SETTINGS("Settings")
}

object RoleRules {
    private val businessReaders = setOf(
        UserRole.SYSTEM_ADMINISTRATOR,
        UserRole.MARKET_MANAGER,
        UserRole.INVENTORY_MANAGER,
        UserRole.SELLER
    )

    private val inventoryEditors = setOf(
        UserRole.SYSTEM_ADMINISTRATOR,
        UserRole.MARKET_MANAGER,
        UserRole.INVENTORY_MANAGER
    )

    private val salesUsers = setOf(
        UserRole.SYSTEM_ADMINISTRATOR,
        UserRole.MARKET_MANAGER,
        UserRole.SELLER
    )

    fun destinationsFor(roles: Set<UserRole>): List<AppDestination> =
        buildList {
            add(AppDestination.DASHBOARD)
            if (canManageUsers(roles)) add(AppDestination.USERS)
            if (canAccessDonors(roles)) add(AppDestination.DONORS)
            if (canAccessItems(roles)) add(AppDestination.ITEMS)
            if (canAccessSales(roles)) add(AppDestination.SALES)
            add(AppDestination.SETTINGS)
        }

    fun canManageUsers(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canAccessDonors(roles: Set<UserRole>): Boolean =
        roles.any { it in businessReaders }

    fun canAccessItems(roles: Set<UserRole>): Boolean =
        roles.any { it in businessReaders }

    fun canEditInventory(roles: Set<UserRole>): Boolean =
        roles.any { it in inventoryEditors }

    fun canEditDonors(roles: Set<UserRole>): Boolean =
        canEditInventory(roles)

    fun canDeleteDonors(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canEditItems(roles: Set<UserRole>): Boolean =
        canEditInventory(roles)

    fun canDeleteItems(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canAccessSales(roles: Set<UserRole>): Boolean =
        roles.any { it in salesUsers }

    fun canManageSales(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canManageSettings(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles

    fun canShutDownLocalServer(roles: Set<UserRole>): Boolean =
        UserRole.SYSTEM_ADMINISTRATOR in roles
}

fun UserRole.displayName(): String = name
    .lowercase()
    .split('_')
    .joinToString(" ") { word ->
        word.replaceFirstChar { character ->
            character.titlecase()
        }
    }
