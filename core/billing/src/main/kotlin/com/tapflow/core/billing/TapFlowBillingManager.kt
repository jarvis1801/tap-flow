package com.tapflow.core.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Immutable
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.tapflow.core.model.entitlement.EntitlementValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class BillingSnapshot(
  val isLifetimeOwned: Boolean = false,
  val isPendingPurchase: Boolean = false,
  val purchaseTokenHash: String? = null,
  val purchasedAtUtcMs: Long = 0L,
  val formattedPrice: String? = null,
  val offerToken: String? = null,
  val statusMessage: String? = null,
)

enum class BillingRestoreResult {
  RESTORED_LIFETIME,
  PENDING_SETTLEMENT,
  NO_PURCHASE_FOUND,
  SERVICE_UNAVAILABLE,
}

@Singleton
class TapFlowBillingManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val connectionMutex = Mutex()

  private val _billingSnapshot = MutableStateFlow(BillingSnapshot())
  val billingSnapshot: StateFlow<BillingSnapshot> = _billingSnapshot.asStateFlow()

  @Volatile
  private var cachedProductDetails: ProductDetails? = null

  private val purchasesUpdatedListener =
    PurchasesUpdatedListener { billingResult, purchases ->
      when (billingResult.responseCode) {
        BillingClient.BillingResponseCode.OK -> {
          scope.launch {
            processPurchases(purchases.orEmpty())
          }
        }
        BillingClient.BillingResponseCode.USER_CANCELED -> {
          _billingSnapshot.update { it.copy(statusMessage = "Purchase canceled.") }
        }
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
          scope.launch { syncBillingState() }
        }
        else -> {
          _billingSnapshot.update {
            it.copy(
              statusMessage = billingResult.debugMessage.ifBlank {
                "Play Billing error (${billingResult.responseCode})."
              },
            )
          }
        }
      }
    }

  private val billingClient: BillingClient =
    BillingClient.newBuilder(context)
      .setListener(purchasesUpdatedListener)
      .enablePendingPurchases(
        PendingPurchasesParams.newBuilder()
          .enableOneTimeProducts()
          .build(),
      )
      .enableAutoServiceReconnection()
      .build()

  suspend fun syncBillingState() {
    val connected = ensureConnected()
    if (!connected) return
    queryLifetimeProductDetails()
    queryAndProcessActivePurchases()
  }

  suspend fun restorePurchases(): BillingRestoreResult {
    val connected = ensureConnected()
    if (!connected) {
      _billingSnapshot.update {
        it.copy(statusMessage = "Google Play Billing service unavailable.")
      }
      return BillingRestoreResult.SERVICE_UNAVAILABLE
    }
    queryLifetimeProductDetails()
    queryAndProcessActivePurchases()

    val current = _billingSnapshot.value
    return when {
      current.isLifetimeOwned -> BillingRestoreResult.RESTORED_LIFETIME
      current.isPendingPurchase -> BillingRestoreResult.PENDING_SETTLEMENT
      else -> BillingRestoreResult.NO_PURCHASE_FOUND
    }
  }

  fun launchLifetimePurchase(activity: Activity): BillingResult? {
    val details = cachedProductDetails
    if (details == null) {
      scope.launch { syncBillingState() }
      _billingSnapshot.update {
        it.copy(statusMessage = "Loading Play Store product details… please try again.")
      }
      return null
    }

    val offerToken = resolveOfferToken(details)
    val productParamsBuilder =
      BillingFlowParams.ProductDetailsParams.newBuilder()
        .setProductDetails(details)
    if (!offerToken.isNullOrBlank()) {
      productParamsBuilder.setOfferToken(offerToken)
    }

    val flowParams =
      BillingFlowParams.newBuilder()
        .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
        .build()

    return billingClient.launchBillingFlow(activity, flowParams)
  }

  private suspend fun ensureConnected(): Boolean =
    connectionMutex.withLock {
      if (billingClient.isReady) return@withLock true
      suspendCancellableCoroutine { continuation ->
        billingClient.startConnection(
          object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
              if (continuation.isActive) {
                continuation.resume(
                  billingResult.responseCode == BillingClient.BillingResponseCode.OK,
                )
              }
            }

            override fun onBillingServiceDisconnected() {
              if (continuation.isActive) {
                continuation.resume(false)
              }
            }
          },
        )
      }
    }

  private suspend fun queryLifetimeProductDetails() {
    val productList =
      listOf(
        QueryProductDetailsParams.Product.newBuilder()
          .setProductId(EntitlementValidator.SKU_PRO_LIFETIME)
          .setProductType(BillingClient.ProductType.INAPP)
          .build(),
      )
    val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
    val result = billingClient.queryProductDetails(params)
    if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
      val details = result.productDetailsList?.firstOrNull()
      cachedProductDetails = details
      if (details != null) {
        val price = resolveFormattedPrice(details)
        val token = resolveOfferToken(details)
        _billingSnapshot.update {
          it.copy(
            formattedPrice = price,
            offerToken = token,
          )
        }
      }
    }
  }

  private suspend fun queryAndProcessActivePurchases() {
    val params =
      QueryPurchasesParams.newBuilder()
        .setProductType(BillingClient.ProductType.INAPP)
        .build()
    val purchasesResult = billingClient.queryPurchasesAsync(params)
    if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
      processPurchases(purchasesResult.purchasesList)
    }
  }

  private suspend fun processPurchases(purchases: List<Purchase>) {
    val matching =
      purchases.filter { purchase ->
        purchase.products.contains(EntitlementValidator.SKU_PRO_LIFETIME)
      }

    val purchased =
      matching.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    if (purchased != null) {
      if (!purchased.isAcknowledged) {
        val ackParams =
          AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchased.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(ackParams)
      }
      _billingSnapshot.update {
        it.copy(
          isLifetimeOwned = true,
          isPendingPurchase = false,
          purchaseTokenHash = sha256Hex(purchased.purchaseToken),
          purchasedAtUtcMs = purchased.purchaseTime,
          statusMessage = null,
        )
      }
      return
    }

    val pending =
      matching.firstOrNull { it.purchaseState == Purchase.PurchaseState.PENDING }
    if (pending != null) {
      _billingSnapshot.update {
        it.copy(
          isLifetimeOwned = false,
          isPendingPurchase = true,
          statusMessage = "Payment pending — Pro will unlock once settled.",
        )
      }
      return
    }

    _billingSnapshot.update {
      it.copy(
        isLifetimeOwned = false,
        isPendingPurchase = false,
        purchaseTokenHash = null,
        purchasedAtUtcMs = 0L,
      )
    }
  }

  @Suppress("DEPRECATION")
  private fun resolveFormattedPrice(details: ProductDetails): String? {
    val multiOfferPrice = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
    if (!multiOfferPrice.isNullOrBlank()) return multiOfferPrice
    return details.oneTimePurchaseOfferDetails?.formattedPrice
  }

  @Suppress("DEPRECATION")
  private fun resolveOfferToken(details: ProductDetails): String? {
    val multiOfferToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
    if (!multiOfferToken.isNullOrBlank()) return multiOfferToken
    return details.oneTimePurchaseOfferDetails?.offerToken
  }

  private fun sha256Hex(raw: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
  }
}
