package com.eterion.glade

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

/** A translated drawer surface: drag updates never measure or rebuild the app grid. */
class DrawerHost(context: Context) : FrameLayout(context) {
    var onProgressChanged: (Float) -> Unit = {}
    var onClosed: () -> Unit = {}
    var canDragDown: (Float, Float) -> Boolean = { _, _ -> true }
    var canDragUp: () -> Boolean = { true }
    var progress = 0f
        private set
    private lateinit var home: View
    private lateinit var drawer: View
    private lateinit var homeContents: View
    private var drawerContents = emptyList<View>()
    private var animator: ValueAnimator? = null
    private val configuration = ViewConfiguration.get(context)
    private val dragSlop = max(2f, configuration.scaledTouchSlop * 0.5f)
    private val homeShift = 48f * resources.displayMetrics.density
    private var velocity: VelocityTracker? = null
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var initialProgress = 0f
    private var activePointer = 0
    private var lastEventTime = -1L
    private var lastEventAction = -1

    fun attach(home: View, drawer: View, homeContents: View, drawerContents: List<View>) {
        this.home = home
        this.drawer = drawer
        this.homeContents = homeContents
        this.drawerContents = drawerContents
        addView(home, LayoutParams(-1, -1))
        addView(drawer, LayoutParams(-1, -1))
        render(0f)
    }

    fun open(animate: Boolean = true) = settle(1f, animate)
    fun close(animate: Boolean = true) = settle(0f, animate)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        render(progress)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            begin(event)
            // Pick up an in-flight animation at its current position, without a jump.
            dragging = progress > 0f && progress < 1f
            return dragging
        }
        track(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !dragging) {
            val index = event.findPointerIndex(activePointer)
            if (index < 0) return false
            val dx = event.getX(index) - downX
            val dy = event.getY(index) - downY
            val vertical = abs(dy) > dragSlop && abs(dy) > abs(dx)
            val opening = initialProgress == 0f && dy < 0f && canDragUp()
            val closing = initialProgress == 1f && dy > 0f && canDragDown(downX, downY)
            if (vertical && (opening || closing)) {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                if (height > 0) render(initialProgress - dy / height)
                return true // Framework sends CANCEL to the button/grid that owned DOWN.
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            recycleVelocity()
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && velocity == null) begin(event)
        track(event)
        val index = event.findPointerIndex(activePointer)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (index >= 0 && height > 0) {
                val dx = event.getX(index) - downX
                val dy = event.getY(index) - downY
                if (!dragging && abs(dy) > dragSlop && abs(dy) > abs(dx)) {
                    dragging = (initialProgress == 0f && dy < 0f && canDragUp()) ||
                        (initialProgress == 1f && dy > 0f && canDragDown(downX, downY))
                }
                if (dragging) render(initialProgress - dy / height)
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == activePointer) {
                val next = if (event.actionIndex == 0) 1 else 0
                activePointer = event.getPointerId(next)
                downX = event.getX(next)
                downY = event.getY(next)
                initialProgress = progress
                velocity?.clear()
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) performClick()
                velocity?.computeCurrentVelocity(1000, configuration.scaledMaximumFlingVelocity.toFloat())
                val speed = velocity?.getYVelocity(activePointer) ?: 0f
                val threshold = max(configuration.scaledMinimumFlingVelocity * 4f, 350f * resources.displayMetrics.density)
                settle(if (DrawerMotion.shouldOpen(progress, speed, threshold)) 1f else 0f, true)
                finishGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                settle(if (initialProgress >= DrawerMotion.OPEN_THRESHOLD) 1f else 0f, true)
                finishGesture()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun begin(event: MotionEvent) {
        animator?.cancel()
        animator = null
        recycleVelocity()
        velocity = VelocityTracker.obtain()
        lastEventTime = -1L
        downX = event.x
        downY = event.y
        activePointer = event.getPointerId(0)
        initialProgress = progress
        dragging = false
        track(event)
    }

    private fun track(event: MotionEvent) {
        if (lastEventTime != event.eventTime || lastEventAction != event.action) {
            velocity?.addMovement(event)
            lastEventTime = event.eventTime
            lastEventAction = event.action
        }
    }

    private fun finishGesture() {
        dragging = false
        recycleVelocity()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun recycleVelocity() { velocity?.recycle(); velocity = null }

    private fun settle(target: Float, animate: Boolean) {
        animator?.cancel()
        animator = null
        if (!animate || height == 0 || progress == target || !ValueAnimator.areAnimatorsEnabled()) {
            render(target)
            if (target == 0f) onClosed()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = (140 + 160 * abs(target - progress)).toLong()
            interpolator = DecelerateInterpolator(1.7f)
            var cancelled = false
            addUpdateListener { render(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        render(target)
                        animator = null
                        if (target == 0f) onClosed()
                    }
                }
            })
            start()
        }
    }

    private fun render(value: Float) {
        progress = value.coerceIn(0f, 1f)
        if (!::drawer.isInitialized) return
        drawer.translationY = (1f - progress) * height
        val surfaceAlpha = DrawerMotion.surfaceAlpha(progress)
        val contentAlpha = DrawerMotion.contentAlpha(progress)
        drawer.alpha = surfaceAlpha
        drawerContents.forEach { it.alpha = contentAlpha }
        homeContents.translationY = -homeShift * progress
        homeContents.alpha = 1f - surfaceAlpha
        drawer.visibility = if (progress == 0f) INVISIBLE else VISIBLE
        home.visibility = if (progress == 1f) INVISIBLE else VISIBLE
        home.importantForAccessibility = if (progress > 0f) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        onProgressChanged(progress)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        recycleVelocity()
        super.onDetachedFromWindow()
    }
}
