package com.example.notebookvectorial.tools

import com.example.notebookvectorial.serialization.LayerData

class UndoManager {
    // Guardamos copias profundas de la página entera por simplicidad y robustez
    private val history = mutableListOf<List<LayerData>>()
    private var currentIndex = -1

    fun saveState(layers: List<LayerData>) {
        // Clonar profundamente para evitar referencias mutables
        val stateClone = layers.map { l -> l.copy(items = l.items.map { it.copy() }.toMutableList()) }

        // Si estamos en medio del historial y hacemos una nueva acción, borramos el "futuro"
        if (currentIndex < history.size - 1) {
            history.subList(currentIndex + 1, history.size).clear()
        }

        history.add(stateClone)
        currentIndex = history.size - 1

        // Limitar historial a 30 pasos para no saturar RAM
        if (history.size > 30) {
            history.removeAt(0)
            currentIndex--
        }
    }

    fun undo(): List<LayerData>? {
        if (currentIndex > 0) {
            currentIndex--
            return cloneState(history[currentIndex])
        }
        return null
    }

    fun redo(): List<LayerData>? {
        if (currentIndex < history.size - 1) {
            currentIndex++
            return cloneState(history[currentIndex])
        }
        return null
    }

    fun canUndo() = currentIndex > 0
    fun canRedo() = currentIndex < history.size - 1

    fun clear() {
        history.clear()
        currentIndex = -1
    }

    private fun cloneState(layers: List<LayerData>): List<LayerData> {
        return layers.map { l -> l.copy(items = l.items.map { it.copy() }.toMutableList()) }
    }
}