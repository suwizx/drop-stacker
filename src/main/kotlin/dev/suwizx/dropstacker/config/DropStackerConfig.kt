package dev.suwizx.dropstacker.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.File

object DropStackerConfig {
    private val logger = LoggerFactory.getLogger("drop-stacker-config")
    var maxStackSize: Int = 1000
    var scanRadiusX: Double = 5.0
    var scanRadiusY: Double = 2.0
    var scanRadiusZ: Double = 5.0
    var scanInterval: Int = 5
    var showDespawnTimer: Boolean = true
    var countLowThreshold: Int = 64
    var countHighThreshold: Int = 500
    var hideSingleItemLabel: Boolean = false
    var blacklist: Set<String> = setOf()
    var despawnTicks: Int = 6000

    private val configFile: File = FabricLoader.getInstance().configDir.resolve("drop-stacker.json").toFile()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        if (!configFile.exists()) {
            save()
            return
        }

        try {
            val data = configFile.reader().use { reader ->
                gson.fromJson(reader, DropStackerConfigData::class.java)
            }

            maxStackSize = data.maxStackSize ?: 1000
            scanRadiusX = data.scanRadiusX ?: 5.0
            scanRadiusY = data.scanRadiusY ?: 2.0
            scanRadiusZ = data.scanRadiusZ ?: 5.0
            scanInterval = data.scanInterval ?: 5
            showDespawnTimer = data.showDespawnTimer ?: true
            countLowThreshold = data.countLowThreshold ?: 64
            countHighThreshold = data.countHighThreshold ?: 500
            hideSingleItemLabel = data.hideSingleItemLabel ?: false
            blacklist = data.blacklist?.toSet() ?: setOf()
            despawnTicks = data.despawnTicks ?: 6000
            
            // Re-save so any new fields missing from an old config file get written
            save()
        } catch (e: Exception) {
            logger.error("Failed to load config, using defaults.", e)
        }
    }

    fun save() {
        try {
            val data = DropStackerConfigData(
                maxStackSize, scanRadiusX, scanRadiusY, scanRadiusZ, scanInterval, showDespawnTimer,
                countLowThreshold, countHighThreshold, hideSingleItemLabel, blacklist.toList(), despawnTicks
            )
            configFile.writeText(gson.toJson(data))
        } catch (e: Exception) {
            logger.error("Failed to save config.", e)
        }
    }

    private data class DropStackerConfigData(
        var maxStackSize: Int?,
        var scanRadiusX: Double?,
        var scanRadiusY: Double?,
        var scanRadiusZ: Double?,
        var scanInterval: Int?,
        var showDespawnTimer: Boolean?,
        var countLowThreshold: Int?,
        var countHighThreshold: Int?,
        var hideSingleItemLabel: Boolean?,
        var blacklist: List<String>?,
        var despawnTicks: Int?
    )
}
