package dev.suwizx.dropstacker.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import dev.suwizx.dropstacker.config.DropStackerConfig
import dev.suwizx.dropstacker.stack.StackEngine
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * `/dropstacker` — runtime control so server operators can tune stacking without a restart.
 */
object DropStackerCommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("dropstacker")
                // LEVEL_GAMEMASTERS is the permission-level-2 check.
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("reload").executes { ctx ->
                        DropStackerConfig.load()
                        reply(ctx.source, "Config reloaded.", ChatFormatting.GREEN)
                        1
                    }
                )
                .then(
                    Commands.literal("stats").executes { ctx ->
                        val src = ctx.source
                        reply(src, "Drop Stacker stats", ChatFormatting.GOLD)
                        reply(src, "  merge passes: ${StackEngine.mergesPerformed}", ChatFormatting.GRAY)
                        reply(src, "  entities collapsed: ${StackEngine.entitiesCollapsed}", ChatFormatting.GRAY)
                        reply(src, "  enabled: ${DropStackerConfig.enabled}", ChatFormatting.GRAY)
                        reply(src, "  mergeOnSpawn: ${DropStackerConfig.mergeOnSpawn}", ChatFormatting.GRAY)
                        reply(src, "  maxStackSize: ${DropStackerConfig.maxStackSize}", ChatFormatting.GRAY)
                        reply(
                            src,
                            "  scan: every ${DropStackerConfig.scanInterval}t " +
                                "(idle backoff to ${DropStackerConfig.maxScanInterval}t), " +
                                "radius ${DropStackerConfig.radius}/${DropStackerConfig.verticalRadius}",
                            ChatFormatting.GRAY,
                        )
                        1
                    }
                )
                .then(
                    Commands.literal("resetstats").executes { ctx ->
                        StackEngine.resetStats()
                        reply(ctx.source, "Stats reset.", ChatFormatting.GREEN)
                        1
                    }
                )
                .then(
                    Commands.literal("toggle").executes { ctx ->
                        DropStackerConfig.enabled = !DropStackerConfig.enabled
                        DropStackerConfig.save()
                        reply(
                            ctx.source,
                            "Drop Stacker is now ${if (DropStackerConfig.enabled) "enabled" else "disabled"}.",
                            if (DropStackerConfig.enabled) ChatFormatting.GREEN else ChatFormatting.RED,
                        )
                        1
                    }
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.literal("maxStackSize")
                                .then(
                                    Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes { ctx ->
                                            DropStackerConfig.maxStackSize =
                                                IntegerArgumentType.getInteger(ctx, "value")
                                            saved(ctx.source, "maxStackSize", DropStackerConfig.maxStackSize)
                                        }
                                )
                        )
                        .then(
                            Commands.literal("scanInterval")
                                .then(
                                    Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes { ctx ->
                                            DropStackerConfig.scanInterval =
                                                IntegerArgumentType.getInteger(ctx, "value")
                                            if (DropStackerConfig.maxScanInterval < DropStackerConfig.scanInterval) {
                                                DropStackerConfig.maxScanInterval = DropStackerConfig.scanInterval
                                            }
                                            saved(ctx.source, "scanInterval", DropStackerConfig.scanInterval)
                                        }
                                )
                        )
                        .then(
                            Commands.literal("radius")
                                .then(
                                    Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 64.0))
                                        .executes { ctx ->
                                            DropStackerConfig.radius =
                                                DoubleArgumentType.getDouble(ctx, "value")
                                            saved(ctx.source, "radius", DropStackerConfig.radius)
                                        }
                                )
                        )
                        .then(
                            Commands.literal("verticalRadius")
                                .then(
                                    Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 64.0))
                                        .executes { ctx ->
                                            DropStackerConfig.verticalRadius =
                                                DoubleArgumentType.getDouble(ctx, "value")
                                            saved(ctx.source, "verticalRadius", DropStackerConfig.verticalRadius)
                                        }
                                )
                        )
                )
        )
    }

    private fun saved(source: CommandSourceStack, field: String, value: Any): Int {
        DropStackerConfig.save()
        reply(source, "$field = $value", ChatFormatting.GREEN)
        return 1
    }

    private fun reply(source: CommandSourceStack, message: String, color: ChatFormatting) {
        source.sendSuccess({ Component.literal(message).withStyle(color) }, false)
    }
}
