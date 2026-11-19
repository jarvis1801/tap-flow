package com.tapflow.core.service.overlay

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedSaveQueueCoreTest {

  @Test
  fun enforcesMaxThreePendingItemsSequentialProcessingAndPostFlushDrain() = runBlocking {
    val firstGate = CompletableDeferred<Unit>()
    val remainingGate = CompletableDeferred<Unit>()
    val firstStarted = CompletableDeferred<Unit>()
    val events = CopyOnWriteArrayList<String>()
    val cleanedUp = CopyOnWriteArrayList<Int>()
    val flushCompleted = CompletableDeferred<Unit>()

    val queue =
      BoundedSaveQueueCore<Int>(
        maxCapacity = 3,
        workerDispatcher = Dispatchers.Default,
        processor = { id ->
          events.add("start:$id")
          if (id == 1) {
            firstStarted.complete(Unit)
            firstGate.await()
          } else {
            remainingGate.await()
          }
          events.add("done:$id")
        },
        onCleanup = { id ->
          cleanedUp.add(id)
        },
      )

    assertFalse(queue.isQueueFull())
    assertEquals(0, queue.pendingSaveCount.value)

    assertTrue(queue.tryEnqueue(1))
    withTimeout(3_000L) { firstStarted.await() }
    assertEquals(1, queue.pendingSaveCount.value)
    assertFalse(queue.isQueueFull())

    assertTrue(queue.tryEnqueue(2))
    assertEquals(2, queue.pendingSaveCount.value)
    assertFalse(queue.isQueueFull())

    assertTrue(queue.tryEnqueue(3))
    assertEquals(3, queue.pendingSaveCount.value)
    assertTrue(queue.isQueueFull())

    // 4th capture must be rejected cleanly without exceeding capacity 3
    assertFalse(queue.tryEnqueue(4))
    assertEquals(3, queue.pendingSaveCount.value)

    // Enqueue post-flush finalizer (simulates Stop / Close while queue is draining)
    assertTrue(
      queue.enqueuePostFlush {
        events.add("flush")
        flushCompleted.complete(Unit)
      },
    )
    assertFalse(flushCompleted.isCompleted)

    // Release tasks and await full drain
    firstGate.complete(Unit)
    remainingGate.complete(Unit)

    withTimeout(3_000L) {
      queue.awaitIdle()
      flushCompleted.await()
    }

    assertEquals(0, queue.pendingSaveCount.value)
    assertFalse(queue.isQueueFull())
    assertEquals(listOf(1, 2, 3), cleanedUp.toList())
    // Verify strictly sequential execution and post-flush ordering after all 3 items
    assertEquals(
      listOf("start:1", "done:1", "start:2", "done:2", "start:3", "done:3", "flush"),
      events.toList(),
    )
  }
}
