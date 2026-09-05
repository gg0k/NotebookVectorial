package com.example.notebookvectorial.tools

import android.graphics.RectF
import kotlin.math.min

class ZoomLogic {

    fun calculateBoxZoom(
        rect: RectF,
        viewWidth: Int,
        viewHeight: Int
    ): Triple<Float, Float, Float> {
        val scaleX = viewWidth.toFloat() / rect.width()
        val scaleY = viewHeight.toFloat() / rect.height()
        val targetScale = min(scaleX, scaleY).coerceIn(0.01f, 50.0f) // Límites extendidos

        val offsetX = (viewWidth / 2f) - (rect.centerX() * targetScale)
        val offsetY = (viewHeight / 2f) - (rect.centerY() * targetScale)

        return Triple(offsetX, offsetY, targetScale)
    }

    fun calculateClickZoom(
        sceneX: Float,
        sceneY: Float,
        currentScale: Float,
        eventX: Float,
        eventY: Float,
        zoomIn: Boolean
    ): Triple<Float, Float, Float> {
        val factor = if (zoomIn) 1.25f else 0.8f
        val targetScale = (currentScale * factor).coerceIn(0.001f, 500.0f) // Límites extendidos

        val offsetX = eventX - sceneX * targetScale
        val offsetY = eventY - sceneY * targetScale

        return Triple(offsetX, offsetY, targetScale)
    }

    // Ya no se usan con el nuevo UI de botones, pero los conservo por si acaso
    fun scaleToProgress(scale: Float): Int {
        return (((scale - 0.01f) / 49.99f) * 100).toInt().coerceIn(0, 100)
    }

    fun progressToScale(progress: Int): Float {
        return (progress / 100f) * 49.99f + 0.01f
    }
}