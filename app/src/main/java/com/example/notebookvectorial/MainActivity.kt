package com.example.notebookvectorial

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notebookvectorial.serialization.LayerData
import com.example.notebookvectorial.storage.StorageManager
import com.example.notebookvectorial.views.CanvasView
import com.example.notebookvectorial.views.ColorPickerDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Estructura para el árbol
data class FileNode(
    val file: File,
    val isClass: Boolean,
    val depth: Int,
    var isExpanded: Boolean = false,
    var children: List<FileNode> = emptyList()
)

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var canvasView: CanvasView
    private lateinit var layoutPenProps: LinearLayout
    private lateinit var layoutEraserProps: LinearLayout
    private lateinit var storageManager: StorageManager
    private lateinit var sharedPreferences: SharedPreferences

    // UI Panel Zoom Flotante
    private lateinit var layoutZoomUI: LinearLayout
    private lateinit var btnZoomIn: Button
    private lateinit var btnZoomOut: Button
    private lateinit var lblZoomValue: TextView

    // UI Panel Colores
    private lateinit var layoutColorPalette: LinearLayout
    private var selectedColorView: View? = null

    // UI Paginación
    private lateinit var lblPageInfo: TextView
    private lateinit var btnPrevPage: Button
    private lateinit var btnNextPage: Button

    // Gestión de proyecto en memoria
    private val pagesData = mutableListOf<List<LayerData>>()
    private var currentPageIndex = 0
    private var previousTool = CanvasView.ToolMode.LAPIZ

    // Variables de Archivos
    private var currentClassDir: File? = null
    private lateinit var treeRecyclerView: RecyclerView
    private lateinit var treeAdapter: TreeAdapter
    private var flatNodes = mutableListOf<FileNode>()
    private var expandedPaths = mutableSetOf<String>()
    private var selectedNode: FileNode? = null

    // Referencias de colores de la paleta
    private lateinit var btnBlack: View
    private lateinit var btnRed: View
    private lateinit var btnBlue: View
    private lateinit var btnGreen: View
    private lateinit var btnPink: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storageManager = StorageManager(this)
        sharedPreferences = getPreferences(Context.MODE_PRIVATE)

        drawerLayout = findViewById(R.id.drawerLayout)
        canvasView = findViewById(R.id.canvasView)
        layoutPenProps = findViewById(R.id.layoutPenProps)
        layoutEraserProps = findViewById(R.id.layoutEraserProps)

        // Controles de zoom
        layoutZoomUI = findViewById(R.id.layoutZoomUI)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        lblZoomValue = findViewById(R.id.lblZoomValue)

        layoutColorPalette = findViewById(R.id.layoutColorPalette)
        treeRecyclerView = findViewById(R.id.treeRecyclerView)
        lblPageInfo = findViewById(R.id.lblPageInfo)
        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        setupToolbar()
        setupProperties()
        setupColorPalette()
        setupFileManager()
        setupPageNavigation()

        // Cargar estado inicial (propiedades de cámara y herramientas)
        loadSavedProperties()

        // Empieza vacío, el usuario debe abrir/crear archivo
        pagesData.add(listOf(LayerData("Capa 1", true, mutableListOf())))
        canvasView.loadPageData(pagesData[0])

        updateKeyboardStatus()

        // Sincronizar eventos de gestos en la pantalla con UI de Lupa
        canvasView.onZoomChanged = { scale ->
            updateZoomLabel(scale)
            saveCameraPositionForCurrentPage()
        }

        canvasView.onCameraMoved = {
            saveCameraPositionForCurrentPage()
        }

        btnZoomIn.setOnClickListener {
            canvasView.zoomInCenter()
            updateZoomLabel(canvasView.scaleFactor)
        }
        btnZoomOut.setOnClickListener {
            canvasView.zoomOutCenter()
            updateZoomLabel(canvasView.scaleFactor)
        }

        // Asegurar que el lienzo siempre tenga el foco para recibir el teclado
        canvasView.isFocusable = true
        canvasView.isFocusableInTouchMode = true
        canvasView.requestFocus()
    }

    private fun updateZoomLabel(scale: Float) {
        val percentage = (scale * 100).toInt()
        lblZoomValue.text = "$percentage%"
    }

    private fun loadSavedProperties() {
        val savedStrokeWidth = sharedPreferences.getFloat("strokeWidth", 4f)
        val savedEraserSize = sharedPreferences.getFloat("eraserSize", 20f)
        val savedColor = sharedPreferences.getInt("penColor", Color.BLACK)
        val isDynamicEraser = sharedPreferences.getBoolean("dynamicEraser", false)

        canvasView.baseStrokeWidth = savedStrokeWidth
        canvasView.eraserSize = savedEraserSize
        canvasView.currentColor = savedColor
        canvasView.useDynamicEraser = isDynamicEraser

        findViewById<SeekBar>(R.id.sliderStrokeWidth).progress = savedStrokeWidth.toInt()
        findViewById<TextView>(R.id.lblStrokeWidth).text = "Grosor Lápiz: ${savedStrokeWidth.toInt()}"

        findViewById<SeekBar>(R.id.sliderEraserWidth).progress = savedEraserSize.toInt()
        findViewById<TextView>(R.id.lblEraserWidth).text = "Tamaño Goma: ${savedEraserSize.toInt()}"

        findViewById<CheckBox>(R.id.chkDynamicEraser).isChecked = isDynamicEraser

        // Restaurar feedback de color en la paleta
        val viewToSelect = when (savedColor) {
            Color.BLACK -> btnBlack
            Color.parseColor("#E53935") -> btnRed
            Color.parseColor("#1E88E5") -> btnBlue
            Color.parseColor("#48B500") -> btnGreen
            Color.parseColor("#CC25FA") -> btnPink
            else -> null
        }
        applyColor(savedColor, viewToSelect)
    }

    override fun onPause() {
        super.onPause()
        // Guardar preferencias generales antes de salir
        with (sharedPreferences.edit()) {
            putFloat("strokeWidth", canvasView.baseStrokeWidth)
            putFloat("eraserSize", canvasView.eraserSize)
            putInt("penColor", canvasView.currentColor)
            putBoolean("dynamicEraser", canvasView.useDynamicEraser)
            apply()
        }
        saveCameraPositionForCurrentPage()
    }

    private fun updateKeyboardStatus() {
        val hasPhysicalKeyboard = resources.configuration.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY
        canvasView.isPinchZoomDisabled = hasPhysicalKeyboard
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateKeyboardStatus() // Actualiza si conectan/desconectan la funda
    }

    private fun createColorDrawable(color: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
        drawable.setStroke(3, Color.parseColor("#80CCCCCC"))
        return drawable
    }

    private fun applyColor(color: Int, view: View?) {
        canvasView.currentColor = color
        with(sharedPreferences.edit()) {
            putInt("penColor", color)
            apply()
        }

        // Restaurar estado del color anterior
        selectedColorView?.let {
            (it.background as GradientDrawable).setStroke(3, Color.parseColor("#80CCCCCC"))
            it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
        }

        // Aplicar feedback al color nuevo (si es uno de la paleta)
        view?.let {
            (it.background as GradientDrawable).setStroke(8, Color.parseColor("#80CCCCCC")) // Borde grueso blanco
            it.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start() // Agrandar
            selectedColorView = it
        } ?: run {
            selectedColorView = null // Es un color personalizado, deseleccionamos la paleta
        }

        // Actualizar el botón de propiedades para que muestre el color
        findViewById<Button>(R.id.btnCustomColor).setBackgroundColor(color)
    }

    private fun setupColorPalette() {
        btnBlack = findViewById(R.id.btnColorBlack)
        btnRed = findViewById(R.id.btnColorRed)
        btnBlue = findViewById(R.id.btnColorBlue)
        btnGreen = findViewById(R.id.btnColorGreen)
        btnPink = findViewById(R.id.btnColorPink)

        btnBlack.background = createColorDrawable(Color.BLACK)
        btnRed.background = createColorDrawable(Color.parseColor("#E53935"))
        btnBlue.background = createColorDrawable(Color.parseColor("#1E88E5"))
        btnGreen.background = createColorDrawable(Color.parseColor("#48B500"))
        btnPink.background = createColorDrawable(Color.parseColor("#CC25FA"))

        val clickListener = View.OnClickListener { view ->
            val color = (view.background as GradientDrawable).color?.defaultColor ?: Color.BLACK
            applyColor(color, view)
        }

        btnBlack.setOnClickListener(clickListener)
        btnRed.setOnClickListener(clickListener)
        btnBlue.setOnClickListener(clickListener)
        btnGreen.setOnClickListener(clickListener)
        btnPink.setOnClickListener(clickListener)

        // Botón de Selector de Color Avanzado en Propiedades
        val btnCustomColor = findViewById<Button>(R.id.btnCustomColor)
        btnCustomColor.setOnClickListener {
            ColorPickerDialog(this, canvasView.currentColor) { selectedColor ->
                applyColor(selectedColor, null) // Pasamos null porque no es de la paleta rápida
                drawerLayout.closeDrawer(GravityCompat.END)
                canvasView.requestFocus()
            }.show(this)
        }
    }

    private fun setupToolbar() {
        val btnMenu = findViewById<Button>(R.id.btnMenu)
        val btnProperties = findViewById<Button>(R.id.btnProperties)
        val btnPen = findViewById<Button>(R.id.btnPen)
        val btnEraser = findViewById<Button>(R.id.btnEraser)
        val btnHand = findViewById<Button>(R.id.btnHand)
        val btnZoom = findViewById<Button>(R.id.btnZoom)

        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        btnProperties.setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }

        val resetColorsAndCommit = {
            // FIX: Forzar el guardado del trazo actual si cambiamos de herramienta repentinamente
            canvasView.forceCommitCurrentStroke()

            btnPen.setBackgroundColor(Color.parseColor("#3C3F41"))
            btnEraser.setBackgroundColor(Color.parseColor("#444444"))
            btnHand.setBackgroundColor(Color.parseColor("#444444"))
            btnZoom.setBackgroundColor(Color.parseColor("#444444"))
            layoutZoomUI.visibility = View.GONE
            layoutColorPalette.visibility = View.GONE
            canvasView.requestFocus() // Devolver foco al lienzo automáticamente
        }

        btnPen.setOnClickListener {
            resetColorsAndCommit()
            canvasView.currentTool = CanvasView.ToolMode.LAPIZ
            btnPen.setBackgroundColor(Color.parseColor("#4CAF50"))
            layoutPenProps.visibility = View.VISIBLE
            layoutEraserProps.visibility = View.GONE
            layoutColorPalette.visibility = View.VISIBLE
        }

        btnEraser.setOnClickListener {
            resetColorsAndCommit()
            canvasView.currentTool = CanvasView.ToolMode.BORRADOR
            btnEraser.setBackgroundColor(Color.parseColor("#4CAF50"))
            layoutPenProps.visibility = View.GONE
            layoutEraserProps.visibility = View.VISIBLE
        }

        btnHand.setOnClickListener {
            resetColorsAndCommit()
            canvasView.currentTool = CanvasView.ToolMode.MANO
            btnHand.setBackgroundColor(Color.parseColor("#4CAF50"))
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        }

        btnZoom.setOnClickListener {
            resetColorsAndCommit()
            canvasView.currentTool = CanvasView.ToolMode.ZOOM
            btnZoom.setBackgroundColor(Color.parseColor("#4CAF50"))
            layoutPenProps.visibility = View.GONE
            layoutEraserProps.visibility = View.GONE
            layoutZoomUI.visibility = View.VISIBLE
            updateZoomLabel(canvasView.scaleFactor)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        }

        btnPen.performClick()
    }

    private fun setupFileManager() {
        treeAdapter = TreeAdapter()
        treeRecyclerView.layoutManager = LinearLayoutManager(this)
        treeRecyclerView.adapter = treeAdapter

        findViewById<Button>(R.id.btnRefreshFiles).setOnClickListener { refreshTree() }

        findViewById<Button>(R.id.btnNewSubject).setOnClickListener {
            val targetDir = when {
                selectedNode == null -> storageManager.getRootDirectory()
                selectedNode!!.isClass -> selectedNode!!.file.parentFile ?: storageManager.getRootDirectory()
                else -> selectedNode!!.file
            }

            val input = EditText(this)
            input.inputType = InputType.TYPE_CLASS_TEXT // Soluciona el salto de línea visual
            input.maxLines = 1

            val dialog = AlertDialog.Builder(this)
                .setTitle("Nueva Carpeta en ${targetDir.name}")
                .setView(input)
                .setPositiveButton("Crear") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        File(targetDir, name).mkdirs()
                        expandedPaths.add(targetDir.absolutePath)
                        refreshTree()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .create()

            input.setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    true
                } else false
            }
            dialog.show()
        }

        findViewById<Button>(R.id.btnTodayClass).setOnClickListener {
            val targetDir = when {
                selectedNode == null -> storageManager.getRootDirectory()
                selectedNode!!.isClass -> selectedNode!!.file.parentFile ?: storageManager.getRootDirectory()
                else -> selectedNode!!.file
            }

            val fecha = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val newClassDir = File(targetDir, fecha)

            if (!newClassDir.exists()) {
                newClassDir.mkdirs()
                File(newClassDir, "assets").mkdirs()
                val emptyPages = listOf(listOf(LayerData("Capa 1", true, mutableListOf())))
                storageManager.saveClassData(newClassDir, emptyPages)
            }

            expandedPaths.add(targetDir.absolutePath)
            refreshTree()
            openClassFile(newClassDir)
            Toast.makeText(this, "Clase $fecha abierta", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSaveFile).setOnClickListener {
            if (currentClassDir == null) {
                Toast.makeText(this, "Abre o crea una clase primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveCurrentPageToMemory()
            storageManager.saveClassData(currentClassDir!!, pagesData)
            Toast.makeText(this, "Guardado exitoso", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnDeleteFile).setOnClickListener {
            if (selectedNode == null) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Borrar")
                .setMessage("¿Seguro que quieres borrar ${selectedNode!!.file.name} y todo su contenido?")
                .setPositiveButton("Sí, borrar") { _, _ ->
                    val isOpenedFile = selectedNode!!.file.absolutePath == currentClassDir?.absolutePath
                    selectedNode!!.file.deleteRecursively() // Equivalente a rmtree

                    if (isOpenedFile) {
                        currentClassDir = null
                        pagesData.clear()
                        pagesData.add(listOf(LayerData("Capa 1", true, mutableListOf())))
                        currentPageIndex = 0
                        canvasView.loadPageData(pagesData[0])
                    }

                    selectedNode = null
                    refreshTree()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        refreshTree()
    }

    private fun refreshTree() {
        val root = storageManager.getRootDirectory()
        val tree = buildTree(root, 0)
        flatNodes.clear()
        flattenTree(tree)
        treeAdapter.notifyDataSetChanged()
    }

    private fun buildTree(dir: File, depth: Int): List<FileNode> {
        val nodes = mutableListOf<FileNode>()
        val files = dir.listFiles() ?: return nodes

        val dirs = mutableListOf<File>()
        val classes = mutableListOf<File>()

        for (f in files) {
            if (f.isDirectory) {
                if (File(f, "data.json").exists()) classes.add(f)
                else dirs.add(f)
            }
        }

        dirs.sortBy { it.name.lowercase() }
        classes.sortByDescending { parseDateForSorting(it.name) }

        for (d in dirs) {
            val node = FileNode(d, false, depth)
            node.isExpanded = expandedPaths.contains(d.absolutePath)
            node.children = buildTree(d, depth + 1)
            nodes.add(node)
        }
        for (c in classes) {
            nodes.add(FileNode(c, true, depth))
        }
        return nodes
    }

    private fun parseDateForSorting(dateString: String): Long {
        return try {
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(dateString)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun flattenTree(nodes: List<FileNode>) {
        for (node in nodes) {
            flatNodes.add(node)
            if (node.isExpanded && !node.isClass) {
                flattenTree(node.children)
            }
        }
    }

    private fun openClassFile(classDir: File) {
        if (currentClassDir != null) {
            saveCurrentPageToMemory()
            storageManager.saveClassData(currentClassDir!!, pagesData)
        }
        currentClassDir = classDir
        val diskData = storageManager.loadClassData(classDir)
        pagesData.clear()
        if (diskData != null && diskData.isNotEmpty()) {
            pagesData.addAll(diskData)
        } else {
            pagesData.add(listOf(LayerData("Capa 1", true, mutableListOf())))
        }
        currentPageIndex = 0
        loadPageFromMemory(currentPageIndex)
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun setupPageNavigation() {
        btnPrevPage.setOnClickListener {
            if (currentPageIndex > 0) {
                saveCurrentPageToMemory()
                loadPageFromMemory(currentPageIndex - 1)
            }
        }

        btnNextPage.setOnClickListener {
            if (currentPageIndex < pagesData.size - 1) {
                saveCurrentPageToMemory()
                loadPageFromMemory(currentPageIndex + 1)
            } else {
                saveCurrentPageToMemory()
                pagesData.add(listOf(LayerData("Capa 1", true, mutableListOf())))
                loadPageFromMemory(pagesData.size - 1)
            }
        }
    }

    private fun saveCurrentPageToMemory() {
        if (currentPageIndex < pagesData.size) {
            // Forzar guardado por si el usuario cambia de página mientras dibuja
            canvasView.forceCommitCurrentStroke()
            pagesData[currentPageIndex] = canvasView.exportCurrentPage()
            saveCameraPositionForCurrentPage()
        }
    }

    private fun getCameraKey(index: Int): String {
        val classId = currentClassDir?.name ?: "temp"
        return "cam_${classId}_page_${index}"
    }

    private fun saveCameraPositionForCurrentPage() {
        val key = getCameraKey(currentPageIndex)
        with(sharedPreferences.edit()) {
            putFloat("${key}_offsetX", canvasView.offsetX)
            putFloat("${key}_offsetY", canvasView.offsetY)
            putFloat("${key}_scale", canvasView.scaleFactor)
            apply()
        }
    }

    private fun loadPageFromMemory(index: Int) {
        currentPageIndex = index
        val pageLayers = pagesData.getOrElse(index) { listOf(LayerData("Capa 1", true, mutableListOf())) }
        canvasView.loadPageData(pageLayers)

        // Restaurar posición de cámara
        val key = getCameraKey(index)
        val defaultScale = 0.5f // valor de inicio alejado
        val defaultOffsetX = (canvasView.width / 2f) - ((3369f * defaultScale) / 2f)

        val offsetX = sharedPreferences.getFloat("${key}_offsetX", defaultOffsetX)
        val offsetY = sharedPreferences.getFloat("${key}_offsetY", 50f)
        val scale = sharedPreferences.getFloat("${key}_scale", defaultScale)

        canvasView.setCameraPosition(offsetX, offsetY, scale)
        updateZoomLabel(scale)

        updatePageUi()
    }

    private fun updatePageUi() {
        val total = pagesData.size
        lblPageInfo.text = "Página: ${currentPageIndex + 1} / ${maxOf(1, total)}"
        btnPrevPage.isEnabled = currentPageIndex > 0
        btnNextPage.text = if (currentPageIndex < total - 1) "Siguiente ▶" else "➕ Nueva Pág"
        btnNextPage.setBackgroundColor(if (currentPageIndex < total - 1) Color.parseColor("#444444") else Color.parseColor("#4CAF50"))
    }

    private fun setupProperties() {
        val sliderStroke = findViewById<SeekBar>(R.id.sliderStrokeWidth)
        val lblStroke = findViewById<TextView>(R.id.lblStrokeWidth)
        sliderStroke.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                val v = maxOf(1, p).toFloat()
                canvasView.baseStrokeWidth = v
                lblStroke.text = "Grosor Lápiz: ${v.toInt()}"

                with(sharedPreferences.edit()) {
                    putFloat("strokeWidth", v)
                    apply()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val sliderEraser = findViewById<SeekBar>(R.id.sliderEraserWidth)
        val lblEraser = findViewById<TextView>(R.id.lblEraserWidth)
        sliderEraser.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                val v = maxOf(5, p).toFloat()
                canvasView.eraserSize = v
                lblEraser.text = "Tamaño Goma: ${v.toInt()}"

                with(sharedPreferences.edit()) {
                    putFloat("eraserSize", v)
                    apply()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val chkDynamicEraser = findViewById<CheckBox>(R.id.chkDynamicEraser)
        chkDynamicEraser.setOnCheckedChangeListener { _, isChecked ->
            canvasView.useDynamicEraser = isChecked
            with(sharedPreferences.edit()) {
                putBoolean("dynamicEraser", isChecked)
                apply()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val isUp = event.action == KeyEvent.ACTION_UP

        // Bloquear la tecla Command (Meta) para que no abra menús de Android
        if (event.keyCode == KeyEvent.KEYCODE_META_LEFT || event.keyCode == KeyEvent.KEYCODE_META_RIGHT) {
            return true
        }

        // Rastrear Alt para el dezoom (Alejar)
        if (event.keyCode == KeyEvent.KEYCODE_ALT_LEFT || event.keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            canvasView.isAltPressed = isDown
        }

        if (event.isCtrlPressed && isDown) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_Z -> { performUndo(); return true }
                KeyEvent.KEYCODE_Y -> { performRedo(); return true }
            }
        }

        // Atajo: Paneo rápido con Espacio
        if (event.keyCode == KeyEvent.KEYCODE_SPACE) {
            if (isDown && event.repeatCount == 0) {
                previousTool = canvasView.currentTool
                findViewById<Button>(R.id.btnHand).performClick()
            } else if (isUp) {
                when (previousTool) {
                    CanvasView.ToolMode.LAPIZ -> findViewById<Button>(R.id.btnPen).performClick()
                    CanvasView.ToolMode.BORRADOR -> findViewById<Button>(R.id.btnEraser).performClick()
                    CanvasView.ToolMode.ZOOM -> findViewById<Button>(R.id.btnZoom).performClick()
                    CanvasView.ToolMode.MANO -> findViewById<Button>(R.id.btnHand).performClick()
                }
            }
            return true // Importante: consumimos el espacio para que no afecte botones
        }

        // Hotkeys para herramientas directas
        if (isDown && event.repeatCount == 0 && !event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_B -> { findViewById<Button>(R.id.btnPen).performClick(); return true }
                KeyEvent.KEYCODE_E -> { findViewById<Button>(R.id.btnEraser).performClick(); return true }
                KeyEvent.KEYCODE_Z -> { findViewById<Button>(R.id.btnZoom).performClick(); return true }
            }
        }

        // Bloquear el resto de teclas alfanuméricas SI estamos enfocados en el lienzo.
        if (canvasView.hasFocus() && event.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && !event.isCtrlPressed) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun performUndo() {
        if (canvasView.undoManager.canUndo()) {
            val previousState = canvasView.undoManager.undo()
            if (previousState != null) canvasView.restoreUndoState(previousState)
        }
    }

    private fun performRedo() {
        if (canvasView.undoManager.canRedo()) {
            val nextState = canvasView.undoManager.redo()
            if (nextState != null) canvasView.restoreUndoState(nextState)
        }
    }

    inner class TreeAdapter : RecyclerView.Adapter<TreeAdapter.ViewHolder>() {

        inner class ViewHolder(val view: LinearLayout, val textView: TextView) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val textView = TextView(context).apply {
                textSize = 15f
                setPadding(0, 24, 24, 24)
            }
            layout.addView(textView)
            return ViewHolder(layout, textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val node = flatNodes[position]

            holder.view.setPadding(node.depth * 60 + 20, 0, 0, 0)

            val icon = if (node.isClass) "📄" else if (node.isExpanded) "📂" else "📁"
            holder.textView.text = "$icon ${node.file.name}"

            if (node.file.absolutePath == selectedNode?.file?.absolutePath) {
                holder.view.setBackgroundColor(Color.parseColor("#444444"))
                holder.textView.setTextColor(Color.WHITE)
            } else {
                holder.view.setBackgroundColor(Color.TRANSPARENT)
                holder.textView.setTextColor(Color.parseColor("#DDDDDD"))
            }

            holder.view.setOnClickListener {
                selectedNode = node
                if (node.isClass) {
                    openClassFile(node.file)
                } else {
                    node.isExpanded = !node.isExpanded
                    if (node.isExpanded) expandedPaths.add(node.file.absolutePath)
                    else expandedPaths.remove(node.file.absolutePath)
                    refreshTree()
                }
                notifyDataSetChanged()
            }
        }

        override fun getItemCount() = flatNodes.size
    }
}