package com.example.notebookvectorial.tools

import android.graphics.Path
import android.graphics.PathIterator
import android.graphics.RectF
import android.graphics.Paint
import androidx.annotation.RequiresApi
import com.example.notebookvectorial.serialization.LayerData
import com.example.notebookvectorial.serialization.PathElement
import kotlin.math.hypot

class EraserLogic {

    @RequiresApi(34)
    fun performStrokeEraser(
        startX: Float, startY: Float, endX: Float, endY: Float,
        eraserSize: Float,
        currentLayer: LayerData,
        buildAndroidPathFunc: (List<PathElement>?) -> Path
    ): Boolean {
        if (!currentLayer.visible) return false
        var modified = false

        val eraserCapsulePath = Path()
        val dx = endX - startX
        val dy = endY - startY

        // Fix al bug de la zona de relleno: si no hay movimiento (click puro), forzamos un círculo
        if (hypot(dx, dy) < 0.1f) {
            eraserCapsulePath.addCircle(startX, startY, eraserSize / 2f, Path.Direction.CW)
        } else {
            val segmentPath = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
            val eraserStrokePaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = eraserSize
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            eraserStrokePaint.getFillPath(segmentPath, eraserCapsulePath)
        }

        val eraserBounds = RectF()
        eraserCapsulePath.computeBounds(eraserBounds, true)

        val iterator = currentLayer.items.listIterator(currentLayer.items.size)
        while (iterator.hasPrevious()) {
            val item = iterator.previous()
            if (item.type != "path") continue

            val itemPath = buildAndroidPathFunc(item.path_elements)
            val itemBounds = RectF()
            itemPath.computeBounds(itemBounds, true)

            if (!RectF.intersects(eraserBounds, itemBounds)) continue

            // Resta limpia estilo PyQt6
            val resultPath = Path()
            val hasIntersection = resultPath.op(itemPath, eraserCapsulePath, Path.Op.DIFFERENCE)

            if (!hasIntersection) continue

            modified = true

            if (resultPath.isEmpty) {
                iterator.remove()
            } else {
                item.path_elements = extractPathElementsFromIterator(resultPath)
            }
        }
        return modified
    }

    @RequiresApi(34)
    private fun extractPathElementsFromIterator(path: Path): List<PathElement> {
        val elements = mutableListOf<PathElement>()
        val iterator = path.pathIterator
        val points = FloatArray(8)

        while (iterator.hasNext()) {
            when (iterator.next(points, 0)) {
                PathIterator.VERB_MOVE -> elements.add(PathElement(0, points[0], points[1]))
                PathIterator.VERB_LINE -> elements.add(PathElement(1, points[0], points[1]))
                PathIterator.VERB_QUAD -> {
                    val start = elements.lastOrNull()
                    val startX = start?.x ?: 0f; val startY = start?.y ?: 0f
                    val cp1x = startX + (2f / 3f) * (points[0] - startX)
                    val cp1y = startY + (2f / 3f) * (points[1] - startY)
                    val cp2x = points[2] + (2f / 3f) * (points[0] - points[2])
                    val cp2y = points[3] + (2f / 3f) * (points[1] - points[3])
                    elements.add(PathElement(2, cp1x, cp1y))
                    elements.add(PathElement(2, cp2x, cp2y))
                    elements.add(PathElement(2, points[2], points[3]))
                }
                PathIterator.VERB_CUBIC -> {
                    elements.add(PathElement(2, points[0], points[1]))
                    elements.add(PathElement(2, points[2], points[3]))
                    elements.add(PathElement(2, points[4], points[5]))
                }
                PathIterator.VERB_CLOSE -> {
                    // AQUÍ ESTABA EL ERROR: Asegúrate de que sea t = 3
                    elements.add(PathElement(3, 0f, 0f))
                }
            }
        }
        return elements
    }
}