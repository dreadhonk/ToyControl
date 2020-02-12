package eu.dreadhonk.apps.toycontrol.control

import kotlin.math.max
import kotlin.math.min

class MathUtil {
    companion object {
        fun clampNorm(v: Float): Float {
            return max(min(v, 1.0f), 0.0f)
        }

        fun length(vec: FloatArray): Float {
            var agg = 0.0;
            for (v in vec) {
                agg += (v*v).toDouble();
            }
            return Math.sqrt(agg).toFloat()
        }

        fun normalise(vec: FloatArray, cutoff: Float = 0.0f): FloatArray {
            val len = length(vec)
            if (len <= cutoff) {
                return FloatArray(vec.size) { 0.0f }
            }

            val out = FloatArray(vec.size)
            for (i in vec.indices) {
                out[i] = vec[i] / len
            }
            return out
        }
    }
}