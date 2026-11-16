package com.tapflow.core.model

import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.common.DelayConfig
import com.tapflow.core.model.common.NumericInput
import com.tapflow.core.model.common.OverlaySizeOption
import com.tapflow.core.model.condition.ColorMatchMode
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
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.pipeline.PipelineErrorStrategy
import com.tapflow.core.model.pipeline.PipelineRepeatMode
import com.tapflow.core.model.pipeline.PipelineStep
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
  fun entitlementValidatorDetectsFreeVsProScriptAndPipelineFeatures() {
    val richFreeScript =
      Script(
        id = "s1",
        title = "Full Logic + Jitter + Single Color",
        globalDelay = DelayConfig.RandomRange(100L, 250L),
        actions = listOf(
          ScriptAction.Tap(id = "a1", jitterPx = 12),
          ScriptAction.MultiTouch(id = "a2"),
          ScriptAction.ForceStopApp(id = "a3", packageName = "com.example.game"),
          ScriptAction.IfBlock(
            id = "if1",
            conditionRoot = ConditionGroup(
              id = "root",
              children = listOf(
                ConditionLeaf(
                  id = "c1",
                  predicate = ConditionPredicate.ColorMatch(
                    mode = ColorMatchMode.SINGLE_POINT,
                    targets = listOf(ColorTarget(10, 20, "#00E5FF")),
                  ),
                ),
                ConditionLeaf(
                  id = "c2",
                  predicate = ConditionPredicate.AppMatch("com.example.game", "Game"),
                ),
              ),
            ),
          ),
        ),
      )
    assertTrue(EntitlementValidator.detectProFeaturesInScript(richFreeScript).isEmpty())

    val visionProScript =
      Script(
        id = "s2",
        title = "Multi-Point & Template Match",
        actions = listOf(
          ScriptAction.IfBlock(
            id = "if2",
            conditionRoot = ConditionGroup(
              id = "root2",
              children = listOf(
                ConditionLeaf(
                  id = "c3",
                  predicate = ConditionPredicate.ColorMatch(
                    mode = ColorMatchMode.MULTI_POINT,
                    targets = listOf(
                      ColorTarget(10, 20, "#00E5FF"),
                      ColorTarget(30, 40, "#FF0000"),
                    ),
                  ),
                ),
                ConditionLeaf(
                  id = "c4",
                  predicate = ConditionPredicate.ImageMatch(templatePngPath = "/tmp/tpl.png"),
                ),
              ),
            ),
          ),
        ),
      )
    assertEquals(
      setOf(ProFeatureGate.ADVANCED_VISION),
      EntitlementValidator.detectProFeaturesInScript(visionProScript),
    )

    val scriptsMap = mapOf("s1" to richFreeScript, "s2" to visionProScript)
    val freePipeline =
      Pipeline(
        id = "p1",
        title = "Free Once",
        repeatMode = PipelineRepeatMode.RunOnce,
        errorStrategy = PipelineErrorStrategy.ABORT,
        steps = listOf(PipelineStep(id = "st1", scriptId = "s1")),
      )
    assertTrue(EntitlementValidator.detectProFeaturesInPipeline(freePipeline, scriptsMap).isEmpty())

    val loopPipeline =
      freePipeline.copy(
        repeatMode = PipelineRepeatMode.Count(3),
        errorStrategy = PipelineErrorStrategy.SKIP_AND_CONTINUE,
      )
    assertEquals(
      setOf(ProFeatureGate.PIPELINE_AUTOMATION),
      EntitlementValidator.detectProFeaturesInPipeline(loopPipeline, scriptsMap),
    )

    val freeState = EntitlementState.Free(TrialStatus.NOT_STARTED)
    assertFalse(EntitlementValidator.isPro(freeState))
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
        ProFeatureGate.EXTERNAL_BROADCAST,
        ProFeatureGate.SHIZUKU_SUITE,
        ProFeatureGate.ADVANCED_VISION,
        ProFeatureGate.PIPELINE_AUTOMATION,
        ProFeatureGate.FULL_LOGS,
        ProFeatureGate.AMOLED_THEME,
      )
    val lifetimeOnlyGates =
      listOf(
        ProFeatureGate.DESKTOP_CONSOLE,
        ProFeatureGate.DATA_BACKUP,
      )

    assertEquals(
      8,
      trialUnlockedGates.size + lifetimeOnlyGates.size,
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

  @Test
  fun overlaySizeOptionMetricsAndSerialization() {
    assertEquals(
      listOf(
        OverlaySizeOption.TINY,
        OverlaySizeOption.COMPACT,
        OverlaySizeOption.MEDIUM,
        OverlaySizeOption.LARGE,
      ),
      OverlaySizeOption.entries,
    )
    assertEquals(28, OverlaySizeOption.TINY.buttonSizeDp)
    assertEquals(16, OverlaySizeOption.TINY.iconSizeDp)
    assertEquals(28, OverlaySizeOption.TINY.bubbleSizeDp)
    assertEquals(8, OverlaySizeOption.TINY.counterTextSp)
    assertEquals(2, OverlaySizeOption.TINY.paddingDp)

    assertEquals(34, OverlaySizeOption.COMPACT.buttonSizeDp)
    assertEquals(19, OverlaySizeOption.COMPACT.iconSizeDp)
    assertEquals(34, OverlaySizeOption.COMPACT.bubbleSizeDp)
    assertEquals(10, OverlaySizeOption.COMPACT.counterTextSp)
    assertEquals(3, OverlaySizeOption.COMPACT.paddingDp)

    assertEquals(42, OverlaySizeOption.MEDIUM.buttonSizeDp)
    assertEquals(22, OverlaySizeOption.MEDIUM.iconSizeDp)
    assertEquals(42, OverlaySizeOption.MEDIUM.bubbleSizeDp)
    assertEquals(11, OverlaySizeOption.MEDIUM.counterTextSp)
    assertEquals(4, OverlaySizeOption.MEDIUM.paddingDp)

    assertEquals(50, OverlaySizeOption.LARGE.buttonSizeDp)
    assertEquals(26, OverlaySizeOption.LARGE.iconSizeDp)
    assertEquals(50, OverlaySizeOption.LARGE.bubbleSizeDp)
    assertEquals(12, OverlaySizeOption.LARGE.counterTextSp)
    assertEquals(6, OverlaySizeOption.LARGE.paddingDp)

    OverlaySizeOption.entries.forEach { option ->
      val encoded = json.encodeToString(OverlaySizeOption.serializer(), option)
      val decoded = json.decodeFromString(OverlaySizeOption.serializer(), encoded)
      assertEquals(option, decoded)
    }
  }

  @Test
  fun elseIfBranch_isBackwardCompatibleAndTraversedByTreeScanners() {
    val elseIfIfBlock =
      ScriptAction.IfBlock(
        id = "if-chain",
        name = "Chain",
        conditionRoot = ConditionGroup(id = "if-root"),
        thenActions = listOf(ScriptAction.Delay(id = "then-d")),
        elseIfBranches =
          listOf(
            ScriptAction.ElseIfBranch(
              id = "ei-1",
              name = "Popup",
              conditionRoot =
                ConditionGroup(
                  id = "ei1-root",
                  children =
                    listOf(
                      ConditionLeaf(
                        id = "ei1-leaf",
                        predicate =
                          ConditionPredicate.ImageMatch(templatePngPath = "/tmp/popup.png"),
                      ),
                    ),
                ),
              actions =
                listOf(
                  ScriptAction.Tap(id = "ei1-tap", jitterPx = 8),
                ),
            ),
          ),
        elseActions = listOf(ScriptAction.Delay(id = "else-d")),
      )

    val script = Script(id = "s-chain", title = "Chain", actions = listOf(elseIfIfBlock))
    assertTrue(script.requiresVision())
    assertEquals(4, script.totalActionCount())

    val proGates = EntitlementValidator.detectProFeaturesInScript(script)
    assertEquals(setOf(ProFeatureGate.ADVANCED_VISION), proGates)
  }
}
