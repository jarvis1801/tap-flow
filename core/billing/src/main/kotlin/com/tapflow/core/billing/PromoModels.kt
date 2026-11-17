package com.tapflow.core.billing

import androidx.compose.runtime.Immutable
import com.tapflow.core.model.entitlement.EntitlementValidator
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class RemotePromoCode(
  val code: String,
  val startDateUtc: String,
  val endDateUtc: String,
  val enabled: Boolean = true,
  val discountWindowDays: Int = 7,
  val sku: String = EntitlementValidator.SKU_PRO_LIFETIME_PROMO,
)

@Immutable
@Serializable
data class PromoCodeCatalog(
  val codes: List<RemotePromoCode> = emptyList(),
)

enum class PromoStatus {
  NONE,
  ACTIVE,
  EXPIRED,
  EXPIRED_TAMPERED,
}

@Immutable
data class PromoEvaluation(
  val status: PromoStatus = PromoStatus.NONE,
  val activePromoCode: String? = null,
  val activePromoSku: String? = null,
  val expiresAtUtcMs: Long? = null,
  val remainingDays: Int = 0,
  val remainingHours: Int = 0,
) {
  val isActive: Boolean
    get() = status == PromoStatus.ACTIVE
}

@Immutable
sealed interface PromoRedeemResult {
  @Immutable
  data class Success(
    val code: String,
    val sku: String,
    val discountWindowDays: Int,
    val expiresAtUtcMs: Long,
  ) : PromoRedeemResult

  @Immutable
  data class Error(val message: String) : PromoRedeemResult
}

enum class DiscountReason {
  NONE,
  TRIAL_NOT_STARTED,
  TRIAL_ACTIVE,
  PROMO_CODE_ACTIVE,
}

@Immutable
data class ProductOfferInfo(
  val productId: String,
  val formattedPrice: String,
  val priceAmountMicros: Long,
  val priceCurrencyCode: String,
  val offerToken: String? = null,
)

@Immutable
data class PaywallPricingState(
  val regularProduct: ProductOfferInfo? = null,
  val promoProduct: ProductOfferInfo? = null,
  val isDiscountEligible: Boolean = true,
  val discountReason: DiscountReason = DiscountReason.TRIAL_NOT_STARTED,
  val activeSku: String = EntitlementValidator.SKU_PRO_LIFETIME,
  val discountPercentOff: Int? = null,
  val activePromoCode: String? = null,
  val promoRemainingDays: Int = 0,
  val promoRemainingHours: Int = 0,
) {
  val showPromoPricing: Boolean
    get() = isDiscountEligible && promoProduct != null

  val effectiveFormattedPrice: String?
    get() = if (showPromoPricing) promoProduct?.formattedPrice else regularProduct?.formattedPrice
}
