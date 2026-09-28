package it.charitymarket.shared.auth

import it.charitymarket.shared.api.AuthenticatedUserResponse
import it.charitymarket.shared.api.UserRole
import it.charitymarket.shared.api.UserStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthAndRoleRulesTest {
    private val seller = AuthenticatedUserResponse(
        id = "seller-id",
        username = "seller",
        displayName = "Seller",
        status = UserStatus.ACTIVE,
        mustChangePassword = false,
        roles = setOf(UserRole.SELLER)
    )

    @Test
    fun sessionKeepsTokenOnlyInMemoryAndClearsItTogetherWithUser() {
        val session = InMemoryAuthSession()

        session.establish("token", seller)
        assertTrue(session.isAuthenticated)
        assertEquals("token", session.accessToken)
        assertEquals(seller, session.authenticatedUser)

        session.clear()
        assertFalse(session.isAuthenticated)
        assertNull(session.accessToken)
        assertNull(session.authenticatedUser)
    }

    @Test
    fun sellerPermissionsMatchServerRolePolicy() {
        val roles = seller.roles

        assertTrue(RoleRules.canAccessDonors(roles))
        assertTrue(RoleRules.canAccessItems(roles))
        assertTrue(RoleRules.canAccessSales(roles))
        assertFalse(RoleRules.canEditInventory(roles))
        assertFalse(RoleRules.canManageUsers(roles))
    }

    @Test
    fun administratorReceivesEveryDestination() {
        assertEquals(
            AppDestination.entries,
            RoleRules.destinationsFor(setOf(UserRole.SYSTEM_ADMINISTRATOR))
        )
    }
}
