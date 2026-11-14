package com.tapflow.feature.overlay

import androidx.compose.runtime.Composable
import com.tapflow.core.service.overlay.OverlayCallbacks
import com.tapflow.core.service.overlay.OverlayContentProvider
import com.tapflow.core.service.overlay.OverlayUiState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TapFlowOverlayContentProvider @Inject constructor() : OverlayContentProvider {
  @Composable
  override fun FloatingDockContent(
    state: OverlayUiState,
    callbacks: OverlayCallbacks,
  ) {
    TapFlowOverlayTheme {
      OverlayDockView(
        state = state,
        callbacks = callbacks,
      )
    }
  }

  @Composable
  override fun CaptureLayerFeedbackContent(state: OverlayUiState) {
    TapFlowOverlayTheme {
      OverlayCaptureFeedbackLayer(state = state)
    }
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OverlayFeatureModule {
  @Binds
  @Singleton
  abstract fun bindOverlayContentProvider(
    impl: TapFlowOverlayContentProvider,
  ): OverlayContentProvider
}
