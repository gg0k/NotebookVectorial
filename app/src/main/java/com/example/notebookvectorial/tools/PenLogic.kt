package com.example.notebookvectorial.tools

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.example.notebookvectorial.serialization.SerializablePath
import kotlin.math.atan2
import kotlin.math.hypot

class PenLogic {

    data class PointPres(val p: PointF, val pres: Float)

    private fun interpolarEspina(puntos: List<PointPres>, pasos: Int = 4): List<PointPres> {
        if (puntos.size < 3) return puntos
        val espinaSuave = mutableListOf(puntos.first())
        for (i in 0 until puntos.size - 2) {
            val p0 = puntos[i].p; val pres0 = puntos[i].pres
            val p1 = puntos[i + 1].p; val pres1 = puntos[i + 1].pres
            val p2 = puntos[i + 2].p; val pres2 = puntos[i + 2].pres

            val m1x = (p0.x + p1.x) / 2f
            val m1y = (p0.y + p1.y) / 2f
            val presM1 = (pres0 + pres1) / 2f
            val m2x = (p1.x + p2.x) / 2f
            val m2y = (p1.y + p2.y) / 2f
            val presM2 = (pres1 + pres2) / 2f

            for (j in 1..pasos) {
                val t = j.toFloat() / pasos
                val u = 1f - t
                val x = (u * u) * m1x + (2 * u * t) * p1.x + (t * t) * m2x
                val y = (u * u) * m1y + (2 * u * t) * p1.y + (t * t) * m2y
                val pres = (u * u) * presM1 + (2 * u * t) * pres1 + (t * t) * presM2
                espinaSuave.add(PointPres(PointF(x, y), pres))
            }
        }
        espinaSuave.add(puntos.last())
        return espinaSuave
    }

    fun generarPathPresion(
        puntosConPresion: List<PointPres>,
        grosorBase: Float,
        suavizar: Boolean = false,
        aplanar: Boolean = false
    ): SerializablePath {
        val serializablePath = SerializablePath()
        serializablePath.androidPath.fillType = Path.FillType.WINDING

        if (puntosConPresion.size == 1) {
            val p = puntosConPresion[0].p
            val radio = (grosorBase * puntosConPresion[0].pres) / 2f
            val kappa = 0.5522847f * radio
            serializablePath.moveTo(p.x, p.y - radio)
            serializablePath.cubicTo(p.x + kappa, p.y - radio, p.x + radio, p.y - kappa, p.x + radio, p.y)
            serializablePath.cubicTo(p.x + radio, p.y + kappa, p.x + kappa, p.y + radio, p.x, p.y + radio)
            serializablePath.cubicTo(p.x - kappa, p.y + radio, p.x - radio, p.y + kappa, p.x - radio, p.y)
            serializablePath.cubicTo(p.x - radio, p.y - kappa, p.x - kappa, p.y - radio, p.x, p.y - radio)
            return serializablePath
        }

        val puntos = if (suavizar) interpolarEspina(puntosConPresion, 4) else puntosConPresion
        if (puntos.size < 2) return serializablePath

        val puntosIzq = mutableListOf<PointF>()
        val puntosDer = mutableListOf<PointF>()
        var offsetInicio = Pair(0f, 0f)
        var offsetFin = Pair(0f, 0f)

        // MEMORIA DEL ÚLTIMO VECTOR VÁLIDO
        var lastNx = 0f
        var lastNy = 1f

        for (i in puntos.indices) {
            val pos = puntos[i].p
            val presion = puntos[i].pres

            val (dx, dy) = when (i) {
                0 -> Pair(puntos[1].p.x - pos.x, puntos[1].p.y - pos.y)
                puntos.size - 1 -> Pair(pos.x - puntos[i - 1].p.x, pos.y - puntos[i - 1].p.y)
                else -> Pair(puntos[i + 1].p.x - puntos[i - 1].p.x, puntos[i + 1].p.y - puntos[i - 1].p.y)
            }

            val longitud = kotlin.math.hypot(dx, dy)
            // SOLO actualizamos la normal si la distancia no es cero
            val nx = if (longitud > 0.01f) -dy / longitud else lastNx
            val ny = if (longitud > 0.01f) dx / longitud else lastNy
            lastNx = nx
            lastNy = ny

            val grosorActual = grosorBase * presion
            val offsetX = nx * (grosorActual / 2f)
            val offsetY = ny * (grosorActual / 2f)

            puntosIzq.add(PointF(pos.x + offsetX, pos.y + offsetY))
            puntosDer.add(PointF(pos.x - offsetX, pos.y - offsetY))

            if (i == 0) offsetInicio = Pair(offsetX, offsetY)
            if (i == puntos.size - 1) offsetFin = Pair(offsetX, offsetY)
        }

        val k = 0.5522847f

        serializablePath.moveTo(puntosIzq[0].x, puntosIzq[0].y)
        for (i in 1 until puntosIzq.size) {
            serializablePath.lineTo(puntosIzq[i].x, puntosIzq[i].y)
        }

        val pFin = puntos.last().p
        val fxFin = offsetFin.second
        val fyFin = -offsetFin.first
        val pFront = PointF(pFin.x + fxFin, pFin.y + fyFin)

        val p1Fin = puntosIzq.last()
        serializablePath.cubicTo(
            p1Fin.x + fxFin * k, p1Fin.y + fyFin * k,
            pFront.x + offsetFin.first * k, pFront.y + offsetFin.second * k,
            pFront.x, pFront.y
        )
        val p2Fin = puntosDer.last()
        serializablePath.cubicTo(
            pFront.x - offsetFin.first * k, pFront.y - offsetFin.second * k,
            p2Fin.x + fxFin * k, p2Fin.y + fyFin * k,
            p2Fin.x, p2Fin.y
        )

        for (i in puntosDer.size - 2 downTo 0) {
            serializablePath.lineTo(puntosDer[i].x, puntosDer[i].y)
        }

        val pInicio = puntos.first().p
        val fxIni = offsetInicio.second
        val fyIni = -offsetInicio.first
        val pBack = PointF(pInicio.x - fxIni, pInicio.y - fyIni)

        val p1Ini = puntosDer.first()
        serializablePath.cubicTo(
            p1Ini.x - fxIni * k, p1Ini.y - fyIni * k,
            pBack.x - offsetInicio.first * k, pBack.y - offsetInicio.second * k,
            pBack.x, pBack.y
        )
        val p2Ini = puntosIzq.first()
        serializablePath.cubicTo(
            pBack.x + offsetInicio.first * k, pBack.y + offsetInicio.second * k,
            p2Ini.x - fxIni * k, p2Ini.y - fyIni * k,
            p2Ini.x, p2Ini.y
        )

        serializablePath.androidPath.close()
        if (aplanar) {
            val flattened = Path()
            flattened.op(serializablePath.androidPath, serializablePath.androidPath, Path.Op.UNION)
            serializablePath.androidPath.set(flattened)
        }



        return serializablePath
    }
}