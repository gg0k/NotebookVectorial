package com.example.notebookvectorial.storage

import android.content.Context
import android.os.Environment
import com.example.notebookvectorial.serialization.DocumentData
import com.example.notebookvectorial.serialization.LayerData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

class StorageManager(context: Context) {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private val rootDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "NotebookVectorial/Materias"
    )

    init {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    fun getRootDirectory(): File = rootDir

    fun saveClassData(classDir: File, pages: List<List<LayerData>>) {
        if (!classDir.exists()) classDir.mkdirs()

        val dataFile = File(classDir, "data.json")
        val tempFile = File(classDir, "data.json.tmp")

        try {
            val exportData = DocumentData(version = "2.0", pages = pages)
            val jsonString = gson.toJson(exportData)
            tempFile.writeText(jsonString)

            if (tempFile.exists()) {
                if (dataFile.exists()) dataFile.delete()
                tempFile.renameTo(dataFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun loadClassData(classDir: File): List<List<LayerData>>? {
        val dataFile = File(classDir, "data.json")
        if (!dataFile.exists()) return null

        return try {
            val jsonString = dataFile.readText()
            val docData = gson.fromJson(jsonString, DocumentData::class.java)
            docData.pages
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}