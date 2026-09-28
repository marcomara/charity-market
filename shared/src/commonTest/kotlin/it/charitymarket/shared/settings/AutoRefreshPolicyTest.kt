package it.charitymarket.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AutoRefreshPolicyTest {
    @Test
    fun serverPolicyControlsEffectiveClientInterval() {
        val fixedPolicy = AutoRefreshPolicy(
            usersCanCustomize = false,
            defaultIntervalSeconds = 10
        )
        val preference = ClientAutoRefreshPreference(
            enabled = false,
            intervalSeconds = 15
        )

        assertTrue(fixedPolicy.isActive(preference))
        assertEquals(10, fixedPolicy.effectiveIntervalSeconds(preference))
    }

    @Test
    fun userPreferenceAppliesOnlyInsideConfiguredRange() {
        val policy = AutoRefreshPolicy(usersCanCustomize = true)

        assertEquals(
            15,
            policy.effectiveIntervalSeconds(
                ClientAutoRefreshPreference(intervalSeconds = 90)
            )
        )
        assertFalse(
            policy.isActive(ClientAutoRefreshPreference(enabled = false))
        )
    }

    @Test
    fun invalidDefaultOutsideRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AutoRefreshPolicy(
                defaultIntervalSeconds = 20,
                minimumIntervalSeconds = 5,
                maximumIntervalSeconds = 15
            ).validated()
        }
    }
}
