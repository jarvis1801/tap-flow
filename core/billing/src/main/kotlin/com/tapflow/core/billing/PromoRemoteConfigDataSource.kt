package com.tapflow.core.billing

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

interface PromoRemoteConfigDataSource {
  suspend fun fetchPromoCodesJson(forceRefresh: Boolean): Result<String>

  companion object {
    const val PARAM_PROMO_REDEEM_CODES: String = "promo_redeem_codes"
    const val FETCH_INTERVAL_REDEEM_SECONDS: Long = 0L
    const val FETCH_INTERVAL_BACKGROUND_SECONDS: Long = 3600L
  }
}

@Singleton
class FirebasePromoRemoteConfigDataSource @Inject constructor() : PromoRemoteConfigDataSource {
  override suspend fun fetchPromoCodesJson(forceRefresh: Boolean): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val intervalSeconds =
          if (forceRefresh) {
            PromoRemoteConfigDataSource.FETCH_INTERVAL_REDEEM_SECONDS
          } else {
            PromoRemoteConfigDataSource.FETCH_INTERVAL_BACKGROUND_SECONDS
          }
        val settings =
          FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(intervalSeconds)
            .build()
        remoteConfig.setConfigSettingsAsync(settings).awaitResult()
        remoteConfig.fetchAndActivate().awaitResult()
        remoteConfig.getString(PromoRemoteConfigDataSource.PARAM_PROMO_REDEEM_CODES)
      }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
  suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { completedTask ->
      if (!continuation.isActive) return@addOnCompleteListener
      val exception = completedTask.exception
      if (exception != null) {
        continuation.resumeWithException(exception)
      } else if (completedTask.isCanceled) {
        continuation.cancel()
      } else {
        @Suppress("UNCHECKED_CAST")
        continuation.resume(completedTask.result as T)
      }
    }
  }
