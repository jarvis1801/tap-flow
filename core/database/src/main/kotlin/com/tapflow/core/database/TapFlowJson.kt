package com.tapflow.core.database

import kotlinx.serialization.json.Json

object TapFlowJson {
  val instance: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "kind"
  }
}
