package com.tapflow.core.billing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.TrialStatus
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PromoAndDualSkuPricingTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private class FakeHmacSigner(
    secret: String = "tapflow-unit-test-secret",
  ) : HmacSigner {
    private val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")

    override fun signBase64(payload: String): String {
      val mac = Mac.getInstance("HmacSHA256")
      mac.init(keySpec)
      val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
      return digest.joinToString("") { "%02x".format(it) }
    }

    override fun verifyBase64(payload: String, storedSignatureBase64: String): Boolean =
      MessageDigest.isEqual(
        signBase64(payload).toByteArray(Charsets.UTF_8),
        storedSignatureBase64.toByteArray(Charsets.UTF_8),
      )
  }

  private class FakeRemoteConfigDataSource(
    var nextResult: Result<String>,
  ) : PromoRemoteConfigDataSource {
    var lastForceRefresh: Boolean? = null

    override suspend fun fetchPromoCodesJson(forceRefresh: Boolean): Result<String> {
      lastForceRefresh = forceRefresh
      return nextResult
    }
  }

  private fun createPrefs(name: String): TapFlowPreferencesRepository {
    val store =
      PreferenceDataStoreFactory.create(
        produceFile = { tempFolder.newFile("$name.preferences_pb") },
      )
    return TapFlowPreferencesRepository(store)
  }

  @Test
  fun redeemCode_normalizesValidatesWindowAndDetectsClockRollbackTampering() = runTest {
    val prefs = createPrefs("promo_valid")
    val signer = FakeHmacSigner()
    val remoteJson =
      """
      {
        "codes": [
          {
            "code": "LAUNCH50",
            "startDateUtc": "2026-11-01T00:00:00Z",
            "endDateUtc": "2026-12-01T00:00:00Z",
            "enabled": true,
            "discountWindowDays": 7,
            "sku": "tapflow_pro_lifetime_promo"
          },
          {
            "code": "FUTURE2027",
            "startDateUtc": "2027-01-01T00:00:00Z",
            "endDateUtc": "2027-02-01T00:00:00Z",
            "enabled": true
          },
          {
            "code": "OLD2025",
            "startDateUtc": "2025-01-01T00:00:00Z",
            "endDateUtc": "2025-02-01T00:00:00Z",
            "enabled": true
          },
          {
            "code": "DISABLED99",
            "startDateUtc": "2026-01-01T00:00:00Z",
            "endDateUtc": "2027-01-01T00:00:00Z",
            "enabled": false
          }
        ]
      }
      """.trimIndent()
    val dataSource = FakeRemoteConfigDataSource(Result.success(remoteJson))
    val manager = PromoRedeemManager(prefs, dataSource, signer)

    // November 17, 2026 UTC
    val nowMs = 1_794_902_400_000L

    assertEquals(
      PromoRedeemResult.Error("Promo code not active yet"),
      manager.redeemCode("future2027", nowMs),
    )
    assertTrue(dataSource.lastForceRefresh == true)

    assertEquals(
      PromoRedeemResult.Error("Expired promo code"),
      manager.redeemCode("old2025", nowMs),
    )
    assertEquals(
      PromoRedeemResult.Error("Invalid promo code"),
      manager.redeemCode("DISABLED99", nowMs),
    )
    assertEquals(
      PromoRedeemResult.Error("Invalid promo code"),
      manager.redeemCode("NONEXISTENT", nowMs),
    )

    // Valid code with leading/trailing spaces & lowercase
    val success = manager.redeemCode("  launch50 ", nowMs)
    assertTrue(success is PromoRedeemResult.Success)
    success as PromoRedeemResult.Success
    assertEquals("LAUNCH50", success.code)
    assertEquals(EntitlementValidator.SKU_PRO_LIFETIME_PROMO, success.sku)
    assertEquals(7, success.discountWindowDays)

    // 2 days later -> still active, 5 days remaining, monotonic clock advances cleanly
    val twoDaysLaterMs = nowMs + (2L * 86_400_000L)
    val evalActive = manager.evaluateAndAdvanceClock(twoDaysLaterMs)
    assertEquals(PromoStatus.ACTIVE, evalActive.status)
    assertEquals(5, evalActive.remainingDays)
    assertEquals("LAUNCH50", evalActive.activePromoCode)

    // Clock rollback (> 60s tolerance) -> invalidated as EXPIRED_TAMPERED
    val rolledBackMs = twoDaysLaterMs - 120_000L
    val evalTampered = manager.evaluateAndAdvanceClock(rolledBackMs)
    assertEquals(PromoStatus.EXPIRED_TAMPERED, evalTampered.status)
    assertFalse(evalTampered.isActive)
  }

  @Test
  fun redeemCode_returnsOfflineErrorOnFetchFailure_andDetectsHmacTampering() = runTest {
    val prefs = createPrefs("promo_offline")
    val signer = FakeHmacSigner()
    val dataSource = FakeRemoteConfigDataSource(Result.failure(IllegalStateException("offline")))
    val manager = PromoRedeemManager(prefs, dataSource, signer)

    val result = manager.redeemCode("LAUNCH50", 1_794_902_400_000L)
    assertEquals(
      PromoRedeemResult.Error("Internet connection required to verify promo code"),
      result,
    )

    // Write forged HMAC signature into DataStore
    prefs.savePromoSnapshot(
      activePromoCode = "LAUNCH50",
      activePromoSku = EntitlementValidator.SKU_PRO_LIFETIME_PROMO,
      promoExpiresAtUtcMs = 1_795_507_200_000L,
      promoMaxObservedClockMs = 1_794_902_400_000L,
      hmacSignatureBase64 = "forged_signature",
    )
    val eval = manager.evaluateAndAdvanceClock(1_794_902_400_000L)
    assertEquals(PromoStatus.EXPIRED_TAMPERED, eval.status)
  }

  @Test
  fun pricingEligibilityEngine_selectsPromoVsRegularSkuAccurately() {
    val regular = ProductOfferInfo(EntitlementValidator.SKU_PRO_LIFETIME, "$14.99", 14_990_000L, "USD")
    val promo = ProductOfferInfo(EntitlementValidator.SKU_PRO_LIFETIME_PROMO, "$7.49", 7_490_000L, "USD")
    val billing = BillingSnapshot(regularOffer = regular, promoOffer = promo)

    val noPromo = PromoEvaluation(status = PromoStatus.NONE)
    val activePromo =
      PromoEvaluation(
        status = PromoStatus.ACTIVE,
        activePromoCode = "LAUNCH50",
        activePromoSku = EntitlementValidator.SKU_PRO_LIFETIME_PROMO,
        remainingDays = 6,
        remainingHours = 12,
      )

    // Helper using same pure logic as EntitlementRepository.resolvePricingState
    fun resolve(entitlement: EntitlementState, promoEval: PromoEvaluation, snap: BillingSnapshot): PaywallPricingState {
      val eligible = EntitlementValidator.isDiscountEligible(entitlement, promoEval.isActive)
      val reason =
        when {
          promoEval.isActive -> DiscountReason.PROMO_CODE_ACTIVE
          entitlement is EntitlementState.ProTrial -> DiscountReason.TRIAL_ACTIVE
          entitlement is EntitlementState.Free && entitlement.trialStatus == TrialStatus.NOT_STARTED ->
            DiscountReason.TRIAL_NOT_STARTED
          else -> DiscountReason.NONE
        }
      val showPromo = eligible && snap.promoOffer != null
      val sku =
        if (showPromo) {
          promoEval.activePromoSku?.takeIf { promoEval.isActive } ?: EntitlementValidator.SKU_PRO_LIFETIME_PROMO
        } else {
          EntitlementValidator.SKU_PRO_LIFETIME
        }
      val pct =
        if (showPromo && snap.regularOffer != null && snap.promoOffer != null) {
          EntitlementValidator.computeDiscountPercentOff(snap.regularOffer.priceAmountMicros, snap.promoOffer.priceAmountMicros)
        } else {
          null
        }
      return PaywallPricingState(
        regularProduct = snap.regularOffer,
        promoProduct = snap.promoOffer,
        isDiscountEligible = eligible,
        discountReason = reason,
        activeSku = sku,
        discountPercentOff = pct,
        activePromoCode = promoEval.activePromoCode.takeIf { promoEval.isActive },
        promoRemainingDays = if (promoEval.isActive) promoEval.remainingDays else 0,
        promoRemainingHours = if (promoEval.isActive) promoEval.remainingHours else 0,
      )
    }

    // 1. Trial NOT_STARTED -> eligible for promo
    val case1 = resolve(EntitlementState.Free(TrialStatus.NOT_STARTED, 7), noPromo, billing)
    assertTrue(case1.showPromoPricing)
    assertEquals(EntitlementValidator.SKU_PRO_LIFETIME_PROMO, case1.activeSku)
    assertEquals("$7.49", case1.effectiveFormattedPrice)
    assertEquals(50, case1.discountPercentOff)
    assertEquals(DiscountReason.TRIAL_NOT_STARTED, case1.discountReason)

    // 2. Trial EXPIRED & no promo -> pays regular price
    val case2 = resolve(EntitlementState.Free(TrialStatus.EXPIRED, 0), noPromo, billing)
    assertFalse(case2.isDiscountEligible)
    assertFalse(case2.showPromoPricing)
    assertEquals(EntitlementValidator.SKU_PRO_LIFETIME, case2.activeSku)
    assertEquals("$14.99", case2.effectiveFormattedPrice)
    assertNull(case2.discountPercentOff)

    // 3. Trial EXPIRED + active redeemed promo code -> unlocked promo price
    val case3 = resolve(EntitlementState.Free(TrialStatus.EXPIRED, 0), activePromo, billing)
    assertTrue(case3.isDiscountEligible)
    assertTrue(case3.showPromoPricing)
    assertEquals(EntitlementValidator.SKU_PRO_LIFETIME_PROMO, case3.activeSku)
    assertEquals(DiscountReason.PROMO_CODE_ACTIVE, case3.discountReason)
    assertEquals("LAUNCH50", case3.activePromoCode)
    assertEquals(6, case3.promoRemainingDays)
    assertEquals(12, case3.promoRemainingHours)
  }
}
