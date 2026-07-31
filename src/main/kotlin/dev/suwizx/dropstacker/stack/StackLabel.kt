package dev.suwizx.dropstacker.stack

import dev.suwizx.dropstacker.config.DropStackerConfig
import dev.suwizx.dropstacker.config.LabelMode
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity

/**
 * Builds the `[×count | m:ss]` name tag shown on a stacked item entity.
 *
 * The mod is server-side only — a vanilla client must still see stack counts — so the label rides
 * on the entity's custom name rather than being drawn client-side. That makes every refresh a
 * synced-data write and a packet to every tracking player, so the hot path here is [labelKey]:
 * a cheap scalar that changes exactly when the *rendered text* would change. The mixin only
 * rebuilds the component tree when that key moves.
 */
object StackLabel {
    /** [labelKey] value meaning "no label should be shown at all". */
    const val NO_LABEL: Long = Long.MIN_VALUE

    /** Vanilla's `ItemEntity.INFINITE_LIFETIME` sentinel. */
    const val INFINITE_LIFETIME: Int = -32768

    /**
     * A scalar identifying the text [build] would produce for this entity right now.
     *
     * Packs the count with the whole-second remainder of the despawn timer, which is the finest
     * granularity the rendered text can distinguish.
     */
    fun labelKey(entity: ItemEntity, count: Int): Long {
        if (!shouldLabel(count)) return NO_LABEL

        val seconds = when {
            !DropStackerConfig.showDespawnTimer -> -1L
            entity.age <= INFINITE_LIFETIME -> -2L
            else -> {
                val remaining = DropStackerConfig.despawnTicks - entity.age
                if (remaining > 0) (remaining / 20).toLong() else -1L
            }
        }
        return (count.toLong() shl 32) xor (seconds and 0xFFFFFFFFL)
    }

    private fun shouldLabel(count: Int): Boolean = when (DropStackerConfig.labelMode) {
        LabelMode.NEVER -> false
        LabelMode.STACKED_ONLY -> count > 1
        LabelMode.ALWAYS -> !(count <= 0 || (count == 1 && DropStackerConfig.hideSingleItemLabel))
    }

    /** The label component, or `null` when this entity should carry no label. */
    fun build(entity: ItemEntity, count: Int): Component? {
        if (!shouldLabel(count)) return null

        val countColor = when {
            count >= DropStackerConfig.countHighThreshold -> ChatFormatting.RED
            count >= DropStackerConfig.countLowThreshold -> ChatFormatting.YELLOW
            else -> ChatFormatting.GREEN
        }

        val text = Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("×$count").withStyle(countColor))

        if (DropStackerConfig.showDespawnTimer) {
            if (entity.age <= INFINITE_LIFETIME) {
                text.append(Component.literal(" | ").withStyle(ChatFormatting.AQUA))
                text.append(Component.literal("∞").withStyle(ChatFormatting.GREEN))
            } else {
                val remainingTicks = DropStackerConfig.despawnTicks - entity.age
                if (remainingTicks > 0) {
                    val totalSeconds = remainingTicks / 20
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    val timerColor = when {
                        remainingTicks < 600 -> ChatFormatting.RED
                        remainingTicks < 2400 -> ChatFormatting.YELLOW
                        else -> ChatFormatting.GREEN
                    }
                    text.append(Component.literal(" | ").withStyle(ChatFormatting.AQUA))
                    text.append(
                        Component.literal("$minutes:${seconds.toString().padStart(2, '0')}")
                            .withStyle(timerColor)
                    )
                }
            }
        }

        text.append(Component.literal("]").withStyle(ChatFormatting.GOLD))
        return text
    }
}
