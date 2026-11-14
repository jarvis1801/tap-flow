package com.tapflow.core.model

import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.condition.ColorTarget
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.condition.ConditionValidator
import com.tapflow.core.model.condition.LogicOperator
import com.tapflow.core.model.entitlement.EntitlementState
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.entitlement.ProFeatureGate
import com.tapflow.core.model.entitlement.TrialStatus
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.ScreenOrientation
import com.tapflow.core.model.ipc.WsMessage
import com.tapflow.core.model.ipc.WsMessageType
import com.tapflow.core.model.script.Script
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelValidationTest {
  private val json = Json { encodeDefaults = true }

  @Test
  fun conditionValidatorEnforcesMaxDepth3() {
    val leaf =
      ConditionLeaf(
        id = "leaf-1",
        predicate = ConditionPredicate.AppMatch("com.jarvis.tapflow.game", "Game"),
      )
    val level3 = ConditionGroup(id = "g3", operator = LogicOperator.ALL, children = listOf(leaf))
    val level2 = ConditionGroup(id = "g2", operator = LogicOperator.ANY, children = listOf(level3))
    val level1 = ConditionGroup(id = "g1", operator = LogicOperator.ALL, children = listOf(level2))

    assertEquals(3, ConditionValidator.calculateMaxDepth(level1))
    assertTrue(ConditionValidator.isValidDepth(level1))
    assertTrue(ConditionValidator.canAddSubGroup(level1, "g2"))
    assertFalse(ConditionValidator.canAddSubGroup(level1, "g3"))

    val level4 = ConditionGroup(id = "g4", operator = LogicOperator.ALL, children = listOf(leaf))
    val invalidRoot = level1.copy(children = listOf(level2.copy(children = listOf(level3.copy(children = listOf(level4))))))
    assertEquals(4, ConditionValidator.calculateMaxDepth(invalidRoot))
    assertFalse(ConditionValidator.isValidDepth(invalidRoot))
  }

  @Test
  fun entitlementValidatorDetectsFreeVsProScriptFeatures() {
    val freeScript =
      Script(
        id = "s1",
        title = "Basic Tap",
        actions = listOf(
          ScriptAction.Tap(
            id = "a1",
            x = NumericInput.Literal(540),
            y = NumericInput.Literal(960),
          )
        ),
      )
    assertTrue(EntitlementValidator.detectProFeaturesInScript(freeScript).isEmpty())

    val proScript =
      Script(
        id = "s2",
        title = "Smart Tap",
        globalDelay = DelayConfig.RandomRange(100L, 250L),
        actions = listOf(
          ScriptAction.Tap(id = "a1", jitterPx = 12),
          ScriptAction.IfBlock(
            id = "if1",
            conditionRoot = ConditionGroup(
              id = "root",
              children = listOf(
                ConditionLeaf(
                  id = "c1",
                  predicate = ConditionPredicate.ColorMatch(
                    targets = listOf(ColorTarget(10, 20, "#00E5FF")),
                  ),
                ),
              ),
            ),
          ),
        ),
      )
    val detected = EntitlementValidator.detectProFeaturesInScript(proScript)
    assertTrue(detected.contains(ProFeatureGate.RANDOM_DEVIATION_DELAY))
    assertTrue(detected.contains(ProFeatureGate.ANTI_BAN_JITTER))
    assertTrue(detected.contains(ProFeatureGate.IF_BLOCK))

    val freeState = EntitlementState.Free(TrialStatus.NOT_STARTED)
    assertFalse(EntitlementValidator.isPro(freeState))
    assertTrue(EntitlementValidator.canCreateScript(freeState, 2))
    assertFalse(EntitlementValidator.canCreateScript(freeState, 3))
  }

  @Test
  fun entitlementValidatorSeparatesTrialAndLifetimeOnlyGates() {
    val freeState = EntitlementState.Free(TrialStatus.NOT_STARTED)
    val trialState = EntitlementState.ProTrial(expiresAtUtcMs = 1_900_000_000_000L, remainingDays = 6, remainingHours = 23)
    val pendingState = EntitlementState.PendingPurchase
    val lifetimeState = EntitlementState.ProLifetime(purchaseTokenHash = "abc", purchasedAtUtcMs = 1_700_000_000_000L)

    assertFalse(freeState.hasTrialOrLifetimeAccess)
    assertFalse(freeState.hasLifetimeProAccess)
    assertTrue(trialState.hasTrialOrLifetimeAccess)
    assertFalse(trialState.hasLifetimeProAccess)
    assertFalse(pendingState.hasTrialOrLifetimeAccess)
    assertFalse(pendingState.hasLifetimeProAccess)
    assertTrue(lifetimeState.hasTrialOrLifetimeAccess)
    assertTrue(lifetimeState.hasLifetimeProAccess)

    val trialUnlockedGates =
      listOf(
        ProFeatureGate.IMAGE_MATCH,
        ProFeatureGate.IF_BLOCK,
        ProFeatureGate.ADVANCED_LOOPS,
        ProFeatureGate.DYNAMIC_VARIABLES,
        ProFeatureGate.ANTI_BAN_JITTER,
        ProFeatureGate.SHIZUKU_INTEGRATION,
        ProFeatureGate.LIVE_IMAGE_INSPECTOR,
        ProFeatureGate.FULL_EXECUTION_LOGS,
        ProFeatureGate.UNLIMITED_SCRIPTS,
        ProFeatureGate.UNLIMITED_PIPELINES,
      )
    val lifetimeOnlyGates =
      listOf(
        ProFeatureGate.DESKTOP_CONSOLE,
        ProFeatureGate.DATA_BACKUP,
      )

    trialUnlockedGates.forEach { gate ->
      assertFalse(EntitlementValidator.canUseFeature(freeState, gate))
      assertTrue(EntitlementValidator.canUseFeature(trialState, gate))
      assertFalse(EntitlementValidator.canUseFeature(pendingState, gate))
      assertTrue(EntitlementValidator.canUseFeature(lifetimeState, gate))
    }

    lifetimeOnlyGates.forEach { gate ->
      assertFalse(EntitlementValidator.canUseFeature(freeState, gate))
      assertFalse(EntitlementValidator.canUseFeature(trialState, gate))
      assertFalse(EntitlementValidator.canUseFeature(pendingState, gate))
      assertTrue(EntitlementValidator.canUseFeature(lifetimeState, gate))
    }
  }

  @Test
  fun wsMessageRoundTripSerialization() {
    val msg =
      WsMessage(
        type = WsMessageType.HANDSHAKE_ACK,
        requestId = "req-1",
        timestamp = 1_700_000_000_000L,
        payloadJson = """{"version":"1.0"}""",
      )
    val encoded = json.encodeToString(WsMessage.serializer(), msg)
    val decoded = json.decodeFromString(WsMessage.serializer(), encoded)
    assertEquals(msg, decoded)
  }

  @Test
  fun inspectItemClassifiesPortraitLandscapeAndFoldableUnfolded() {
    fun makeItem(w: Int, h: Int) =
      InspectItem(
        id = "i1",
        groupId = "g1",
        fullScreenPngPath = "",
        screenWidth = w,
        screenHeight = h,
        appName = "Test",
        packageName = "com.test",
      )

    val portrait = makeItem(1080, 2400)
    assertEquals(ScreenOrientation.PORTRAIT, portrait.screenOrientation)
    assertNull(portrait.orientationSubHint)

    val landscape = makeItem(2400, 1080)
    assertEquals(ScreenOrientation.LANDSCAPE, landscape.screenOrientation)
    assertNull(landscape.orientationSubHint)

    val unfoldedTall = makeItem(2076, 2152)
    assertEquals(ScreenOrientation.SQUARE_UNFOLDED, unfoldedTall.screenOrientation)
    assertEquals("Unfolded (Portrait)", unfoldedTall.orientationSubHint)

    val unfoldedWide = makeItem(2152, 2076)
    assertEquals(ScreenOrientation.SQUARE_UNFOLDED, unfoldedWide.screenOrientation)
    assertEquals("Unfolded (Landscape)", unfoldedWide.orientationSubHint)

    val unfoldedFold4 = makeItem(1812, 2176)
    assertEquals(ScreenOrientation.SQUARE_UNFOLDED, unfoldedFold4.screenOrientation)
    assertEquals("Unfolded (Portrait)", unfoldedFold4.orientationSubHint)

    val exactSquare = makeItem(1800, 1800)
    assertEquals(ScreenOrientation.SQUARE_UNFOLDED, exactSquare.screenOrientation)
    assertEquals("Unfolded (Square)", exactSquare.orientationSubHint)
  }
}
