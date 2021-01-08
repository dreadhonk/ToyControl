package eu.dreadhonk.apps.toycontrol.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * TODO: document your custom view class.
 */
class ValueView : View {
    private var _currentValue: Float = 0.0f
    private var _subValueA: Float = 0.0f
    private var _subValueB: Float = 0.0f
    private var _fillRectInvalidated: Boolean = true
    private var _showSubValueA: Boolean = false
    private var _showSubValueB: Boolean = false

    public var minimumValue: Float = -1.0f
    public var maximumValue: Float = 1.0f
    public var currentValue: Float
        get() {
            return _currentValue
        }
        set(v: Float) {
            _currentValue = v
            invalidateFillRect()
            invalidate()
        }
    public var subValueA: Float
        get() {
            return _subValueA
        }
        set(v: Float) {
            _subValueA = v
            invalidateFillRect()
            invalidate()
        }
    public var subValueB: Float
        get() {
            return _subValueB
        }
        set(v: Float) {
            _subValueB = v
            invalidateFillRect()
            invalidate()
        }
    public var showSubValueA: Boolean
        get() {
            return _showSubValueA
        }
        set(v: Boolean) {
            _showSubValueA = v
            invalidate()
        }
    public var showSubValueB: Boolean
        get() {
            return _showSubValueB
        }
        set(v: Boolean) {
            _showSubValueB = v
            invalidate()
        }

    private var hPadding = 0
    private var vPadding = 0

    private val _density = resources.displayMetrics.density
    private val IDEAL_WIDTH = (20.0f * _density).roundToInt()
    private val LAYOUT_WIDTH = (24.0f * _density).roundToInt()
    private val LAYOUT_HEIGHT = (24.0f * _density).roundToInt()
    private val ABSOLUTE_MINIMUM_HEIGHT = (12.0f * _density).roundToInt()
    private val IDEAL_VPADDING = (6.0f * _density).roundToInt()

    private val SUBBAR_WIDTH = 8.0f * _density

    private val outline = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.0f
    }

    private val fill = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    private val fillSubA = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    private val fillSubB = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private var fillRect = RectF(0.0f, 0.0f, 0.0f, 0.0f)
    private var fillRectSubA = RectF(0.0f, 0.0f, 0.0f, 0.0f)
    private var fillRectSubB = RectF(0.0f, 0.0f, 0.0f, 0.0f)
    private var outlineRect = Rect(0, 0, 0, 0)

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {

    }

    override fun getMinimumWidth(): Int {
        return LAYOUT_WIDTH
    }

    override fun getMinimumHeight(): Int {
        return LAYOUT_WIDTH
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = when (View.MeasureSpec.getMode(heightMeasureSpec)) {
            View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST ->
                View.MeasureSpec.getSize(heightMeasureSpec)
            else -> 100  // does this make sense?
        }

        val width = LAYOUT_WIDTH
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > IDEAL_WIDTH) {
            hPadding = w - IDEAL_WIDTH
        }
        if (h > ABSOLUTE_MINIMUM_HEIGHT) {
            vPadding = Math.min(h - ABSOLUTE_MINIMUM_HEIGHT, IDEAL_VPADDING)
        }
        outlineRect = Rect(hPadding / 2, vPadding / 2, w - hPadding / 2, h - vPadding / 2)
        invalidateFillRect()
    }

    private fun invalidateFillRect() {
        _fillRectInvalidated = true
    }

    private fun layoutFillRect(fillTo: Float, fillBaseline: Float, width: Float, offset: Float): RectF {
        val height = outlineRect.height()

        val ya = height - fillBaseline * height + outlineRect.top
        val yb = height - fillTo * height + outlineRect.top
        val y0 = Math.min(ya, yb)
        val y1 = Math.max(ya, yb)

        return RectF(
            outlineRect.left.toFloat() + offset, y0,
            outlineRect.left.toFloat() + offset + width, y1
        )
    }

    private fun updateFillRect() {
        if (!_fillRectInvalidated) {
            return
        }
        _fillRectInvalidated = false

        // always fill from zero (and clamp to outline rect in the last step)
        val dynamicRange = (maximumValue - minimumValue)
        val fillBaseline = -minimumValue / dynamicRange

        val fillToMain = (_currentValue - minimumValue) / dynamicRange
        val fillToSubA = (_subValueA - minimumValue) / dynamicRange
        val fillToSubB = (_subValueB - minimumValue) / dynamicRange

        val baseWidth = outlineRect.width().toFloat()
        val subSpace = baseWidth - SUBBAR_WIDTH * 2
        val subOffsetA = subSpace / 3.0f
        val subOffsetB = subOffsetA * 2.0f + SUBBAR_WIDTH

        fillRect = layoutFillRect(fillToMain, fillBaseline, baseWidth, 0.0f)
        fillRectSubA = layoutFillRect(fillToSubA, fillBaseline, SUBBAR_WIDTH, subOffsetA)
        fillRectSubB = layoutFillRect(fillToSubB, fillBaseline, SUBBAR_WIDTH, subOffsetB)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateFillRect()
        canvas.drawRect(fillRect, fill)
        if (_showSubValueA) {
            canvas.drawRect(fillRectSubA, fillSubA)
        }
        if (_showSubValueB) {
            canvas.drawRect(fillRectSubB, fillSubB)
        }
        canvas.drawRect(outlineRect, outline)
    }
}
