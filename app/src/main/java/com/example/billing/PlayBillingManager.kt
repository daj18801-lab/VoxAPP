package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Defines consumable Google Play Store in-app product IDs for VoxStage point packs ($1 per point).
 * Configure these exact one-time consumable Product IDs in Google Play Console -> Monetize -> In-app products.
 */
data class PointsProductSku(
    val productId: String,
    val points: Long,
    val fallbackPriceLabel: String
)

enum class PlayBillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE
}

sealed interface PlayBillingEvent {
    data class PurchaseVerifiedAndConsumed(
        val points: Long,
        val productId: String,
        val orderId: String
    ) : PlayBillingEvent

    data class ProSubscriptionVerified(
        val productId: String,
        val orderId: String
    ) : PlayBillingEvent

    data class Message(val text: String) : PlayBillingEvent
}

/**
 * Real Google Play Billing Library client for consumable point purchases.
 * Connects to Google Play Store, queries localized ProductDetails, launches the official
 * Google Play purchase sheet, consumes verified purchases, and emits fulfillment events
 * so Firestore credits points only after Play Store confirms the purchase.
 */
class PlayBillingManager(
    context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "PlayBillingManager"

        const val PRO_SUBSCRIPTION_PRODUCT_ID = "voxstage_pro_monthly"
        const val PRO_FALLBACK_PRICE_LABEL = "$9.99/mo"
        const val VIP_PRO_SUBSCRIPTION_PRODUCT_ID = "voxstage_vip_pro_monthly"
        const val VIP_PRO_FALLBACK_PRICE_LABEL = "$29.99/mo"
        const val BUSINESS_SUBSCRIPTION_PRODUCT_ID = "voxstage_business_monthly"
        const val BUSINESS_FALLBACK_PRICE_LABEL = "$99.99/mo"

        val ALL_SUBSCRIPTION_PRODUCT_IDS: Set<String> = setOf(
            PRO_SUBSCRIPTION_PRODUCT_ID,
            VIP_PRO_SUBSCRIPTION_PRODUCT_ID,
            BUSINESS_SUBSCRIPTION_PRODUCT_ID
        )

        val POINTS_CATALOG: List<PointsProductSku> = listOf(
            PointsProductSku(productId = "voxstage_points_2", points = 2L, fallbackPriceLabel = "$2.00"),
            PointsProductSku(productId = "voxstage_points_5", points = 5L, fallbackPriceLabel = "$5.00"),
            PointsProductSku(productId = "voxstage_points_10", points = 10L, fallbackPriceLabel = "$10.00"),
            PointsProductSku(productId = "voxstage_points_20", points = 20L, fallbackPriceLabel = "$20.00"),
            PointsProductSku(productId = "voxstage_points_50", points = 50L, fallbackPriceLabel = "$50.00")
        )

        fun skuForPoints(points: Long): PointsProductSku? =
            POINTS_CATALOG.firstOrNull { it.points == points }

        fun pointsForProductId(productId: String): Long? =
            POINTS_CATALOG.firstOrNull { it.productId == productId }?.points
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _connectionState = MutableStateFlow(PlayBillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PlayBillingConnectionState> = _connectionState.asStateFlow()

    private val _productDetailsByPoints = MutableStateFlow<Map<Long, ProductDetails>>(emptyMap())
    val productDetailsByPoints: StateFlow<Map<Long, ProductDetails>> = _productDetailsByPoints.asStateFlow()

    private val _proSubscriptionDetails = MutableStateFlow<ProductDetails?>(null)
    val proSubscriptionDetails: StateFlow<ProductDetails?> = _proSubscriptionDetails.asStateFlow()

    private val _subscriptionDetailsByProductId = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val subscriptionDetailsByProductId: StateFlow<Map<String, ProductDetails>> = _subscriptionDetailsByProductId.asStateFlow()

    private val _isPurchaseInFlight = MutableStateFlow(false)
    val isPurchaseInFlight: StateFlow<Boolean> = _isPurchaseInFlight.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _events = MutableSharedFlow<PlayBillingEvent>(extraBufferCapacity = 12)
    val events: SharedFlow<PlayBillingEvent> = _events.asSharedFlow()

    private val processedTokens = mutableSetOf<String>()

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            _connectionState.value = PlayBillingConnectionState.CONNECTED
            scope.launch {
                queryCatalogProducts()
                reconcilePendingPurchases()
            }
            return
        }
        if (_connectionState.value == PlayBillingConnectionState.CONNECTING) return
        _connectionState.value = PlayBillingConnectionState.CONNECTING

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        _connectionState.value = PlayBillingConnectionState.CONNECTED
                        _statusBanner.value = null
                        scope.launch {
                            queryCatalogProducts()
                            reconcilePendingPurchases()
                        }
                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                        _connectionState.value = PlayBillingConnectionState.UNAVAILABLE
                        _statusBanner.value = "Google Play Billing is unavailable on this device. Install or sign into Google Play Store."
                    }
                    else -> {
                        _connectionState.value = PlayBillingConnectionState.DISCONNECTED
                        _statusBanner.value = "Google Play Store connection paused (${billingResult.debugMessage.ifBlank { "code ${billingResult.responseCode}" }})."
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _connectionState.value = PlayBillingConnectionState.DISCONNECTED
            }
        })
    }

    private suspend fun queryCatalogProducts() {
        if (!billingClient.isReady) return
        val productList = POINTS_CATALOG.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val detailsMap = buildMap<Long, ProductDetails> {
                result.productDetailsList.orEmpty().forEach { details ->
                    val pts = pointsForProductId(details.productId)
                    if (pts != null) put(pts, details)
                }
            }
            _productDetailsByPoints.value = detailsMap
            if (detailsMap.isEmpty()) {
                _statusBanner.value = "Connected to Google Play. Create in-app product IDs (voxstage_points_2..50 & voxstage_pro_monthly) in Google Play Console to activate checkout."
            } else {
                _statusBanner.value = null
            }
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.responseCode} ${result.billingResult.debugMessage}")
        }

        // Query VOX Pro ($9.99/mo), VIP Pro ($29.99/mo), and Business Account ($99.99/mo) Subscriptions
        val subsProducts = ALL_SUBSCRIPTION_PRODUCT_IDS.map { skuId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(skuId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subsProducts)
            .build()
        val subsResult = billingClient.queryProductDetails(subsParams)
        if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val subMap = subsResult.productDetailsList.orEmpty().associateBy { it.productId }
            _subscriptionDetailsByProductId.value = subMap
            _proSubscriptionDetails.value = subMap[PRO_SUBSCRIPTION_PRODUCT_ID]
        }
    }

    private suspend fun reconcilePendingPurchases() {
        if (!billingClient.isReady) return
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val inAppResult = billingClient.queryPurchasesAsync(inAppParams)
        if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            inAppResult.purchasesList.forEach { purchase ->
                handlePurchase(purchase)
            }
        }
        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val subsResult = billingClient.queryPurchasesAsync(subsParams)
        if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            subsResult.purchasesList.forEach { purchase ->
                handlePurchase(purchase)
            }
        }
    }

    fun formattedProPrice(): String = formattedPriceForTier(com.example.data.ProTier.PRO)

    fun formattedPriceForTier(tier: com.example.data.ProTier): String {
        val productId = tier.playProductId
        if (productId.isBlank()) return tier.priceLabel
        val offer = _subscriptionDetailsByProductId.value[productId]
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
        return offer?.formattedPrice?.let { "$it/mo" } ?: tier.priceLabel
    }

    fun launchProSubscription(activity: Activity) =
        launchSubscription(activity, com.example.data.ProTier.PRO)

    fun launchSubscription(activity: Activity, tier: com.example.data.ProTier) {
        val productId = tier.playProductId
        if (productId.isBlank()) return
        if (!billingClient.isReady) {
            startConnection()
            _events.tryEmit(
                PlayBillingEvent.Message(
                    "Connecting to Google Play Store… Please make sure Google Play is signed in and try again."
                )
            )
            return
        }
        val details = _subscriptionDetailsByProductId.value[productId]
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (details == null || offerToken.isNullOrBlank()) {
            scope.launch { queryCatalogProducts() }
            val extraHint = if (tier == com.example.data.ProTier.PRO) {
                " You can also activate VOX Pro with 10 points below."
            } else {
                ""
            }
            _events.tryEmit(
                PlayBillingEvent.Message(
                    "Google Play subscription '$productId' (${tier.priceLabel}) is not active in Play Console yet.$extraHint"
                )
            )
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        _isPurchaseInFlight.value = true
        val launchResult = billingClient.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _isPurchaseInFlight.value = false
            _events.tryEmit(
                PlayBillingEvent.Message(
                    "Could not open Google Play subscription checkout: ${launchResult.debugMessage.ifBlank { "Error ${launchResult.responseCode}" }}"
                )
            )
        }
    }

    fun formattedPriceFor(points: Long): String {
        val details = _productDetailsByPoints.value[points]
        val playPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice
        return playPrice ?: skuForPoints(points)?.fallbackPriceLabel ?: "\$$points.00"
    }

    /**
     * Launches the official Google Play Store in-app purchase sheet for the requested point pack.
     */
    fun launchPointsPurchase(activity: Activity, points: Long) {
        val sku = skuForPoints(points)
        if (sku == null) {
            _events.tryEmit(PlayBillingEvent.Message("Unsupported points package."))
            return
        }
        if (!billingClient.isReady) {
            startConnection()
            _events.tryEmit(
                PlayBillingEvent.Message(
                    "Connecting to Google Play Store… Please make sure Google Play is signed in and try again."
                )
            )
            return
        }
        val details = _productDetailsByPoints.value[points]
        if (details == null) {
            scope.launch { queryCatalogProducts() }
            _events.tryEmit(
                PlayBillingEvent.Message(
                    "Google Play product '${sku.productId}' is not active in Play Console yet. Activate it in Play Console -> In-app products to purchase via Google Play."
                )
            )
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        _isPurchaseInFlight.value = true
        val launchResult = billingClient.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _isPurchaseInFlight.value = false
            _events.tryEmit(
                PlayBillingEvent.Message(
                    "Could not open Google Play checkout: ${launchResult.debugMessage.ifBlank { "Error ${launchResult.responseCode}" }}"
                )
            )
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                if (list.isEmpty()) {
                    _isPurchaseInFlight.value = false
                    return
                }
                scope.launch {
                    list.forEach { purchase -> handlePurchase(purchase) }
                    _isPurchaseInFlight.value = false
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _isPurchaseInFlight.value = false
                _events.tryEmit(PlayBillingEvent.Message("Google Play purchase canceled."))
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch {
                    reconcilePendingPurchases()
                    _isPurchaseInFlight.value = false
                }
            }
            else -> {
                _isPurchaseInFlight.value = false
                _events.tryEmit(
                    PlayBillingEvent.Message(
                        "Google Play purchase failed: ${billingResult.debugMessage.ifBlank { "Code ${billingResult.responseCode}" }}"
                    )
                )
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                _events.tryEmit(
                    PlayBillingEvent.Message(
                        "Google Play payment is pending confirmation. Points will be credited automatically once cleared."
                    )
                )
                return
            }
            Purchase.PurchaseState.PURCHASED -> {
                val token = purchase.purchaseToken
                if (token.isBlank() || !processedTokens.add(token)) return

                val productId = purchase.products.firstOrNull().orEmpty()
                if (productId in ALL_SUBSCRIPTION_PRODUCT_IDS) {
                    val orderId = purchase.orderId.orEmpty().ifBlank { token.take(36) }
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(token)
                            .build()
                        val ackResult = billingClient.acknowledgePurchase(ackParams)
                        if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            processedTokens.remove(token)
                            _events.tryEmit(
                                PlayBillingEvent.Message(
                                    "Could not confirm subscription (${ackResult.debugMessage}). Please retry."
                                )
                            )
                            return
                        }
                    }
                    _events.tryEmit(
                        PlayBillingEvent.ProSubscriptionVerified(
                            productId = productId,
                            orderId = orderId
                        )
                    )
                    return
                }

                val points = pointsForProductId(productId)
                if (points == null) {
                    Log.w(TAG, "Unknown productId purchased: $productId")
                    return
                }

                // Consume the consumable in-app product so user can buy this pack again later
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(token)
                    .build()
                val consumeResult = billingClient.consumePurchase(consumeParams)
                if (consumeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val orderId = purchase.orderId.orEmpty().ifBlank { token.take(36) }
                    _events.tryEmit(
                        PlayBillingEvent.PurchaseVerifiedAndConsumed(
                            points = points,
                            productId = productId,
                            orderId = orderId
                        )
                    )
                } else {
                    processedTokens.remove(token)
                    _events.tryEmit(
                        PlayBillingEvent.Message(
                            "Google Play could not finalize your purchase (${consumeResult.billingResult.debugMessage}). Please retry."
                        )
                    )
                }
            }
            else -> Unit
        }
    }

    fun endConnection() {
        scope.cancel()
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
