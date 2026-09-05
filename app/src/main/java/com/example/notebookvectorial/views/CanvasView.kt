package com.example.notebookvectorial.views

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.notebookvectorial.serialization.ItemData
import com.example.notebookvectorial.serialization.LayerData
import com.example.notebookvectorial.serialization.PathElement
import com.example.notebookvectorial.tools.PenLogic
import com.example.notebookvectorial.tools.EraserLogic
import com.example.notebookvectorial.tools.UndoManager
import com.example.notebookvectorial.tools.ZoomLogic
import kotlin.math.max
import kotlin.math.min
import kotlin.math.hypot

class CanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ToolMode { LAPIZ, BORRADOR, MANO, ZOOM }

    private val layers = mutableListOf<LayerData>()
    private var activeLayer = 0
    private val penLogic = PenLogic()
    private val eraserLogic = EraserLogic()

    val undoManager = UndoManager()

    // Módulos de Lupa integrados
    val zoomLogic = ZoomLogic()
    var onZoomChanged: ((Float) -> Unit)? = null
    var onCameraMoved: (() -> Unit)? = null // Para guardar posición al usar la mano

    var isAltPressed = false
    var isPinchZoomDisabled = false

    var currentTool = ToolMode.LAPIZ
    var currentColor = Color.BLACK
    var baseStrokeWidth = 4f

    // Goma
    var eraserSize = 20f
    var useDynamicEraser = false
    private var currentEraserSize = 20f // Tamaño que realmente se dibuja (puede variar por la velocidad)
    private var lastEraserTime = 0L
    private val eraserCursorPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.DKGRAY
        isAntiAlias = true
    }

    var usePressure = true

    var offsetX = 0f
    var offsetY = 0f
    var scaleFactor = 0.5f

    // Control de la Mano
    private var isPanStarted = false
    private var lastPanX = 0f
    private var lastPanY = 0f

    // Variables de Zoom por Caja
    private var zoomStartX = 0f
    private var zoomStartY = 0f
    private var zoomRect: RectF? = null

    // Cursor flotante de la goma
    private var hoverEraserX = -1f
    private var hoverEraserY = -1f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(0.01f, 50.0f)
            val focusX = detector.focusX
            val focusY = detector.focusY

            val sceneFocusX = (focusX - offsetX) / scaleFactor
            val sceneFocusY = (focusY - offsetY) / scaleFactor

            offsetX = focusX - sceneFocusX * newScale
            offsetY = focusY - sceneFocusY * newScale
            scaleFactor = newScale

            onZoomChanged?.invoke(scaleFactor)
            invalidate()
            return true
        }
    })

    // Variables de trazado
    private val currentPoints = mutableListOf<PenLogic.PointPres>()
    private var tempAndroidPath = Path()
    private val activePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    // Variables de la Goma
    private var lastEraserX = 0f
    private var lastEraserY = 0f
    private var isErasing = false
    private val eraserTrailPath = Path()
    private val eraserPreviewPaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND; color = Color.argb(100, 255, 255, 255)
    }

    // Constantes VectorScene PC
    private val ANCHO_LIENZO = 3369f
    private val ALTO_LIENZO = 2382f
    private val appBgColor = Color.parseColor("#444444")
    private val bgPaint = Paint().apply { color = Color.parseColor("#e0e0e0") }
    private val paperPaint = Paint().apply { color = Color.WHITE }
    private val gridPaint = Paint().apply { color = Color.parseColor("#dce6f0"); strokeWidth = 2f }
    private val redMarginPaint = Paint().apply { color = Color.parseColor("#ff6464"); strokeWidth = 2f }
    private val grayMarginPaint = Paint().apply { color = Color.parseColor("#c8c8c8"); strokeWidth = 2f }

    init {
        layers.add(LayerData("Capa 1", true, mutableListOf()))
        saveUndoState()

        post {
            invalidate()
        }
    }

    fun setCameraPosition(x: Float, y: Float, scale: Float) {
        offsetX = x
        offsetY = y
        scaleFactor = scale.coerceIn(0.01f, 50.0f)
        invalidate()
    }

    fun zoomInCenter() {
        val targetScale = (scaleFactor * 1.5f).coerceIn(0.01f, 50.0f)
        applyZoomToCenter(targetScale)
    }

    fun zoomOutCenter() {
        val targetScale = (scaleFactor / 1.5f).coerceIn(0.01f, 50.0f)
        applyZoomToCenter(targetScale)
    }

    private fun applyZoomToCenter(targetScale: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val sceneX = (centerX - offsetX) / scaleFactor
        val sceneY = (centerY - offsetY) / scaleFactor

        offsetX = centerX - sceneX * targetScale
        offsetY = centerY - sceneY * targetScale
        scaleFactor = targetScale
        invalidate()
    }

    fun saveUndoState() {
        undoManager.saveState(exportCurrentPage())
    }

    // Esta función permite cerrar el trazo en memoria si el usuario cambia
    // súbitamente de herramienta en medio del trazo (ej. apretar barra espaciadora)
    fun forceCommitCurrentStroke() {
        if (currentPoints.isNotEmpty()) {
            // Generar el path normalmente, sin aplanar
            val finalPath = penLogic.generarPathPresion(currentPoints, baseStrokeWidth, suavizar = true)
            val hexColor = String.format("#%06X", 0xFFFFFF and currentColor)

            layers[activeLayer].items.add(ItemData(
                type = "path", pos_x = 0f, pos_y = 0f,
                path_elements = finalPath.elements,
                has_pen = false, has_fill = true, fill_color = hexColor
            ))
            currentPoints.clear()
            tempAndroidPath.reset()
            saveUndoState()

        }
        isErasing = false
        eraserTrailPath.reset()
        hoverEraserX = -1f
        hoverEraserY = -1f
        isPanStarted = false
        invalidate()
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (currentTool == ToolMode.BORRADOR) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_EXIT -> {
                    hoverEraserX = -1f
                    hoverEraserY = -1f
                }
                else -> {
                    hoverEraserX = (event.x - offsetX) / scaleFactor
                    hoverEraserY = (event.y - offsetY) / scaleFactor
                    currentEraserSize = eraserSize
                }
            }
            invalidate()
            return true
        }
        return super.onHoverEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 1. Zoom Multi-táctil nativo
        if (currentTool == ToolMode.ZOOM && !isPinchZoomDisabled) {
            scaleDetector.onTouchEvent(event)
        }

        // 2. Palm Rejection estricto
        if (event.pointerCount > 1) return true

        val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        if (currentTool == ToolMode.LAPIZ && !isStylus) return false

        val sceneX = (event.x - offsetX) / scaleFactor
        val sceneY = (event.y - offsetY) / scaleFactor

        // Mantener actualizado el cursor del borrador siempre
        if (currentTool == ToolMode.BORRADOR) {
            hoverEraserX = sceneX
            hoverEraserY = sceneY
        }

        when (currentTool) {
            ToolMode.LAPIZ -> handlePenTouch(event, sceneX, sceneY, isStylus)
            ToolMode.BORRADOR -> handleEraserTouch(event, sceneX, sceneY)
            ToolMode.MANO -> handlePanTouch(event)
            ToolMode.ZOOM -> handleZoomTouch(event, sceneX, sceneY)
        }
        return true
    }

    private fun handlePanTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastPanX = event.x
                lastPanY = event.y
                isPanStarted = true
            }
            MotionEvent.ACTION_MOVE -> {
                // FIX BUG DE SALTO: Si cambiamos a Mano a mitad de un toque,
                // evitamos usar el lastPanX viejo (que causaba el tirón)
                if (!isPanStarted) {
                    lastPanX = event.x
                    lastPanY = event.y
                    isPanStarted = true
                    return
                }

                offsetX += event.x - lastPanX
                offsetY += event.y - lastPanY
                lastPanX = event.x
                lastPanY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanStarted = false
                onCameraMoved?.invoke()
            }
        }
    }

    private fun handleZoomTouch(event: MotionEvent, sceneX: Float, sceneY: Float) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                zoomStartX = sceneX
                zoomStartY = sceneY
                zoomRect = RectF(sceneX, sceneY, sceneX, sceneY)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                zoomRect = RectF(
                    min(zoomStartX, sceneX), min(zoomStartY, sceneY),
                    max(zoomStartX, sceneX), max(zoomStartY, sceneY)
                )
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val rect = zoomRect
                if (rect != null) {
                    val isClick = rect.width() * scaleFactor < 30f || rect.height() * scaleFactor < 30f

                    if (isClick) {
                        val zoomIn = !isAltPressed
                        val (newOffsetX, newOffsetY, newScale) = zoomLogic.calculateClickZoom(
                            sceneX, sceneY, scaleFactor, event.x, event.y, zoomIn
                        )
                        offsetX = newOffsetX
                        offsetY = newOffsetY
                        scaleFactor = newScale
                    } else {
                        if (isAltPressed) {
                            val ratio = min(width / (rect.width() * scaleFactor), height / (rect.height() * scaleFactor))
                            val targetScale = (scaleFactor / ratio).coerceIn(0.01f, 50.0f)

                            val centerX = event.x
                            val centerY = event.y
                            val sX = (centerX - offsetX) / scaleFactor
                            val sY = (centerY - offsetY) / scaleFactor

                            offsetX = centerX - sX * targetScale
                            offsetY = centerY - sY * targetScale
                            scaleFactor = targetScale

                        } else {
                            val (newOffsetX, newOffsetY, newScale) = zoomLogic.calculateBoxZoom(rect, width, height)
                            offsetX = newOffsetX
                            offsetY = newOffsetY
                            scaleFactor = newScale
                        }
                    }
                    onZoomChanged?.invoke(scaleFactor)
                }
                zoomRect = null
                invalidate()
            }
        }
    }

    private fun handlePenTouch(event: MotionEvent, sceneX: Float, sceneY: Float, isStylus: Boolean) {
        val pressure = if (usePressure && isStylus) {
            0.25f + (Math.pow(event.pressure.toDouble(), 0.6) * 0.75f).toFloat()
        } else { 1.0f }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentPoints.add(PenLogic.PointPres(PointF(sceneX, sceneY), pressure))
                activePaint.color = currentColor
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                currentPoints.add(PenLogic.PointPres(PointF(sceneX, sceneY), pressure))
                tempAndroidPath = penLogic.generarPathPresion(currentPoints, baseStrokeWidth, suavizar = false).androidPath
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                forceCommitCurrentStroke()
            }
        }
    }

    private fun handleEraserTouch(event: MotionEvent, sceneX: Float, sceneY: Float) {
        val currentTime = System.currentTimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isErasing = true
                lastEraserX = sceneX
                lastEraserY = sceneY
                lastEraserTime = currentTime
                currentEraserSize = eraserSize
                eraserTrailPath.reset()
                eraserTrailPath.moveTo(sceneX, sceneY)

                if (Build.VERSION.SDK_INT >= 34) {
                    eraserLogic.performStrokeEraser(sceneX, sceneY, sceneX, sceneY, currentEraserSize, layers[activeLayer], ::buildAndroidPathFromElements)
                }
                invalidate() // FIX: Invalidate SIEMPRE para que el cursor no se congele
            }
            MotionEvent.ACTION_MOVE -> {
                if (isErasing) {
                    if (useDynamicEraser) {
                        val dx = sceneX - lastEraserX
                        val dy = sceneY - lastEraserY
                        val distance = hypot(dx, dy)
                        val dt = currentTime - lastEraserTime

                        if (dt > 0) {
                            val speed = distance / dt
                            val targetSize = eraserSize + (speed * 25f)
                            currentEraserSize = currentEraserSize * 0.7f + targetSize * 0.3f
                            currentEraserSize = min(currentEraserSize, eraserSize * 5f)
                        }
                    } else {
                        currentEraserSize = eraserSize
                    }

                    eraserTrailPath.lineTo(sceneX, sceneY)
                    if (Build.VERSION.SDK_INT >= 34) {
                        eraserLogic.performStrokeEraser(lastEraserX, lastEraserY, sceneX, sceneY, currentEraserSize, layers[activeLayer], ::buildAndroidPathFromElements)
                    }
                    lastEraserX = sceneX
                    lastEraserY = sceneY
                    lastEraserTime = currentTime

                    invalidate() // FIX: Invalidate SIEMPRE para que el cursor no se congele
                }
            }
            MotionEvent.ACTION_UP -> {
                isErasing = false
                eraserTrailPath.reset()
                saveUndoState()
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Dibujar la "Mesa de Trabajo" (Fuera del papel)
        canvas.drawColor(appBgColor)

        canvas.save()

        // 2. Aplicar Matrices de Cámara
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)

        // 3. RECORTAR: A partir de aquí, es imposible dibujar fuera de la hoja 3369x2382
        val paperRect = RectF(0f, 0f, ANCHO_LIENZO, ALTO_LIENZO)
        canvas.clipRect(paperRect)

        // 4. Dibujar estructura base (Afectada por el recorte)
        canvas.drawRect(paperRect, paperPaint)

        // Grid
        for (x in 0..ANCHO_LIENZO.toInt() step 75) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), ALTO_LIENZO, gridPaint)
        for (y in 0..ALTO_LIENZO.toInt() step 75) canvas.drawLine(0f, y.toFloat(), ANCHO_LIENZO, y.toFloat(), gridPaint)

        // Margins
        canvas.drawLine(60f, 0f, 60f, ALTO_LIENZO, redMarginPaint)
        canvas.drawLine(1700f, 0f, 1700f, ALTO_LIENZO, redMarginPaint)
        canvas.drawLine(0f, ALTO_LIENZO - 100f, ANCHO_LIENZO, ALTO_LIENZO - 100f, grayMarginPaint)

        // 5. Dibujar capas de Vectores
        val renderPaint = Paint().apply { isAntiAlias = true }
        for (layer in layers) {
            if (!layer.visible) continue
            for (item in layer.items) {
                if (item.type == "path") {
                    val path = buildAndroidPathFromElements(item.path_elements)
                    if (item.has_fill) {
                        renderPaint.style = Paint.Style.FILL
                        renderPaint.color = try { Color.parseColor(item.fill_color ?: "#000000") } catch (e: Exception) { Color.BLACK }
                        canvas.drawPath(path, renderPaint)
                    }
                }
            }
        }

        // 6. Trazo actual
        if (currentPoints.isNotEmpty()) canvas.drawPath(tempAndroidPath, activePaint)

        // 7. Trail y cursor de goma actual
        if (currentTool == ToolMode.BORRADOR) {
            if (isErasing) {
                eraserPreviewPaint.strokeWidth = currentEraserSize
                canvas.drawPath(eraserTrailPath, eraserPreviewPaint)
            }

            // Dibujar el círculo del cursor (diámetro)
            if (hoverEraserX >= 0 && hoverEraserY >= 0) {
                eraserCursorPaint.strokeWidth = 2f / scaleFactor
                canvas.drawCircle(hoverEraserX, hoverEraserY, currentEraserSize / 2f, eraserCursorPaint)
            }
        }

        // 8. Caja de Lupa interactiva
        if (currentTool == ToolMode.ZOOM && zoomRect != null) {
            val dashPaint = Paint().apply {
                style = Paint.Style.STROKE
                color = if (isAltPressed) Color.RED else Color.BLUE
                strokeWidth = 3f / scaleFactor
                pathEffect = DashPathEffect(floatArrayOf(15f / scaleFactor, 15f / scaleFactor), 0f)
            }
            canvas.drawRect(zoomRect!!, dashPaint)
        }

        canvas.restore()
    }

    private fun buildAndroidPathFromElements(elements: List<PathElement>?): Path {
        // 1. REVERTIMOS A WINDING: Mantiene el centro del "8" sólido en el lápiz
        val path = Path().apply { fillType = Path.FillType.WINDING }
        if (elements == null) return path
        var i = 0
        while (i < elements.size) {
            val e = elements[i]
            when (e.t) {
                0 -> { path.moveTo(e.x, e.y); i++ }
                1 -> { path.lineTo(e.x, e.y); i++ }
                2 -> {
                    if (i + 2 < elements.size) {
                        path.cubicTo(e.x, e.y, elements[i + 1].x, elements[i + 1].y, elements[i + 2].x, elements[i + 2].y)
                        i += 3
                    } else i++
                }
                3 -> {
                    // 2. FUNDAMENTAL: Cierra los sub-paths para que los huecos de la goma existan
                    path.close()
                    i++
                }
                else -> i++
            }
        }
        return path
    }
    fun exportCurrentPage() = layers.map { l -> l.copy(items = l.items.map { it.copy() }.toMutableList()) }

    fun loadPageData(newLayers: List<LayerData>) {
        layers.clear()
        layers.addAll(newLayers.map { l -> l.copy(items = l.items.map { it.copy() }.toMutableList()) })
        activeLayer = 0
        undoManager.clear()
        saveUndoState()
        invalidate()
    }

    fun restoreUndoState(restoredLayers: List<LayerData>) {
        layers.clear()
        layers.addAll(restoredLayers.map { l -> l.copy(items = l.items.map { it.copy() }.toMutableList()) })
        invalidate()
    }
}