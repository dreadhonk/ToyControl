package eu.dreadhonk.apps.toycontrol.ui

import android.R
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.AutoCompleteTextView
import kotlin.math.roundToInt

class IntensitySliderView: View {
    interface OnValueChangeListener {
        fun onValueChange(newValue: Float)
    }

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

    private var mOnValueChangeListener: OnValueChangeListener? = null

    fun setOnValueChangedListener(newListener: OnValueChangeListener) {
        mOnValueChangeListener = newListener
    }

    private fun valueChanged(newValue: Float) {
        val listener = mOnValueChangeListener
        if (listener == null) {
            return
        }
        listener.onValueChange(newValue)
    }

    private val _density = resources.displayMetrics.density
    private val IDEAL_WIDTH = (40.0f * _density).roundToInt()
    private val LAYOUT_WIDTH = (48.0f * _density).roundToInt()
    private val ABSOLUTE_MINIMUM_HEIGHT = (40.0f * _density).roundToInt()

    private val KNOB_WIDTH = IDEAL_WIDTH.toFloat() * 0.8f
    private val SLOT_WIDTH = KNOB_WIDTH.toFloat() / 8.0f
    private val KNOB_HEIGHT = IDEAL_WIDTH.toFloat()
    private val KNOB_GRIP_LINES = 3
    private val KNOB_GRIP_HEIGHT = KNOB_HEIGHT * 0.4f
    private val KNOB_GRIP_WIDTH = KNOB_WIDTH * 0.6f
    private val KNOB_GRIP_LINE_WIDTH = KNOB_GRIP_HEIGHT / 15.0f

    private var mCurrentValue = 0.0f
    private var mFloat = false
    private var mHasTouch = false

    public var currentValue: Float
        get() {
            return mCurrentValue
        }
        set(v: Float) {
            mCurrentValue = Math.max(0.0f, Math.min(1.0f, v))
            mMetricsInvalidated = true
            invalidate()
            valueChanged(mCurrentValue)
        }

    public var float: Boolean
        get() {
            return mFloat
        }
        set(v: Boolean) {
            mFloat = v
            if (!mFloat && !mHasTouch) {
                currentValue = 0.0f
            }
        }

    private val mSliderSlotPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL_AND_STROKE
    }

    private val mKnobFill = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
    }

    private val mKnobBorder = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.0f
    }

    private val mKnobGrip = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = KNOB_GRIP_LINE_WIDTH
    }

    private var mSliderSlotRect = RectF()
    private var mKnobRect = Rect()
    private var mGripRect = Rect()
    private var mMetricsInvalidated = true
    private var mMinY = 0.0f
    private var mMaxY = 0.0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = when (View.MeasureSpec.getMode(heightMeasureSpec)) {
            View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST ->
                View.MeasureSpec.getSize(heightMeasureSpec)
            else -> ABSOLUTE_MINIMUM_HEIGHT
        }

        val width = when (View.MeasureSpec.getMode(widthMeasureSpec)) {
            View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST ->
                Math.max(LAYOUT_WIDTH, View.MeasureSpec.getSize(widthMeasureSpec))
            else -> LAYOUT_WIDTH
        }

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mMetricsInvalidated = true
        invalidate()
    }

    private fun updateMetrics() {
        if (!mMetricsInvalidated) {
            return
        }
        mMetricsInvalidated = false

        val width = this.width
        val height = this.height
        val slotRadius = SLOT_WIDTH / 2.0f

        val cX = width.toFloat() / 2.0f
        mMinY = height.toFloat() - KNOB_HEIGHT / 2.0f
        mMaxY = KNOB_HEIGHT / 2.0f
        val cY = mCurrentValue * (mMaxY - mMinY) + mMinY

        mSliderSlotRect.set(
            cX - slotRadius,
            mMaxY - slotRadius,
            cX + slotRadius,
            mMinY + slotRadius
        )

        mKnobRect.set(
            (cX - KNOB_WIDTH / 2.0f).roundToInt(),
            (cY - KNOB_HEIGHT / 2.0f).roundToInt(),
            (cX + KNOB_WIDTH / 2.0f).roundToInt(),
            (cY + KNOB_HEIGHT / 2.0f).roundToInt()
        )

        val gripHorizMargin = ((KNOB_WIDTH - KNOB_GRIP_WIDTH) / 2.0f).roundToInt()
        val gripVerticalMargin = ((KNOB_HEIGHT - KNOB_GRIP_HEIGHT) / 2.0f).roundToInt()

        mGripRect.set(
            mKnobRect.left + gripHorizMargin,
            mKnobRect.top + gripVerticalMargin,
            mKnobRect.right - gripHorizMargin,
            mKnobRect.bottom - gripVerticalMargin
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateMetrics()
        canvas.drawRoundRect(
            mSliderSlotRect,
            SLOT_WIDTH / 2.5f,
            SLOT_WIDTH / 2.5f,
            mSliderSlotPaint
        )
        canvas.drawRect(mKnobRect, mKnobFill)
        canvas.drawRect(mKnobRect, mKnobBorder)

        val gripLineSpacing = ((mGripRect.bottom - mGripRect.top) / (KNOB_GRIP_LINES - 1).toFloat()).roundToInt()
        val gripX0 = mGripRect.left.toFloat()
        val gripX1 = mGripRect.right.toFloat()
        for (i in 0 until KNOB_GRIP_LINES) {
            val gripY = (mGripRect.top + gripLineSpacing * i).toFloat()
            canvas.drawLine(gripX0, gripY, gripX1, gripY, mKnobGrip)
        }
    }

    private var mMoveOffset = 0.0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ypos = event.getY(0)

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                updateMetrics()
                mHasTouch = true
                mMoveOffset = if (mFloat) {
                    // in case of non-float, we have to decide whether the user touched the knob
                    // for fine adjustment or whether a jump is in order
                    val knobY = (mKnobRect.bottom - mKnobRect.top) / 2.0f + mKnobRect.top
                    val offset = knobY - ypos
                    Log.v("IntensitySliderView", "knobY = ${knobY}, ypos = ${ypos}, offset = ${offset}, half_height = ${KNOB_HEIGHT / 2.0f}")
                    if (Math.abs(offset) < KNOB_HEIGHT / 2.0f) {
                        // user touched the knob, use the offset
                        offset
                    } else {
                        0.0f
                    }
                } else {
                    0.0f
                }
                val correctedYpos = ypos.toFloat() + mMoveOffset
                val scaledY = (correctedYpos.toFloat() - mMinY) / (mMaxY - mMinY)
                currentValue = scaledY
                true
            }

            MotionEvent.ACTION_MOVE -> {
                updateMetrics()
                val correctedYpos = ypos.toFloat() + mMoveOffset
                val scaledY = (correctedYpos.toFloat() - mMinY) / (mMaxY - mMinY)
                currentValue = scaledY
                true
            }

            MotionEvent.ACTION_UP -> {
                mHasTouch = false
                if (!mFloat) {
                    currentValue = 0.0f
                }
                true
            }
            else -> super.onTouchEvent(event)
        }
    }
}