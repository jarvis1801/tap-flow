package com.tapflow.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLibraryFilterTest {
  @Test
  fun extractAvailableTags_trims_ignoresBlank_deduplicatesCaseInsensitive_andSorts() {
    val tags =
      HomeLibraryFilter.extractAvailableTags(
        listOf(
          listOf(" RPG ", "", "Daily", "farming"),
          listOf("rpg", "   ", "Boss", "DAILY"),
        ),
      )

    assertEquals(listOf("Boss", "Daily", "farming", "RPG"), tags)
  }

  @Test
  fun pruneSelectedTags_removesDeletedTags_andNormalizesCasing() {
    val available = listOf("Boss", "Daily", "RPG")
    val selected = setOf("rpg", "RemovedTag", "daily")

    val pruned = HomeLibraryFilter.pruneSelectedTags(selected, available)

    assertEquals(setOf("RPG", "Daily"), pruned)
  }

  @Test
  fun pruneSelectedTags_returnsEmpty_whenAvailableTagsEmpty() {
    val pruned = HomeLibraryFilter.pruneSelectedTags(setOf("RPG"), emptyList())
    assertTrue(pruned.isEmpty())
  }

  @Test
  fun toggleTagSelection_addsAndRemovesCaseInsensitivelyUsingCanonicalLabel() {
    val available = listOf("Daily", "RPG")

    val afterAdd = HomeLibraryFilter.toggleTagSelection(emptySet(), "rpg", available)
    assertEquals(setOf("RPG"), afterAdd)

    val afterSecondAdd = HomeLibraryFilter.toggleTagSelection(afterAdd, "Daily", available)
    assertEquals(setOf("RPG", "Daily"), afterSecondAdd)

    val afterRemove = HomeLibraryFilter.toggleTagSelection(afterSecondAdd, "RPG", available)
    assertEquals(setOf("Daily"), afterRemove)
  }

  @Test
  fun matchesSearchAndTags_combinesQueryAndMultiSelectTags() {
    val selected = setOf("RPG", "Daily")

    assertTrue(
      HomeLibraryFilter.matchesSearchAndTags(
        title = "Arena Battle",
        tags = listOf("rpg", "PvP"),
        query = "arena",
        selectedTags = selected,
      ),
    )

    assertTrue(
      HomeLibraryFilter.matchesSearchAndTags(
        title = "Check-In",
        tags = listOf("DAILY"),
        query = "daily",
        selectedTags = selected,
      ),
    )

    assertFalse(
      HomeLibraryFilter.matchesSearchAndTags(
        title = "Arena Battle",
        tags = listOf("Utility"),
        query = "arena",
        selectedTags = selected,
      ),
    )

    assertFalse(
      HomeLibraryFilter.matchesSearchAndTags(
        title = "Dungeon Sweep",
        tags = listOf("RPG"),
        query = "arena",
        selectedTags = selected,
      ),
    )
  }
}
