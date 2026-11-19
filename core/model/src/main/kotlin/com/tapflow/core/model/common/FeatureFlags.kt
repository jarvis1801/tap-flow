package com.tapflow.core.model.common

/**
 * Centralized release feature flags for TapFlow.
 * Kept in :core:model so all layers share compile-time / runtime gating without Android dependencies.
 */
object FeatureFlags {
  const val ENABLE_DESKTOP_CONSOLE: Boolean = false
}
