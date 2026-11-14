package com.tapflow.core.billing

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Immutable
import com.tapflow.core.datastore.TapFlowPreferencesRepository
import com.tapflow.core.datastore.TrialStorageSnapshot
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.TrialStatus
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
data class TrialEvaluation(
  val status: TrialStatus,
  val startedAtUtcMs: Long?,
  val expiresAtUtcMs: Long?,
  val remainingDays: Int,
  val remainingHours: Int,
)

@Singleton
class TrialGuardManager @Inject constructor(
  private val preferencesRepository: TapFlowPreferencesRepository,
) {
  companion object {
    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val HMAC_KEY_ALIAS = "tapflow_trial_hmac_key"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val MS_PER_HOUR = 60L * 60L * 1000L
    private const val MS_PER_DAY = 24L * MS_PER_HOUR
  }

  private val mutex = Mutex()

  val trialEvaluationFlow: Flow<TrialEvaluation> =
    preferencesRepository.trialSnapshotFlow.map { snapshot ->
      evaluateSnapshot(snapshot, System.currentTimeMillis())
    }

  suspend fun evaluateAndAdvanceClock(
    nowUtcMs: Long = System.currentTimeMillis(),
  ): TrialEvaluation =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val snapshot = preferencesRepository.getTrialSnapshotOnce()
        val evaluation = evaluateSnapshot(snapshot, nowUtcMs)
        if (
          evaluation.status == TrialStatus.ACTIVE &&
          snapshot.trialStartedAtUtcMs != null &&
          nowUtcMs > snapshot.maxObservedClockMs
        ) {
          val updatedTag = computeHmacBase64(snapshot.trialStartedAtUtcMs!!, nowUtcMs)
          if (updatedTag != null) {
            preferencesRepository.updateTrialMaxObservedClock(
              maxObservedClockMs = nowUtcMs,
              hmacSignatureBase64 = updatedTag,
            )
          }
        }
        evaluation
      }
    }

  suspend fun activateSevenDayTrial(
    nowUtcMs: Long = System.currentTimeMillis(),
  ): TrialEvaluation =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val snapshot = preferencesRepository.getTrialSnapshotOnce()
        val currentEvaluation = evaluateSnapshot(snapshot, nowUtcMs)
        if (currentEvaluation.status != TrialStatus.NOT_STARTED) {
          return@withContext currentEvaluation
        }

        val signature =
          computeHmacBase64(nowUtcMs, nowUtcMs)
            ?: return@withContext TrialEvaluation(
              status = TrialStatus.EXPIRED_TAMPERED,
              startedAtUtcMs = null,
              expiresAtUtcMs = null,
              remainingDays = 0,
              remainingHours = 0,
            )

        preferencesRepository.saveTrialSnapshot(
          startedAtUtcMs = nowUtcMs,
          maxObservedClockMs = nowUtcMs,
          hmacSignatureBase64 = signature,
        )

        val expiresAt = nowUtcMs + EntitlementValidator.TRIAL_DURATION_MS
        TrialEvaluation(
          status = TrialStatus.ACTIVE,
          startedAtUtcMs = nowUtcMs,
          expiresAtUtcMs = expiresAt,
          remainingDays = 7,
          remainingHours = 0,
        )
      }
    }

  internal fun evaluateSnapshot(
    snapshot: TrialStorageSnapshot,
    nowUtcMs: Long,
  ): TrialEvaluation {
    val startedAt = snapshot.trialStartedAtUtcMs
    val storedSignature = snapshot.hmacSignatureBase64

    if (startedAt == null && storedSignature == null && snapshot.maxObservedClockMs == 0L) {
      return TrialEvaluation(
        status = TrialStatus.NOT_STARTED,
        startedAtUtcMs = null,
        expiresAtUtcMs = null,
        remainingDays = 7,
        remainingHours = 0,
      )
    }

    // Partial or corrupted storage -> tampered
    if (startedAt == null || storedSignature.isNullOrBlank()) {
      return tamperedEvaluation(startedAt)
    }

    // Clock rollback protection
    if (nowUtcMs < snapshot.maxObservedClockMs - EntitlementValidator.CLOCK_ROLLBACK_TOLERANCE_MS) {
      return tamperedEvaluation(startedAt)
    }

    // HMAC verification
    val expectedSignature = computeHmacBase64(startedAt, snapshot.maxObservedClockMs)
    if (expectedSignature == null || !constantTimeEquals(storedSignature, expectedSignature)) {
      return tamperedEvaluation(startedAt)
    }

    val expiresAt = startedAt + EntitlementValidator.TRIAL_DURATION_MS
    val remainingMs = expiresAt - nowUtcMs
    if (remainingMs <= 0L) {
      return TrialEvaluation(
        status = TrialStatus.EXPIRED,
        startedAtUtcMs = startedAt,
        expiresAtUtcMs = expiresAt,
        remainingDays = 0,
        remainingHours = 0,
      )
    }

    val days = (remainingMs / MS_PER_DAY).toInt()
    val hours = ((remainingMs % MS_PER_DAY) / MS_PER_HOUR).toInt()
    return TrialEvaluation(
      status = TrialStatus.ACTIVE,
      startedAtUtcMs = startedAt,
      expiresAtUtcMs = expiresAt,
      remainingDays = days,
      remainingHours = hours,
    )
  }

  private fun tamperedEvaluation(startedAt: Long?): TrialEvaluation =
    TrialEvaluation(
      status = TrialStatus.EXPIRED_TAMPERED,
      startedAtUtcMs = startedAt,
      expiresAtUtcMs = startedAt?.plus(EntitlementValidator.TRIAL_DURATION_MS),
      remainingDays = 0,
      remainingHours = 0,
    )

  private fun computeHmacBase64(startedAtUtcMs: Long, maxObservedClockMs: Long): String? {
    return runCatching {
      val secretKey = getOrCreateHmacKey()
      val mac = Mac.getInstance(HMAC_ALGORITHM)
      mac.init(secretKey)
      val payload = "$startedAtUtcMs:$maxObservedClockMs".toByteArray(Charsets.UTF_8)
      val digest = mac.doFinal(payload)
      Base64.encodeToString(digest, Base64.NO_WRAP)
    }.getOrNull()
  }

  private fun getOrCreateHmacKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
    val existing = keyStore.getKey(HMAC_KEY_ALIAS, null) as? SecretKey
    if (existing != null) return existing

    val keyGenerator =
      KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE_PROVIDER)
    val spec =
      KeyGenParameterSpec.Builder(
        HMAC_KEY_ALIAS,
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
      )
        .setDigests(KeyProperties.DIGEST_SHA256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
