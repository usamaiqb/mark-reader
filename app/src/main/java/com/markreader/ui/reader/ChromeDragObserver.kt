package com.markreader.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * How far the reader has to drag before the chrome reacts.
 *
 * **The one number left, and a different kind of number from the three it replaced.** Those existed
 * because the signal lied: chrome visibility was derived by diffing scroll positions, which cannot
 * tell a finger from a relayout, so `delta < -8` and `delta in 9..1200` were guesses about which
 * changes had been a person and a 350 ms settle window covered the times the guess was wrong — by
 * ignoring real input. Nothing below is a guess about what happened: it is a finger, read from the
 * touch stream. This says only how decisive a drag has to be before the bar moves, which is a
 * design choice with no correct value, and that is why it is in dp rather than pixels.
 */
private val CHROME_DRAG_THRESHOLD: Dp = 12.dp

/**
 * Whether a run of drag movement has asked for the chrome yet, kept out of the modifier so it can
 * be tested.
 *
 * Direction is the finger's: a negative `dy` is a finger travelling up the screen, which drives the
 * document down and means "more to read". Positive is the reader going back up, and a reader going
 * back up wants the controls.
 *
 * @param threshold pixels of travel in one direction before the answer changes.
 */
internal class ChromeDragTracker(private val threshold: Float) {

    /**
     * Travel since the last decision, reset when the finger turns round.
     *
     * A plain field rather than snapshot state: written on every frame of a drag, read nowhere
     * else. Making it observable would invalidate the composition sixty times a second to hold a
     * number nothing draws.
     */
    private var travel = 0f

    /**
     * Takes one movement of the finger.
     *
     * **Sideways movement is not an opinion about the chrome.** The document has scrollers of its
     * own — wide tables, code blocks — and a swipe across one is not a reader asking for anything.
     * Dropping horizontal-dominant movement costs nothing on a vertical scroll, where `dx` is
     * noise, and it is the difference between a table swipe leaving the bar alone and hiding it.
     *
     * @return true to show the chrome, false to hide it, **null for no opinion yet** — which is
     *   most movements, and is why this is not a `Boolean`. A tracker that answered every time
     *   would have the caller re-deciding sixty times a second to say the same thing.
     */
    fun accept(dx: Float, dy: Float): Boolean? {
        if (dy == 0f || abs(dx) > abs(dy)) {
            return null
        }
        // A turn resets the count, so reversing direction costs a whole threshold's worth of drag
        // rather than however much had been banked going the other way.
        travel = if ((travel < 0f) == (dy < 0f)) travel + dy else dy
        return when {
            travel > threshold -> true
            travel < -threshold -> false
            else -> null
        }?.also { travel = 0f }
    }
}

/**
 * Reads the reader's drags on their way to whichever surface is drawing, and tells the caller when
 * one has asked for the chrome.
 *
 * **It observes and consumes nothing**, which is the whole design. The reader's scrolling belongs
 * to the surface underneath — Chromium's in the page, the `ScrollView`'s in raw mode — and both do
 * it better than this app would. What was missing was never the scrolling; it was knowing that a
 * *person* had caused it, and a touch on its way past says so directly.
 *
 * That is why the pass is [PointerEventPass.Initial]: it runs outermost-first, before anything has
 * had a chance to claim the gesture, so what arrives here is every touch over the reader regardless
 * of who ends up handling it. Nothing is consumed, so the surface below sees an untouched stream —
 * taps, long-press selection, flings and a table's own horizontal scrolling all behave exactly as
 * they would with this modifier absent.
 *
 * **The alternative was built and reverted.** `Modifier.scrollable` over a page driven by
 * `scrollBy` — `readium/kotlin-toolkit`'s design — makes the reader a real nested-scroll
 * participant, but it has to claim the gesture, and claiming it breaks the document's own
 * horizontal scrolling: slop is measured per-axis, so a 91%-horizontal swipe was taken as a
 * vertical scroll. Readium can afford that because it owns both axes; a Markdown reader whose
 * tables and code blocks scroll sideways cannot.
 *
 * What is given up by observing rather than owning: there is no fling signal, so the chrome settles
 * on the last decision made while the finger was down. In practice the decision is made long before
 * the finger lifts.
 *
 * @param onVisibilityChange called when a drag has asked for a change.
 */
@Composable
internal fun Modifier.chromeDragObserver(
    onVisibilityChange: (visible: Boolean) -> Unit,
): Modifier {
    val threshold = with(LocalDensity.current) { CHROME_DRAG_THRESHOLD.toPx() }
    val changed by rememberUpdatedState(onVisibilityChange)
    val tracker = remember(threshold) { ChromeDragTracker(threshold) }
    return pointerInput(tracker) {
        awaitEachGesture {
            // The pointer the gesture started with. A second finger arriving mid-scroll is a
            // pinch or a stray palm, and neither is a statement about the chrome.
            var pointer: PointerId? = null
            var down = true
            while (down) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { pointer == null || it.id == pointer }
                if (change == null || !change.pressed) {
                    down = false
                } else {
                    pointer = change.id
                    // `IgnoreConsumed`, because what is being measured is where the finger went,
                    // which is true whoever ends up acting on it.
                    val moved = change.positionChangeIgnoreConsumed()
                    tracker.accept(moved.x, moved.y)?.let(changed)
                }
            }
        }
    }
}
