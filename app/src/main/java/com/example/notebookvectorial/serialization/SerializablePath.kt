package com.example.notebookvectorial.serialization

import android.graphics.Path

class SerializablePath {
    val androidPath = Path()
    val elements = mutableListOf<PathElement>()

    fun moveTo(x: Float, y: Float) {
        androidPath.moveTo(x, y)
        elements.add(PathElement(0, x, y))
    }

    fun lineTo(x: Float, y: Float) {
        androidPath.lineTo(x, y)
        elements.add(PathElement(1, x, y))
    }

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        androidPath.cubicTo(x1, y1, x2, y2, x3, y3)
        // PyQt6 requiere que declaremos el punto de destino como tipo 2 (CubicTo)
        // y los de control se infieren en la iteración. (Mantenemos retrocompatibilidad con tu versión PC)
        elements.add(PathElement(2, x1, y1))
        elements.add(PathElement(2, x2, y2))
        elements.add(PathElement(2, x3, y3))
    }
}