package com.example.premium

import com.example.premium.domain.model.Entitlement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valida la regla que usa la app para decidir si una feature está activa.
 * Espejo de `PremiumManager.hasFeature`: status activo + días restantes > 0.
 */
class EntitlementModelTest {

    private fun entitlement(status: String, daysLeft: Int) = Entitlement(
        id = "ent-1",
        featureKey = "chat",
        productCode = "chat_gold_5d",
        name = "Chat Gold 5 días",
        emoji = "💬",
        startsAt = "2026-09-21T00:00:00Z",
        expiresAt = "2026-09-26T00:00:00Z",
        status = status,
        daysLeft = daysLeft
    )

    @Test
    fun `activa con status active y dias restantes`() {
        assertTrue(entitlement(status = "active", daysLeft = 5).isActive)
    }

    @Test
    fun `inactiva si status es expired aunque sobre dias`() {
        assertFalse(entitlement(status = "expired", daysLeft = 3).isActive)
    }

    @Test
    fun `inactiva si status active pero dias agotados`() {
        assertFalse(entitlement(status = "active", daysLeft = 0).isActive)
    }

    @Test
    fun `inactiva si status es revoked`() {
        assertFalse(entitlement(status = "revoked", daysLeft = 1).isActive)
    }
}