package com.example.notebookvectorial.views

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

// Cuadro de Color estilo Photoshop (2D Saturación/Valor y 1D Tono)
class ColorPickerDialog(context: Context, initialColor: Int, val onColorPicked: (Int) -> Unit) {
    private var currentColor = initialColor

    fun show(context: Context) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        // Vista previa del color actual
        val previewView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
            setBackgroundColor(currentColor)
        }

        // Cuadro 2D y Barra 1D
        val svBox = SVBox(context)
        svBox.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600).apply { topMargin = 40 }

        val hueBar = HueBar(context)
        hueBar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120).apply { topMargin = 40 }

        // Extraer HSV inicial
        val hsv = FloatArray(3)
        Color.colorToHSV(currentColor, hsv)
        svBox.setHue(hsv[0])
        svBox.setSatVal(hsv[1], hsv[2])
        hueBar.setHue(hsv[0])

        // Listeners para actualizar todo en tiempo real
        svBox.onColorChanged = { sat, val_ ->
            hsv[1] = sat
            hsv[2] = val_
            currentColor = Color.HSVToColor(hsv)
            previewView.setBackgroundColor(currentColor)
        }

        hueBar.onHueChanged = { hue ->
            hsv[0] = hue
            svBox.setHue(hue)
            currentColor = Color.HSVToColor(hsv)
            previewView.setBackgroundColor(currentColor)
        }

        layout.addView(previewView)
        layout.addView(svBox)
        layout.addView(hueBar)

        AlertDialog.Builder(context)
            .setTitle("Selector de Color")
            .setView(layout)
            .setPositiveButton("Aplicar") { _, _ -> onColorPicked(currentColor) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Cuadro de Saturación (X) y Valor/Brillo (Y)
    class SVBox(context: Context) : View(context) {
        var onColorChanged: ((Float, Float) -> Unit)? = null
        private var currentHue = 0f
        private var currentSat = 1f
        private var currentVal = 1f
        private val paint = Paint()
        private var shader: ComposeShader? = null

        fun setHue(hue: Float) {
            currentHue = hue
            updateShader()
            invalidate()
        }

        fun setSatVal(sat: Float, val_: Float) {
            currentSat = sat
            currentVal = val_
            invalidate()
        }

        private fun updateShader() {
            if (width > 0 && height > 0) {
                val valShader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
                val satShader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f)), Shader.TileMode.CLAMP)
                shader = ComposeShader(valShader, satShader, PorterDuff.Mode.MULTIPLY)
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            updateShader()
        }

        override fun onDraw(canvas: Canvas) {
            shader?.let { paint.shader = it }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            // Dibujar el cursor (circulito)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            val cx = currentSat * width
            val cy = (1f - currentVal) * height

            paint.color = Color.WHITE
            paint.strokeWidth = 4f
            canvas.drawCircle(cx, cy, 20f, paint)

            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            canvas.drawCircle(cx, cy, 22f, paint)

            paint.style = Paint.Style.FILL
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            currentSat = (event.x / width).coerceIn(0f, 1f)
            currentVal = 1f - (event.y / height).coerceIn(0f, 1f)
            onColorChanged?.invoke(currentSat, currentVal)
            invalidate()
            return true
        }
    }

    // Barra de Tono (Arcoíris)
    class HueBar(context: Context) : View(context) {
        var onHueChanged: ((Float) -> Unit)? = null
        private var currentHue = 0f
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            if (paint.shader == null && width > 0) {
                val colors = IntArray(361)
                for (i in 0..360) colors[i] = Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            // Dibujar cursor
            paint.shader = null
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            val cx = (currentHue / 360f) * width
            canvas.drawRect(cx - 8f, 0f, cx + 8f, height.toFloat(), paint)

            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            canvas.drawRect(cx - 10f, 2f, cx + 10f, height.toFloat() - 2f, paint)

            paint.style = Paint.Style.FILL
        }

        fun setHue(hue: Float) {
            currentHue = hue
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            currentHue = ((event.x / width) * 360f).coerceIn(0f, 360f)
            onHueChanged?.invoke(currentHue)
            invalidate()
            return true
        }
    }
}