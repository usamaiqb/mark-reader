package com.markreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The chrome's show/hide decision, which used to be three magic numbers over a signal that could
 * not tell a finger from a relayout.
 *
 * What replaced them is one threshold over deltas that are a finger by construction — so what is
 * worth pinning here is not the number but the *shape*: which direction means what, that travel
 * accumulates across frames because a drag arrives in many small pieces, that a turn does not
 * cash in the travel banked going the other way, and that most frames have no opinion at all.
 * Every one of those is silent when it regresses; the bar just feels wrong.
 */
class ChromeDragTrackerTest {

    @Test
    fun `a drag up the screen hides the chrome`() {
        // Negative is Compose's direction for a finger moving up, which drives the document down.
        // A reader going down the document is reading, and wants the room.
        assertEquals(false, ChromeDragTracker(THRESHOLD).accept(0f, -30f))
    }

    @Test
    fun `a drag down the screen shows the chrome`() {
        assertEquals(true, ChromeDragTracker(THRESHOLD).accept(0f, 30f))
    }

    @Test
    fun `travel accumulates across frames`() {
        // The point of accumulating: a slow drag arrives as many deltas smaller than the
        // threshold, and it is still a drag. Comparing each frame against the threshold on its
        // own would mean only a flick could ever move the bar.
        val tracker = ChromeDragTracker(THRESHOLD)

        assertNull(tracker.accept(0f, -4f))
        assertNull(tracker.accept(0f, -4f))
        assertEquals(false, tracker.accept(0f, -4f))
    }

    @Test
    fun `a delta below the threshold decides nothing`() {
        assertNull(ChromeDragTracker(THRESHOLD).accept(0f, -1f))
    }

    @Test
    fun `a decision resets the travel`() {
        // Without the reset the tracker would answer on every frame for the rest of the drag,
        // and the caller would be told to hide a bar that is already hidden sixty times a second.
        val tracker = ChromeDragTracker(THRESHOLD)
        assertEquals(false, tracker.accept(0f, -20f))

        assertNull(tracker.accept(0f, -4f))
    }

    @Test
    fun `turning round does not spend the travel banked the other way`() {
        // The case that makes this a tracker rather than a running sum. Dragging a long way down
        // the document and then reversing must not show the chrome instantly on the first upward
        // pixel — nor need a drag long enough to cancel out everything that came before.
        val tracker = ChromeDragTracker(THRESHOLD)
        assertNull(tracker.accept(0f, -9f))

        assertNull(tracker.accept(0f, 4f))
        assertEquals(true, tracker.accept(0f, 7f))
    }

    @Test
    fun `a sideways swipe is not an opinion about the chrome`() {
        // The case that reverted a whole design. A document's wide tables and code blocks scroll
        // horizontally, and a swipe across one wobbles vertically; without this the bar hides
        // every time a reader looks at the right-hand column of a table.
        val tracker = ChromeDragTracker(THRESHOLD)

        assertNull(tracker.accept(-50f, -6f))
        assertNull(tracker.accept(-60f, -7f))
        assertNull(tracker.accept(-70f, -8f))
    }

    @Test
    fun `a diagonal drag that is mostly vertical still counts`() {
        // The inverse, and why the test is dominance rather than "any horizontal movement at all":
        // no real vertical scroll is perfectly straight.
        assertEquals(false, ChromeDragTracker(THRESHOLD).accept(-4f, -30f))
    }

    @Test
    fun `a zero delta is not a direction`() {
        // A frame that consumed nothing is the end of a document, not a change of mind, and
        // treating it as positive would show the chrome every time a reader hit the bottom.
        val tracker = ChromeDragTracker(THRESHOLD)
        assertNull(tracker.accept(0f, -9f))
        assertNull(tracker.accept(0f, 0f))

        assertEquals(false, tracker.accept(0f, -2f))
    }

    private companion object {
        /** Stands in for 12.dp; the tests are about the shape, not the size. */
        const val THRESHOLD = 10f
    }
}
