package eu.dreadhonk.apps.toycontrol.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import eu.dreadhonk.apps.toycontrol.R
import eu.dreadhonk.apps.toycontrol.control.SimpleControlMode

class IntensityControl: LinearLayout {
    interface IntensityControlListener {
        fun onManualValueChange(newValue: Float)
        fun onModeChange(newMode: SimpleControlMode)
    }

    constructor(context: Context): super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int): super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    private lateinit var mSlider: IntensitySliderView
    private lateinit var mFloatButton: Switch
    private lateinit var mMode: Spinner
    private lateinit var mDeviceLabel: TextView
    private lateinit var mDevicePort: TextView

    public var listener: IntensityControlListener? = null

    private data class ControlModeItem(
        val label: String,
        val drawableId: Int,
        val mode: SimpleControlMode
    )

    private class ControlModeAdapter(context: Context,
                                     itemLayoutId: Int,
                                     iconViewId: Int,
                                     textViewId: Int,
                                     items: MutableList<ControlModeItem>
    ): ArrayAdapter<ControlModeItem>(context, itemLayoutId, textViewId, items) {

        private val mItemLayoutId = itemLayoutId
        private var mDropDownItemLayoutId = itemLayoutId
        private val mTextViewId = textViewId
        private val mIconViewId = iconViewId
        private val mInflater = LayoutInflater.from(context)

        private fun createViewFromResource(
            inflater: LayoutInflater,
            viewResource: Int,
            item: ControlModeItem,
            convertView: View?,
            parent: ViewGroup): View
        {
            val view = convertView ?: inflater.inflate(viewResource, parent, false)

            val textView = view.findViewById<TextView>(mTextViewId)
            if (textView != null) {
                textView.text = item.label
            }

            val iconView = view.findViewById<ImageView>(mIconViewId)
            iconView?.setImageResource(item.drawableId)

            return view
        }

        override fun setDropDownViewResource(resource: Int) {
            super.setDropDownViewResource(resource)
            mDropDownItemLayoutId = resource
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createViewFromResource(
                mInflater,
                mItemLayoutId,
                getItem(position)!!,
                convertView,
                parent
            )
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createViewFromResource(
                mInflater,
                mDropDownItemLayoutId,
                getItem(position)!!,
                convertView,
                parent
            )
        }

        public fun getPositionByValue(mode: SimpleControlMode): Int {
            for (i in 0 until count) {
                val item = getItem(i) as ControlModeItem
                if (item.mode == mode) {
                    return i
                }
            }
            return -1
        }
    }

    private val mEventForwarder = object: IntensitySliderView.OnValueChangeListener {
        override fun onValueChange(newValue: Float) {
            val fwdTo = listener
            if (fwdTo == null) {
                return
            }
            fwdTo.onManualValueChange(newValue)
        }
    }

    private val mItemSelectedListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            val fwdTo = listener
            if (fwdTo == null) {
                return
            }

            val item = adapter.getItem(position)!!
            fwdTo.onModeChange(item.mode)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private lateinit var adapter: ControlModeAdapter

    public var deviceName: CharSequence
        get() {
            return mDeviceLabel.text
        }
        set(v: CharSequence) {
            mDeviceLabel.text = v
        }

    public var devicePort: CharSequence
        get() {
            return mDevicePort.text
        }
        set(v: CharSequence) {
            mDevicePort.text = v
        }

    public var mode: SimpleControlMode
        get() {
            return adapter.getItem(mMode.selectedItemPosition)!!.mode
        }
        set(mode: SimpleControlMode) {
            mMode.setSelection(adapter.getPositionByValue(mode))
        }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        orientation = LinearLayout.VERTICAL

        val topLayout = LinearLayout(context)
        topLayout.orientation = LinearLayout.HORIZONTAL

        mDeviceLabel = TextView(context)
        mDeviceLabel.text = "dev"
        mDevicePort = TextView(context)
        mDevicePort.text = "M1"

        topLayout.addView(mDeviceLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1.0f))
        topLayout.addView(mDevicePort, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0.0f))

        mMode = Spinner(context)
        mMode.setPadding(0, 0, 0, 0)
        mMode.onItemSelectedListener = mItemSelectedListener

        val modeLabelArray = resources.getStringArray(R.array.control_simple_inputs_list)
        val modeArray = arrayListOf(
            ControlModeItem(
                modeLabelArray[0],
                R.drawable.control_manual,
                SimpleControlMode.MANUAL
            ),
            ControlModeItem(
                modeLabelArray[1],
                R.drawable.control_gravity,
                SimpleControlMode.GRAVITY_X
            ),
            ControlModeItem(
                modeLabelArray[2],
                R.drawable.control_gravity,
                SimpleControlMode.GRAVITY_Y
            ),
            ControlModeItem(
                modeLabelArray[3],
                R.drawable.control_gravity,
                SimpleControlMode.GRAVITY_Z
            ),
            ControlModeItem(
                modeLabelArray[4],
                R.drawable.control_shake,
                SimpleControlMode.SHAKE
            )
        )

        adapter = ControlModeAdapter(
            context,
            R.layout.control_spinner_front_item,
            R.id.icon,
            R.id.value,
            modeArray
        ).also { adapter ->
            adapter.setDropDownViewResource(R.layout.control_spinner_item)
            mMode.adapter = adapter
        }

        mSlider = IntensitySliderView(context)
        mSlider.setOnValueChangedListener(mEventForwarder)
        mFloatButton = Switch(context)

        addView(topLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0.0f))
        addView(mMode, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0.0f))
        addView(mSlider, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f))
        addView(mFloatButton, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0.0f))

        mFloatButton.setOnClickListener {
            mSlider.float = mFloatButton.isChecked
        }
    }
}