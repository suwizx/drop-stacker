package dev.suwizx.dropstacker.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object DropStackerConfig {
    var maxStackSize: Int = 1000
    var scanRadiusX: Double = 5.0
    var scanRadiusY: Double = 2.0
    var scanRadiusZ: Double = 5.0
    var scanInterval: Int = 5
    var showDespawnTimer: Boolean = true

    private val configFile: File = FabricLoader.getInstance().configDir.resolve("drop-stacker.json").toFile()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        if (!configFile.exists()) {
            save()
            return
        }

        try {
            val reader = configFile.reader()
            val data = gson.fromJson(reader, DropStackerConfigData::class.java)
            reader.close()

            maxStackSize = data.maxStackSize ?: 1000
            scanRadiusX = data.scanRadiusX ?: 5.0
            scanRadiusY = data.scanRadiusY ?: 2.0
            scanRadiusZ = data.scanRadiusZ ?: 5.0
            scanInterval = data.scanInterval ?: 5
            showDespawnTimer = data.showDespawnTimer ?: true
            // Re-save so any new fields missing from an old config file get written
            save()
        } catch (e: Exception) {
            println("[DropStacker] Failed to load config, using defaults.")
            e.printStackTrace()
        }
    }

    fun save() {
        try {
            val data = DropStackerConfigData(
                maxStackSize, scanRadiusX, scanRadiusY, scanRadiusZ, scanInterval, showDespawnTimer
            )
            configFile.writeText(gson.toJson(data))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private data class DropStackerConfigData(
        var maxStackSize: Int?,
        var scanRadiusX: Double?,
        var scanRadiusY: Double?,
        var scanRadiusZ: Double?,
        var scanInterval: Int?,
        var showDespawnTimer: Boolean?
    )
}