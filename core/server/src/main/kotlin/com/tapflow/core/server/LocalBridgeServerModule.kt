package com.tapflow.core.server

import com.tapflow.core.service.bridge.LocalBridgeServerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalBridgeServerModule {
  @Binds
  abstract fun bindLocalBridgeServerController(impl: TapFlowLocalServer): LocalBridgeServerController
}
