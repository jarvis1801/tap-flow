package com.tapflow.feature.home

import java.util.Locale

internal object HomeLibraryFilter {
  fun extractAvailableTags(tagLists: Iterable<List<String>>): List<String> =
    tagLists
      .asSequence()
      .flatten()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinctBy { it.lowercase(Locale.ROOT) }
      .sortedWith(String.CASE_INSENSITIVE_ORDER)
      .toList()

  fun pruneSelectedTags(
    selectedTags: Set<String>,
    availableTags: List<String>,
  ): Set<String> {
    if (selectedTags.isEmpty() || availableTags.isEmpty()) return emptySet()
    val canonicalByLower = availableTags.associateBy { it.lowercase(Locale.ROOT) }
    return selectedTags
      .mapNotNull { tag -> canonicalByLower[tag.trim().lowercase(Locale.ROOT)] }
      .toSet()
  }

  fun toggleTagSelection(
    selectedTags: Set<String>,
    rawTag: String,
    availableTags: List<String>,
  ): Set<String> {
    val clean = rawTag.trim()
    if (clean.isEmpty()) return selectedTags
    val alreadySelected = selectedTags.any { it.equals(clean, ignoreCase = true) }
    if (alreadySelected) {
      return selectedTags.filterNot { it.equals(clean, ignoreCase = true) }.toSet()
    }
    val canonical = availableTags.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
    return selectedTags + canonical
  }

  fun isTagSelected(
    tag: String,
    selectedTags: Set<String>,
  ): Boolean {
    val clean = tag.trim()
    if (clean.isEmpty() || selectedTags.isEmpty()) return false
    return selectedTags.any { it.equals(clean, ignoreCase = true) }
  }

  fun matchesSearchAndTags(
    title: String,
    tags: List<String>,
    query: String,
    selectedTags: Set<String>,
  ): Boolean {
    val trimmedQuery = query.trim()
    val matchesSearchQuery =
      trimmedQuery.isEmpty() ||
        title.contains(trimmedQuery, ignoreCase = true) ||
        tags.any { it.contains(trimmedQuery, ignoreCase = true) }
    val matchesTags =
      selectedTags.isEmpty() ||
        tags.any { tag ->
          val cleanTag = tag.trim()
          cleanTag.isNotEmpty() && selectedTags.any { it.equals(cleanTag, ignoreCase = true) }
        }
    return matchesSearchQuery && matchesTags
  }
}
