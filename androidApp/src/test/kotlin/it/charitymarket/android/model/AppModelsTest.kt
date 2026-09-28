package it.charitymarket.android.model

import it.charitymarket.shared.api.UserRole
import it.charitymarket.shared.auth.AppDestination
import it.charitymarket.shared.auth.RoleRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelsTest {
    @Test
    fun parsesCurrencyWithoutFloatingPoint() {
        assertEquals(1234L, parseMoneyToCents("12.34"))
        assertEquals(1234L, parseMoneyToCents("12,34"))
        assertEquals(1200L, parseMoneyToCents("12"))
        assertNull(parseMoneyToCents("-0.01"))
        assertNull(parseMoneyToCents("not money"))
    }

    @Test
    fun sellerCanSellButCannotEditInventory() {
        val roles = setOf(UserRole.SELLER)

        assertTrue(RoleRules.canAccessDonors(roles))
        assertTrue(RoleRules.canAccessItems(roles))
        assertTrue(RoleRules.canAccessSales(roles))
        assertFalse(RoleRules.canEditInventory(roles))
        assertFalse(RoleRules.canDeleteDonors(roles))
    }

    @Test
    fun administratorReceivesEveryDestination() {
        assertEquals(
            AppDestination.entries,
            RoleRules.destinationsFor(
                setOf(UserRole.SYSTEM_ADMINISTRATOR)
            )
        )
    }
}
