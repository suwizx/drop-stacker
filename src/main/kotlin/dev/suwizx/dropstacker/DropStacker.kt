package dev.suwizx.dropstacker

import dev.suwizx.dropstacker.config.DropStackerConfig
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object DropStacker : ModInitializer {
    private val logger = LoggerFactory.getLogger("drop-stacker")

	override fun onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		DropStackerConfig.load()
		println("DropStacker initialized and config loaded!")
	}
}