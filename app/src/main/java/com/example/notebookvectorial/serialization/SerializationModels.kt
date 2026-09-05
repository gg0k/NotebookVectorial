package com.example.notebookvectorial.serialization

// Estructura raíz para exportación segura y evitar bugs de Float a Double de Gson
data class DocumentData(
    val version: String,
    val pages: List<List<LayerData>>
)

data class PathElement(
    val t: Int, // 0: MoveTo, 1: LineTo, 2: CubicTo
    val x: Float,
    val y: Float
)

data class ItemData(
    val type: String, // "path", "text", "image"
    var pos_x: Float, var pos_y: Float,
    var rot: Float = 0f, var scale: Float = 1f, var z: Float = 0f,
    var m11: Float = 1f, var m12: Float = 0f,
    var m21: Float = 0f, var m22: Float = 1f,

    // Exclusivo de "path"
    var path_elements: List<PathElement>? = null,
    var pen_color: String? = null,
    var pen_width: Float? = null,
    var has_pen: Boolean = true,
    var has_fill: Boolean = false,
    var fill_color: String? = null,

    // Exclusivo de "text"
    var content: String? = null,
    var font_family: String? = null,
    var font_size: Int? = null,
    var color: String? = null,

    // Exclusivo de "image"
    var img_filename: String? = null
)

data class LayerData(
    val nombre: String,
    val visible: Boolean,
    val items: MutableList<ItemData>
)