package com.tapflow.feature.home

import com.tapflow.core.model.action.ScriptAction
import com.tapflow.core.model.condition.ConditionGroup
import com.tapflow.core.model.condition.ConditionLeaf
import com.tapflow.core.model.condition.ConditionPredicate
import com.tapflow.core.model.entitlement.EntitlementValidator
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.pipeline.PipelineStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptLibraryHelpersTest {
  @Test
  fun starterTemplates_createTwoFreeTierExamplesWithUniqueIds() {
    val templates = StarterScriptTemplates.createAll(nowMs = 1_750_000_000_000L)
    assertEquals(2, templates.size)
    assertEquals(
      listOf("Example: Basic Tap Loop", "Example: Color Match Decision"),
      templates.map { it.title },
    )
    assertEquals(2, templates.map { it.id }.toSet().size)
    templates.forEach { script ->
      assertTrue(
        "Expected starter template '${script.title}' to require zero Pro gates",
        EntitlementValidator.detectProFeaturesInScript(script).isEmpty(),
      )
    }
    val decision = templates[1]
    assertTrue(decision.requiresVision())
  }

  @Test
  fun cloneActions_reassignsNestedIdsAndRemapsTemplatePaths() = runBlocking {
    val templates = StarterScriptTemplates.createAll(nowMs = 1_000L)
    val originalIf = templates[1].actions.single() as ScriptAction.IfBlock
    val imageLeaf =
      ConditionLeaf(
        id = "leaf-img",
        predicate = ConditionPredicate.ImageMatch(templatePngPath = "/tmp/original_tpl.png"),
      )
    val rootWithImage =
      originalIf.copy(
        conditionRoot =
          ConditionGroup(
            id = "grp-img",
            children = originalIf.conditionRoot.children + imageLeaf,
          ),
      )

    val cloned =
      ScriptCloningUtils.cloneActions(
        actions = listOf(rootWithImage),
        copyTemplatePath = { path -> "$path.copied" },
      )
    val clonedIf = cloned.single() as ScriptAction.IfBlock
    assertNotEquals(rootWithImage.id, clonedIf.id)
    assertNotEquals(rootWithImage.conditionRoot.id, clonedIf.conditionRoot.id)
    assertNotEquals(rootWithImage.thenActions.single().id, clonedIf.thenActions.single().id)
    assertNotEquals(
      rootWithImage.elseIfBranches.single().id,
      clonedIf.elseIfBranches.single().id,
    )
    assertNotEquals(
      rootWithImage.elseActions.single().id,
      clonedIf.elseActions.single().id,
    )

    val clonedImageLeaf = clonedIf.conditionRoot.children.last() as ConditionLeaf
    assertNotEquals(imageLeaf.id, clonedImageLeaf.id)
    assertEquals(
      "/tmp/original_tpl.png.copied",
      (clonedImageLeaf.predicate as ConditionPredicate.ImageMatch).templatePngPath,
    )
  }

  @Test
  fun clonePipelineForDuplicate_appendsCopySuffixAndReassignsIds() {
    val source =
      Pipeline(
        id = "pipe-1",
        title = "Daily Routine",
        steps =
          listOf(
            PipelineStep(id = "step-1", scriptId = "script-a", repeatCount = 2),
            PipelineStep(id = "step-2", scriptId = "script-b", repeatCount = 1),
          ),
        createdAtMs = 100L,
        updatedAtMs = 200L,
      )

    val cloned = ScriptCloningUtils.clonePipelineForDuplicate(source, nowMs = 9_999L)
    assertNotEquals(source.id, cloned.id)
    assertEquals("Daily Routine (Copy)", cloned.title)
    assertEquals(9_999L, cloned.createdAtMs)
    assertEquals(9_999L, cloned.updatedAtMs)
    assertEquals(2, cloned.steps.size)
    assertNotEquals(source.steps[0].id, cloned.steps[0].id)
    assertNotEquals(source.steps[1].id, cloned.steps[1].id)
    assertEquals(source.steps.map { it.scriptId }, cloned.steps.map { it.scriptId })
  }
}
