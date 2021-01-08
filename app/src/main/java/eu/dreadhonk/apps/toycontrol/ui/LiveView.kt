package eu.dreadhonk.apps.toycontrol.ui

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import eu.dreadhonk.apps.toycontrol.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class LiveView: GLSurfaceView {
    constructor(context: Context): super(context) {
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
    }

    private val _density = resources.displayMetrics.density
    private val ABSOLUTE_MINIMUM_HEIGHT = Math.round(_density * 32)
    private val ABSOLUTE_MINIMUM_WIDTH = Math.round(_density * 32)
    private val PREFERRED_HEIGHT = Math.round(_density * 120)

    private class Trace(
        private val size: Int,
        public var next: Float = 0.0f
    ) {
        private val points = FloatArray(size)
        public val color = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)

        fun shift() {
            for (i in 0 until size - 1) {
                points[i] = points[i+1]
            }
            points[size - 1] = next
        }

        fun fillVertexBuffer(buffer: FloatBuffer) {
            val isize = size
            val fsize = (isize - 1).toFloat()
            buffer.apply {
                for (i in 0 until isize) {
                    val x = i / fsize
                    put(x)
                    put(points[i])
                }
            }
        }
    }

    class Renderer(private val context: Context): GLSurfaceView.Renderer {
        private val backgroundColor: FloatArray = FloatArray(4)
        private val traces = HashMap<Long, Trace>()
        private val POINTS = 200
        private val LINE_WIDTH = 2
        private val buffer = ByteBuffer.allocateDirect(POINTS * 4 * 2).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer()
        }

        private val vertexShaderCode =
            "attribute vec2 vPosition;" +
                    "void main() {" +
                    "  gl_Position = vec4(vPosition * 2.0 - 1.0, 0.0, 1.0);" +
                    "}"

        private val fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}"

        private var traceShaderProgram: Int = 0
        private var traceVertexAttributeLocation: Int = 0
        private var traceColorUniformLocation: Int = 0

        private fun colorIntToFloatArray(cin: Int, fout: FloatArray) {
            fout[0] = Color.red(cin) / 255f
            fout[1] = Color.green(cin) / 255f
            fout[2] = Color.blue(cin) / 255f
            fout[3] = Color.alpha(cin) / 255f
        }

        init {
            colorIntToFloatArray(
                ContextCompat.getColor(context, R.color.liveviewBackground),
                backgroundColor
            )
        }

        private fun loadShader(type: Int, src: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, src)
                GLES20.glCompileShader(shader)
                val resultBuffer = IntBuffer.allocate(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, resultBuffer)
                val log = GLES20.glGetShaderInfoLog(shader)
                if (resultBuffer.get() == GLES20.GL_FALSE) {
                    Log.e("LiveView.Renderer", log)
                    throw RuntimeException("shader compilation failed")
                } else {
                    Log.v("LiveView.Renderer", log)
                }
            }
        }

        private fun buildShaderProgram(vertexSrc: String, fragmentSrc: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
            return GLES20.glCreateProgram().also { prog ->
                GLES20.glAttachShader(prog, vertexShader)
                GLES20.glAttachShader(prog, fragmentShader)
                GLES20.glLinkProgram(prog)
            }
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(
                backgroundColor[0], backgroundColor[1],
                backgroundColor[2], backgroundColor[3]
            )
            traceShaderProgram = buildShaderProgram(vertexShaderCode, fragmentShaderCode)
            traceVertexAttributeLocation = GLES20.glGetAttribLocation(
                traceShaderProgram,
                "vPosition")
            traceColorUniformLocation = GLES20.glGetUniformLocation(
                traceShaderProgram,
                "vColor")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        private fun drawTrace(trace: Trace) {
            GLES20.glUniform4fv(traceColorUniformLocation, 1, trace.color, 0)
            buffer.apply {
                position(0)
                trace.fillVertexBuffer(this)
                position(0)
            }
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, POINTS)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glLineWidth(context.resources.displayMetrics.density * LINE_WIDTH)
            GLES20.glUseProgram(traceShaderProgram)
            GLES20.glEnableVertexAttribArray(traceVertexAttributeLocation)
            GLES20.glVertexAttribPointer(
                traceVertexAttributeLocation,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                buffer
            )
            for (pair in traces) {
                val trace = pair.value
                drawTrace(trace)
            }
            GLES20.glDisableVertexAttribArray(traceVertexAttributeLocation)
        }

        private fun createTrace(traceId: Long): Trace {
            val trace = Trace(POINTS)
            traces[traceId] = trace
            return trace
        }

        private fun autocreateTrace(traceId: Long): Trace {
            return traces[traceId] ?: return createTrace(traceId)
        }

        fun addTrace(traceId: Long, color: Int, initial: Float) {
            val trace = autocreateTrace(traceId)
            trace.next = initial
            colorIntToFloatArray(color, trace.color)
        }

        fun setTraceValue(traceId: Long, value: Float) {
            val trace = traces[traceId]
            if (trace == null) {
                return
            }
            trace.next = value
        }

        fun removeTrace(traceId: Long) {
            traces.remove(traceId)
        }

        fun shift() {
            for (pair in traces) {
                pair.value.shift()
            }
        }
    }

    private var schedule: ScheduledThreadPoolExecutor? = null

    private lateinit var renderer: Renderer

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = PREFERRED_HEIGHT

        val width = when (View.MeasureSpec.getMode(widthMeasureSpec)) {
            View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST ->
                Math.max(ABSOLUTE_MINIMUM_WIDTH, View.MeasureSpec.getSize(widthMeasureSpec))
            else -> ABSOLUTE_MINIMUM_WIDTH
        }

        setMeasuredDimension(width, height)
    }

    init {
        renderer = Renderer(context)

        setEGLContextClientVersion(2)
        setRenderer(renderer)
        // TODO: change this to WHEN_DIRTY once we got a proper loop set up
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun addTrace(traceId: Long, color: Int) {
        queueEvent {
            renderer.addTrace(traceId, color, 0.0f)
        }
    }

    fun setTraceValue(traceId: Long, value: Float) {
        queueEvent {
            renderer.setTraceValue(traceId, value)
        }
    }

    fun removeTrace(traceId: Long) {
        queueEvent {
            renderer.removeTrace(traceId)
        }
    }

    private fun queueUpdate() {
        queueEvent {
            renderer.shift()
            post {
                requestRender()
            }
        }
        post {
            requestRender()
        }
    }

    override fun onResume() {
        super.onResume()
        schedule = ScheduledThreadPoolExecutor(1)
        schedule!!.scheduleAtFixedRate(
            Runnable { queueUpdate() },
            0, 33,
            TimeUnit.MILLISECONDS)
    }

    override fun onPause() {
        schedule?.shutdownNow()
        schedule = null
        super.onPause()
    }
}