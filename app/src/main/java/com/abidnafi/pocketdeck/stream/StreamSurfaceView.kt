package com.abidnafi.pocketdeck.stream

import android.content.Context
import android.view.SurfaceView
import kotlin.math.roundToInt

/** Video surface that keeps the stream's aspect ratio (letterboxed inside its parent). */
class StreamSurfaceView(context: Context) : SurfaceView(context) {
    /** width / height of the video; 0 = fill. */
    var aspect: Double = 0.0
        set(value) {
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (aspect <= 0.0) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w == 0 || h == 0) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (w.toDouble() / h > aspect) {
            setMeasuredDimension((h * aspect).roundToInt(), h)
        } else {
            setMeasuredDimension(w, (w / aspect).roundToInt())
        }
    }
}
