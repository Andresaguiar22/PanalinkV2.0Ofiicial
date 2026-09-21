package com.example.premium.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.premium.data.repository.PremiumRepositoryImpl
import com.example.premium.domain.model.*
import com.example.premium.domain.repository.PremiumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del Centro Premium 2.0.
 * Orquesta la vista: saldo, entitlements, catálogo, promos, eventos,
 * recompensa diaria, misiones y notificaciones.
 */
class PremiumViewModel : ViewModel() {
    private val repository: PremiumRepository by lazy { PremiumRepositoryImpl() }

    private val _state = MutableStateFlow(PremiumUiState())
    val state: StateFlow<PremiumUiState> = _state.asStateFlow()

    private val _buyingCode = MutableStateFlow<String?>(null)
    val buyingCode: StateFlow<String?> = _buyingCode.asStateFlow()

    private val _claimingReward = MutableStateFlow(false)
    val claimingReward: StateFlow<Boolean> = _claimingReward.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _cosmetics = MutableStateFlow<MyCosmeticsResponse?>(null)
    val cosmetics: StateFlow<MyCosmeticsResponse?> = _cosmetics.asStateFlow()

    private val _equippingCode = MutableStateFlow<String?>(null)
    val equippingCode: StateFlow<String?> = _equippingCode.asStateFlow()

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repository.getWalletBalance().onSuccess { b ->
                _state.value = _state.value.copy(wallet = b)
            }
            repository.getMyEntitlements().onSuccess { list ->
                _state.value = _state.value.copy(entitlements = list)
            }
            repository.getCatalog().onSuccess { list ->
                _state.value = _state.value.copy(products = list)
            }
            repository.getPromotions().onSuccess { list ->
                _state.value = _state.value.copy(promotions = list)
            }
            repository.getEvents().onSuccess { list ->
                _state.value = _state.value.copy(events = list)
            }
            repository.getDailyRewardStatus().onSuccess { s ->
                _state.value = _state.value.copy(dailyReward = s)
            }
            repository.getMissions().onSuccess { list ->
                _state.value = _state.value.copy(missions = list)
            }
            repository.getNotifications().onSuccess { list ->
                _state.value = _state.value.copy(notifications = list)
            }
            repository.getWalletHistory().onSuccess { h ->
                _state.value = _state.value.copy(walletHistory = h)
            }
            repository.getLevelInfo().onSuccess { li ->
                _state.value = _state.value.copy(levelInfo = li)
            }
            _state.value = _state.value.copy(loading = false)
        }
    }

    fun loadCosmetics() {
        viewModelScope.launch {
            repository.getMyCosmetics().onSuccess { c ->
                _cosmetics.value = c
            }
        }
    }

    fun equipCosmetic(cosmeticCode: String) {
        viewModelScope.launch {
            _equippingCode.value = cosmeticCode
            repository.equipCosmetic(cosmeticCode).onSuccess { res ->
                if (res.ok) {
                    _message.value = "Marco equipado ✓"
                    // Refresca el estado: equipado actualiza el nivel también.
                    loadCosmetics()
                } else {
                    _message.value = res.reason ?: "No se pudo equipar"
                }
                _equippingCode.value = null
            }.onFailure { e ->
                _message.value = e.message ?: "Error al equipar"
                _equippingCode.value = null
            }
        }
    }

    fun buyProduct(productCode: String) {
        viewModelScope.launch {
            _buyingCode.value = productCode
            val result = repository.buyProduct(productCode)
            result.onSuccess { buy ->
                if (buy.ok) {
                    _state.value = _state.value.copy(wallet = _state.value.wallet.copy(coins = buy.balance ?: _state.value.wallet.coins))
                    loadEntitlements()
                    // Refrescar el manager global para que los gates se actualicen ya.
                    com.example.premium.domain.PremiumManager.refreshWallet()
                    com.example.premium.domain.PremiumManager.refreshEntitlements()
                    com.example.premium.domain.PremiumEventBus.publish(
                        com.example.premium.domain.PremiumEvent.PurchaseCompleted(
                            productCode = productCode,
                            featureKey = buy.featureKey ?: "",
                            productName = _state.value.products.firstOrNull { it.code == productCode }?.name ?: productCode,
                            emoji = _state.value.products.firstOrNull { it.code == productCode }?.emoji ?: "💎",
                            balance = buy.balance
                        )
                    )
                    _message.value = "✅ ${buy.featureKey ?: "Premium"} activado hasta ${buy.expiresAt?.take(10) ?: ""}"
                } else {
                    _message.value = when (buy.reason) {
                        "insufficient_funds" -> "Saldo insuficiente 💸"
                        "unknown_product" -> "Producto no válido"
                        else -> "No se pudo completar la compra"
                    }
                }
            }
            result.onFailure { _message.value = "Error: ${it.message}" }
            _buyingCode.value = null
        }
    }

    fun loadEntitlements() {
        viewModelScope.launch {
            repository.getMyEntitlements().onSuccess { list ->
                _state.value = _state.value.copy(entitlements = list)
            }
        }
    }

    fun claimDaily() {
        viewModelScope.launch {
            _claimingReward.value = true
            repository.claimDailyReward()
                .onSuccess { r ->
                    if (r.ok) {
                        _state.value = _state.value.copy(dailyReward = _state.value.dailyReward.copy(streak = r.streak ?: 0, claimedToday = true))
                        repository.getWalletBalance().onSuccess { b -> _state.value = _state.value.copy(wallet = b) }
                        com.example.premium.domain.PremiumEventBus.publish(
                            com.example.premium.domain.PremiumEvent.RewardClaimed(
                                amount = r.amount ?: 0,
                                day = r.day ?: 0,
                                streak = r.streak ?: 0,
                                currency = r.currency ?: "coins"
                            )
                        )
                        _message.value = "🎁 Recibiste ${r.amount} ${r.currency} (día ${r.day}, racha ${r.streak})"
                    } else {
                        _message.value = r.reason?.let {
                            if (it == "already_claimed_today") "Ya reclamaste hoy 🎁" else it
                        } ?: "Ya reclamaste hoy 🎁"
                    }
                }
                .onFailure { _message.value = it.message ?: "Error" }
            _claimingReward.value = false
        }
    }

    fun exchangeDiamonds() {
        viewModelScope.launch {
            val d = _state.value.wallet.diamonds
            if (d <= 0) {
                _message.value = "No tienes diamantes 💎"
                return@launch
            }
            repository.exchangeDiamonds(d)
                .onSuccess { ex ->
                    if (ex.ok) {
                        _state.value = _state.value.copy(
                            wallet = _state.value.wallet.copy(
                                coins = ex.coins ?: _state.value.wallet.coins,
                                diamonds = 0
                            )
                        )
                        _message.value = "💎→🪙 Intercambiaste tus diamantes"
                    } else {
                        _message.value = "No se pudo intercambiar"
                    }
                }
                .onFailure { _message.value = it.message ?: "Error" }
        }
    }

    fun claimMissions() {
        viewModelScope.launch {
            repository.claimMissionRewards()
                .onSuccess { r ->
                    if (r.ok && r.claimed.isNotEmpty()) {
                        val total = r.claimed.sumOf { it.reward }
                        repository.getWalletBalance().onSuccess { b -> _state.value = _state.value.copy(wallet = b) }
                        loadAll()
                        r.claimed.forEach { c ->
                            com.example.premium.domain.PremiumEventBus.publish(
                                com.example.premium.domain.PremiumEvent.CoinsEarned(
                                    amount = c.reward,
                                    source = "mission",
                                    note = c.title
                                )
                            )
                        }
                        _message.value = "🎯 Reclamaste ${r.claimed.size} ${if (r.claimed.size == 1) "misión" else "misiones"} (+$total 🪙)"
                    } else {
                        _message.value = "No hay misiones completadas para reclamar"
                    }
                }
                .onFailure { _message.value = it.message ?: "Error" }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationRead(id)
            _state.value = _state.value.copy(
                notifications = _state.value.notifications.map { if (it.id == id) it.copy(isRead = true) else it }
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

data class PremiumUiState(
    val loading: Boolean = true,
    val wallet: WalletBalance = WalletBalance(),
    val entitlements: List<Entitlement> = emptyList(),
    val products: List<PremiumProduct> = emptyList(),
    val promotions: List<PremiumPromotion> = emptyList(),
    val events: List<PremiumEvent> = emptyList(),
    val dailyReward: DailyRewardStatus = DailyRewardStatus(),
    val missions: List<Mission> = emptyList(),
    val notifications: List<PremiumNotification> = emptyList(),
    val walletHistory: WalletHistoryResponse = WalletHistoryResponse(),
    val levelInfo: LevelInfo = LevelInfo()
)

