package com.markreader.ui.zoom

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A FrameLayout that adds pinch-to-zoom, pan, and double-tap-to-reset
 * to its single child (expected to be a ScrollView).
 *
 * Touch handling strategy:
 * - 2+ pointers → intercept for pinch-to-zoom
 * - 1 pointer + zoomed + can pan → intercept for panning
 * - Otherwise → pass through to child (scroll / text selection)
 */
class ZoomableContentLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scaleFactor = 1f
    private var translationXOffset = 0f
    private var translationYOffset = 0f

    private val minScale = 1f
    private val maxScale = 3f

    val currentScale: Float get() = scaleFactor

    // Pan tracking
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    // Used to detect whether we should intercept single-finger drags for panning
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasDecidedIntercept = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val child = getChildAt(0) ?: return false
                val prevScale = scaleFactor
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)

                if (scaleFactor != prevScale) {
                    // Snap to exactly 1x when close enough
                    if (scaleFactor < 1.05f && scaleFactor > minScale) {
                        scaleFactor = minScale
                        translationXOffset = 0f
                        translationYOffset = 0f
                    } else {
                        // Adjust translation to keep the pinch focal point stationary
                        val focusX = detector.focusX
                        val focusY = detector.focusY
                        val childCenterX = child.pivotX + translationXOffset
                        val childCenterY = child.pivotY + translationYOffset
                        val dx = focusX - childCenterX
                        val dy = focusY - childCenterY
                        val scaleChange = scaleFactor / prevScale
                        translationXOffset += dx * (1 - scaleChange)
                        translationYOffset += dy * (1 - scaleChange)
                    }

                    clampTranslation()
                    applyTransform()
                }
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scaleFactor > minScale) {
                    animateResetZoom()
                    return true
                }
                return false
            }
        }
    )

    init {
        // Prevent ScaleGestureDetector from consuming small movements early
        scaleDetector.isQuickScaleEnabled = false
    }

    /**
     * Ignore disallow-intercept requests from children (e.g. HorizontalScrollView).
     * We must always be able to detect 2-finger pinch gestures.
     * Single-finger events at 1x still pass through because onInterceptTouchEvent
     * returns false for them.
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Intentionally do not call super — we always need onInterceptTouchEvent
        // to fire so we can detect pinch-to-zoom.
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val pointerCount = ev.pointerCount

        // Always intercept multi-touch for pinch
        if (pointerCount >= 2) {
            hasDecidedIntercept = false
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.x
                initialTouchY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                hasDecidedIntercept = false
                isPanning = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleFactor > minScale && !hasDecidedIntercept) {
                    val dx = abs(ev.x - initialTouchX)
                    val dy = abs(ev.y - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        hasDecidedIntercept = true
                        // Intercept horizontal drags when zoomed (scrollView doesn't handle these)
                        if (dx > dy) {
                            return true
                        }
                        // For vertical drags, check if child scrollView is at its scroll boundary
                        val child = getChildAt(0) ?: return false
                        val scrollY = if (child is android.widget.ScrollView) child.scrollY else 0
                        val maxScrollY = if (child is android.widget.ScrollView) {
                            val innerChild = child.getChildAt(0)
                            if (innerChild != null) innerChild.height - child.height else 0
                        } else 0

                        val goingUp = ev.y - initialTouchY > 0
                        val goingDown = ev.y - initialTouchY < 0
                        // Pan if at scroll edge and translation room exists
                        if ((goingUp && scrollY <= 0 && translationYOffset < getMaxTranslationY()) ||
                            (goingDown && scrollY >= maxScrollY && translationYOffset > -getMaxTranslationY())
                        ) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Feed scale detector
        scaleDetector.onTouchEvent(ev)
        // Feed gesture detector for double-tap
        gestureDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                lastTouchY = ev.y
                isPanning = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Reset last touch to current to avoid jump when second finger lands
                lastTouchX = ev.x
                lastTouchY = ev.y
                isPanning = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && ev.pointerCount == 1 && scaleFactor > minScale) {
                    val dx = ev.x - lastTouchX
                    val dy = ev.y - lastTouchY
                    translationXOffset += dx
                    translationYOffset += dy
                    clampTranslation()
                    applyTransform()
                    isPanning = true
                }
                lastTouchX = ev.x
                lastTouchY = ev.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                hasDecidedIntercept = false
            }
        }
        return true
    }

    private fun getMaxTranslationX(): Float {
        val child = getChildAt(0) ?: return 0f
        val scaledWidth = child.width * scaleFactor
        val excess = (scaledWidth - width) / 2f
        return max(0f, excess)
    }

    private fun getMaxTranslationY(): Float {
        val child = getChildAt(0) ?: return 0f
        val scaledHeight = child.height * scaleFactor
        val excess = (scaledHeight - height) / 2f
        return max(0f, excess)
    }

    private fun clampTranslation() {
        val maxTx = getMaxTranslationX()
        val maxTy = getMaxTranslationY()
        translationXOffset = translationXOffset.coerceIn(-maxTx, maxTx)
        translationYOffset = translationYOffset.coerceIn(-maxTy, maxTy)
    }

    private fun applyTransform() {
        val child = getChildAt(0) ?: return
        child.pivotX = child.width / 2f
        child.pivotY = child.height / 2f
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        child.translationX = translationXOffset
        child.translationY = translationYOffset
    }

    /**
     * Animate zoom back to 1x with a smooth decelerate animation.
     */
    fun resetZoom() {
        animateResetZoom()
    }

    /**
     * Reset only the pan offsets (keep current zoom). Useful after scroll-to navigation.
     */
    fun resetPan() {
        translationXOffset = 0f
        translationYOffset = 0f
        applyTransform()
    }

    private fun animateResetZoom() {
        val startScale = scaleFactor
        val startTx = translationXOffset
        val startTy = translationYOffset

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                scaleFactor = startScale + (minScale - startScale) * fraction
                translationXOffset = startTx * (1f - fraction)
                translationYOffset = startTy * (1f - fraction)
                applyTransform()
            }
            start()
        }
    }
}
