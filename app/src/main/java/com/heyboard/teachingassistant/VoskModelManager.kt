package com.heyboard.teachingassistant

import android.content.Context
import android.util.Log
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class VoskModelManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskModelManager"
        private val ASSET_DIRS = mapOf(
            "zh-CN" to "model-zh-cn",
            "en-US" to "model-en-us"
        )
    }

    private val modelsDir = File(context.filesDir, "vosk-models")

    fun getModelPath(language: String): String {
        val dirName = ASSET_DIRS[language] ?: ASSET_DIRS["zh-CN"]!!
        return File(modelsDir, dirName).absolutePath
    }

    fun isModelReady(language: String): Boolean {
        val modelDir = File(getModelPath(language))
        return modelDir.exists() && modelDir.isDirectory &&
                modelDir.listFiles()?.isNotEmpty() == true
    }

    fun loadModel(language: String): Model? {
        if (!isModelReady(language)) {
            unpackModel(language)
        }
        return try {
            Model(getModelPath(language))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model for $language", e)
            null
        }
    }

    fun unpackModel(language: String) {
        val assetDir = ASSET_DIRS[language] ?: return
        val targetDir = File(getModelPath(language))
        if (targetDir.exists()) return

        Log.i(TAG, "Unpacking model: $assetDir")
        targetDir.mkdirs()
        copyAssetDir(assetDir, targetDir)
        Log.i(TAG, "Model unpacked to: ${targetDir.absolutePath}")
    }

    private fun copyAssetDir(assetPath: String, targetDir: File) {
        val assetManager = context.assets
        try {
            val files = assetManager.list(assetPath) ?: return
            if (files.isEmpty()) {
                // It's a file, copy it
                copyAssetFile(assetPath, File(targetDir.parent!!, File(assetPath).name))
                return
            }
            targetDir.mkdirs()
            for (file in files) {
                val subAssetPath = "$assetPath/$file"
                val subFiles = assetManager.list(subAssetPath)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    // It's a directory
                    copyAssetDir(subAssetPath, File(targetDir, file))
                } else {
                    // It's a file
                    copyAssetFile(subAssetPath, File(targetDir, file))
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset dir: $assetPath", e)
        }
    }

    private fun copyAssetFile(assetPath: String, targetFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset file: $assetPath", e)
        }
    }
}
