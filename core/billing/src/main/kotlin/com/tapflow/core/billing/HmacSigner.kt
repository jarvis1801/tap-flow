package com.tapflow.core.billing

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

interface HmacSigner {
  fun signBase64(payload: String): String?

  fun verifyBase64(payload: String, storedSignatureBase64: String): Boolean {
    val expected = signBase64(payload) ?: return false
    return MessageDigest.isEqual(
      storedSignatureBase64.toByteArray(Charsets.UTF_8),
      expected.toByteArray(Charsets.UTF_8),
    )
  }
}

@Singleton
class AndroidKeyStoreHmacSigner @Inject constructor() : HmacSigner {
  companion object {
    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val HMAC_KEY_ALIAS = "tapflow_trial_hmac_key"
    private const val HMAC_ALGORITHM = "HmacSHA256"
  }

  override fun signBase64(payload: String): String? {
    return runCatching {
      val secretKey = getOrCreateHmacKey()
      val mac = Mac.getInstance(HMAC_ALGORITHM)
      mac.init(secretKey)
      val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
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
}
