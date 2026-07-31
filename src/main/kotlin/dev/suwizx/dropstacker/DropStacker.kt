package dev.suwizx.dropstacker

import dev.suwizx.dropstacker.command.DropStackerCommand
import dev.suwizx.dropstacker.config.DropStackerConfig
import dev.suwizx.dropstacker.config.ItemRules
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.LoggerFactory

object DropStacker : ModInitializer {
    private val logger = LoggerFactory.getLogger("drop-stacker")

    override fun onInitialize() {
        DropStackerConfig.load()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            DropStackerCommand.register(dispatcher)
        }

        // Item tags only exist once datapacks have loaded, and they change on /reload, so the
        // resolved blacklist/whitelist/override view has to be rebuilt whenever they do.
        ServerLifecycleEvents.SERVER_STARTED.register { _ -> ItemRules.invalidate() }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, _ -> ItemRules.invalidate() }

        logger.info("Drop Stacker initialized (max stack size {})", DropStackerConfig.maxStackSize)
    }
}
