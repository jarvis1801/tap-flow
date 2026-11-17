package com.tapflow.core.billing

import com.tapflow.core.datastore.PromoStorageSnapshot
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.model.entitlement.EntitlementValidator
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class PromoRedeemManager @Inject constructor(
  private val preferencesRepository: TapFlowPreferencesRepository,
  private val remoteConfigDataSource: PromoRemoteConfigDataSource,
  private val hmacSigner: HmacSigner,
) {
  companion object {
    private const val MS_PER_HOUR = 60L * 60L * 1000L
    private const val MS_PER_DAY = 24L * MS_PER_HOUR

    private val promoJson =
      Json {
        ignoreUnknownKeys = true
        isLenient = true
      }
  }

  private val mutex = Mutex()

  val promoEvaluationFlow: Flow<PromoEvaluation> =
    preferencesRepository.promoSnapshotFlow.map { snapshot ->
      evaluateSnapshot(snapshot, System.currentTimeMillis())
    }

  suspend fun refreshConfigInBackground() {
    withContext(Dispatchers.IO) {
      remoteConfigDataSource.fetchPromoCodesJson(forceRefresh = false)
    }
  }

  suspend fun evaluateAndAdvanceClock(
    nowUtcMs: Long = System.currentTimeMillis(),
  ): PromoEvaluation =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val snapshot = preferencesRepository.getPromoSnapshotOnce()
        val evaluation = evaluateSnapshot(snapshot, nowUtcMs)
        if (
          evaluation.status == PromoStatus.ACTIVE &&
          snapshot.activePromoCode != null &&
          snapshot.activePromoSku != null &&
          snapshot.promoExpiresAtUtcMs != null &&
          nowUtcMs > snapshot.promoMaxObservedClockMs
        ) {
          val payload =
            buildHmacPayload(
              code = snapshot.activePromoCode!!,
              sku = snapshot.activePromoSku!!,
              expiresAtUtcMs = snapshot.promoExpiresAtUtcMs!!,
              maxObservedClockMs = nowUtcMs,
            )
          val updatedTag = hmacSigner.signBase64(payload)
          if (updatedTag != null) {
            preferencesRepository.updatePromoMaxObservedClock(
              maxObservedClockMs = nowUtcMs,
              hmacSignatureBase64 = updatedTag,
            )
          }
        }
        evaluation
      }
    }

  suspend fun redeemCode(
    rawCode: String,
    nowUtcMs: Long = System.currentTimeMillis(),
  ): PromoRedeemResult =
    mutex.withLock {
      val normalizedCode = rawCode.trim().uppercase()
      if (normalizedCode.isEmpty()) {
        return@withLock PromoRedeemResult.Error("Please enter a promo code")
      }

      val fetchResult =
        withContext(Dispatchers.IO) {
          remoteConfigDataSource.fetchPromoCodesJson(forceRefresh = true)
        }
      val jsonRaw =
        fetchResult.getOrElse {
          return@withLock PromoRedeemResult.Error(
            "Internet connection required to verify promo code",
          )
        }

      if (jsonRaw.isBlank()) {
        return@withLock PromoRedeemResult.Error("Invalid promo code")
      }

      val catalog =
        runCatching { promoJson.decodeFromString(PromoCodeCatalog.serializer(), jsonRaw) }
          .getOrNull()
          ?: return@withLock PromoRedeemResult.Error("Invalid promo code")

      val entry =
        catalog.codes.firstOrNull { it.code.trim().uppercase() == normalizedCode }
          ?: return@withLock PromoRedeemResult.Error("Invalid promo code")

      if (!entry.enabled) {
        return@withLock PromoRedeemResult.Error("Invalid promo code")
      }

      val startInstant = runCatching { Instant.parse(entry.startDateUtc.trim()) }.getOrNull()
      val endInstant = runCatching { Instant.parse(entry.endDateUtc.trim()) }.getOrNull()
      if (startInstant == null || endInstant == null || endInstant.isBefore(startInstant)) {
        return@withLock PromoRedeemResult.Error("Invalid promo code")
      }

      val nowInstant = Instant.ofEpochMilli(nowUtcMs)
      if (nowInstant.isBefore(startInstant)) {
        return@withLock PromoRedeemResult.Error("Promo code not active yet")
      }
      if (nowInstant.isAfter(endInstant)) {
        return@withLock PromoRedeemResult.Error("Expired promo code")
      }

      val windowDays = entry.discountWindowDays.coerceAtLeast(1)
      val expiresAtUtcMs = nowUtcMs + (windowDays * MS_PER_DAY)
      val targetSku = entry.sku.trim().ifBlank { EntitlementValidator.SKU_PRO_LIFETIME_PROMO }
      val payload =
        buildHmacPayload(
          code = normalizedCode,
          sku = targetSku,
          expiresAtUtcMs = expiresAtUtcMs,
          maxObservedClockMs = nowUtcMs,
        )
      val signature =
        hmacSigner.signBase64(payload)
          ?: return@withLock PromoRedeemResult.Error(
            "Unable to secure promo activation on this device",
          )

      withContext(Dispatchers.IO) {
        preferencesRepository.savePromoSnapshot(
          activePromoCode = normalizedCode,
          activePromoSku = targetSku,
          promoExpiresAtUtcMs = expiresAtUtcMs,
          promoMaxObservedClockMs = nowUtcMs,
          hmacSignatureBase64 = signature,
        )
      }

      PromoRedeemResult.Success(
        code = normalizedCode,
        sku = targetSku,
        discountWindowDays = windowDays,
        expiresAtUtcMs = expiresAtUtcMs,
      )
    }

  internal fun evaluateSnapshot(
    snapshot: PromoStorageSnapshot,
    nowUtcMs: Long,
  ): PromoEvaluation {
    val code = snapshot.activePromoCode
    val sku = snapshot.activePromoSku
    val expiresAt = snapshot.promoExpiresAtUtcMs
    val storedSig = snapshot.hmacSignatureBase64

    if (
      code == null &&
      sku == null &&
      expiresAt == null &&
      storedSig == null &&
      snapshot.promoMaxObservedClockMs == 0L
    ) {
      return PromoEvaluation(status = PromoStatus.NONE)
    }

    if (code.isNullOrBlank() || sku.isNullOrBlank() || expiresAt == null || storedSig.isNullOrBlank()) {
      return tamperedEvaluation(code, sku, expiresAt)
    }

    if (nowUtcMs < snapshot.promoMaxObservedClockMs - EntitlementValidator.CLOCK_ROLLBACK_TOLERANCE_MS) {
      return tamperedEvaluation(code, sku, expiresAt)
    }

    val expectedPayload =
      buildHmacPayload(
        code = code,
        sku = sku,
        expiresAtUtcMs = expiresAt,
        maxObservedClockMs = snapshot.promoMaxObservedClockMs,
      )
    if (!hmacSigner.verifyBase64(expectedPayload, storedSig)) {
      return tamperedEvaluation(code, sku, expiresAt)
    }

    val remainingMs = expiresAt - nowUtcMs
    if (remainingMs <= 0L) {
      return PromoEvaluation(
        status = PromoStatus.EXPIRED,
        activePromoCode = code,
        activePromoSku = sku,
        expiresAtUtcMs = expiresAt,
        remainingDays = 0,
        remainingHours = 0,
      )
    }

    val days = (remainingMs / MS_PER_DAY).toInt()
    val hours = ((remainingMs % MS_PER_DAY) / MS_PER_HOUR).toInt()
    return PromoEvaluation(
      status = PromoStatus.ACTIVE,
      activePromoCode = code,
      activePromoSku = sku,
      expiresAtUtcMs = expiresAt,
      remainingDays = days,
      remainingHours = hours,
    )
  }

  private fun tamperedEvaluation(
    code: String?,
    sku: String?,
    expiresAt: Long?,
  ): PromoEvaluation =
    PromoEvaluation(
      status = PromoStatus.EXPIRED_TAMPERED,
      activePromoCode = code,
      activePromoSku = sku,
      expiresAtUtcMs = expiresAt,
      remainingDays = 0,
      remainingHours = 0,
    )

  private fun buildHmacPayload(
    code: String,
    sku: String,
    expiresAtUtcMs: Long,
    maxObservedClockMs: Long,
  ): String = "promo:$code:$sku:$expiresAtUtcMs:$maxObservedClockMs"
}
