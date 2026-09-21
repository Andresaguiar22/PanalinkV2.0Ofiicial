package com.example.premium.domain

import com.example.premium.data.repository.PremiumRepositoryImpl
import com.example.premium.domain.model.Entitlement
import com.example.premium.domain.model.PremiumBuyResult
import com.example.premium.domain.model.PremiumProduct
import com.example.premium.domain.model.WalletBalance
import com.example.premium.domain.repository.PremiumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Feature keys premium (coinciden con premium_features.feature_key).
 */
object PremiumFeatures {
    const val CHAT = "chat"
    const val STORY = "story"
    const val LIVE = "live"
    const val WALL = "wall"
    const val VOICE = "voice"
    const val PANATV = "panatv"
}

/**
 * Manager singleton de Premium 2.0.
 *
 * Expone reactivamente:
 *  - saldo del wallet (coins/diamonds/tickets/xp/nivel)
 *  - entitlements activos del usuario por feature
 *  - catálogo de productos
 *
 * Es la fuente de verdad en memoria para PremiumGate y la UI. Refresca desde
 * Supabase y mantiene un caché local hasta el próximo arranque.
 */
object PremiumManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: PremiumRepository by lazy { PremiumRepositoryImpl() }

    private val _wallet = MutableStateFlow(WalletBalance())
    val wallet: StateFlow<WalletBalance> = _wallet.asStateFlow()

    private val _entitlements = MutableStateFlow<List<Entitlement>>(emptyList())
    val entitlements: StateFlow<List<Entitlement>> = _entitlements.asStateFlow()

    private val _catalog = MutableStateFlow<List<PremiumProduct>>(emptyList())
    val catalog: StateFlow<List<PremiumProduct>> = _catalog.asStateFlow()

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    /** Job del timer de expiración local (invalida una feature al vencer). */
    private var expiryJob: kotlinx.coroutines.Job? = null

    /**
     * Agenda una invalidación local cuando el entitlement activo más próximo
     * expira. Sin esto, la feature seguiría activa en memoria hasta el próximo
     * refresh aunque el servidor ya la considere vencida.
     */
    private fun scheduleExpiry() {
        expiryJob?.cancel()
        val now = System.currentTimeMillis()
        val next = _entitlements.value
            .asSequence()
            .filter { it.isActive }
            .mapNotNull { parseIsoToMillis(it.expiresAt) }
            .filter { it > now }
            .minOrNull()
            ?: return
        val delayMs = next - now
        expiryJob = scope.launch {
            kotlinx.coroutines.delay(delayMs)
            // Re-evalúa localmente: marca como expirados los que vencieron.
            _entitlements.value = _entitlements.value.map {
                if (it.isActive && (parseIsoToMillis(it.expiresAt) ?: Long.MAX_VALUE) < System.currentTimeMillis()) {
                    it.copy(status = "expired", daysLeft = 0)
                } else it
            }
            scheduleExpiry()
        }
    }

    private fun parseIsoToMillis(iso: String): Long? = runCatching {
        java.time.Instant.parse(iso).toEpochMilli()
    }.getOrNull()

    /**
     * Carga inicial asíncrona (llamar en el arranque de la app o al entrar al
     * centro Premium).
     */
    fun initialize() {
        if (_initialized.value) return
        refreshAll()
    }

    /** Refresca saldo + entitlements + catálogo en paralelo. */
    fun refreshAll() {
        scope.launch {
            repository.getWalletBalance().onSuccess { _wallet.value = it }
            repository.getMyEntitlements().onSuccess {
                _entitlements.value = it
                scheduleExpiry()
            }
            repository.getCatalog().onSuccess { _catalog.value = it }
            _initialized.value = true
        }
    }

    /** Refresca solo saldo (tras compra/recompensa). */
    fun refreshWallet() {
        scope.launch {
            repository.getWalletBalance().onSuccess { _wallet.value = it }
        }
    }

    /** Refresca solo entitlements. */
    fun refreshEntitlements() {
        scope.launch {
            repository.getMyEntitlements().onSuccess {
                _entitlements.value = it
                scheduleExpiry()
            }
        }
    }

    /**
     * Limpia todo el estado en memoria (logout o cambio de usuario). Sin esto,
     * el siguiente usuario heredaría saldo/entitlements del anterior.
     */
    fun reset() {
        expiryJob?.cancel()
        _wallet.value = WalletBalance()
        _entitlements.value = emptyList()
        _catalog.value = emptyList()
        _initialized.value = false
    }

    /** Devuelve el entitlement activo de una feature (o null). */
    fun entitlementFor(featureKey: String): Entitlement? =
        _entitlements.value.firstOrNull { it.featureKey == featureKey && it.isActive }

    /** Tiene la feature activa? */
    fun hasFeature(featureKey: String): Boolean =
        entitlementFor(featureKey) != null

    /** Días restantes de la feature activa. */
    fun daysLeftFor(featureKey: String): Int? =
        entitlementFor(featureKey)?.daysLeft

    /**
     * Compra un producto. Devuelve el resultado y refresca estado en memoria
     * si fue exitoso.
     */
    fun buyProduct(
        productCode: String,
        requestId: String? = null,
        onResult: (Result<PremiumBuyResult>) -> Unit
    ) {
        scope.launch {
            val result = repository.buyProduct(productCode, requestId)
            result.onSuccess { buy ->
                if (buy.ok) {
                    refreshWallet()
                    refreshEntitlements()
                }
            }
            onResult(result)
        }
    }

    /** Monedas actuales. */
    val coins: Int get() = _wallet.value.coins

    /** Nivel actual del usuario. */
    val level: Int get() = _wallet.value.level
}