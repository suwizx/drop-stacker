package dev.suwizx.dropstacker.mixin

import dev.suwizx.dropstacker.accessor.ItemEntityAccessor
import dev.suwizx.dropstacker.config.DropStackerConfig
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

private const val DESPAWN_TICKS = 6000

@Mixin(ItemEntity::class)
abstract class ItemEntityMixin : ItemEntityAccessor {
    @Shadow
    abstract fun getItem() : ItemStack

    @Shadow
    protected abstract fun tryToMerge(other: ItemEntity)

    @Shadow
    protected abstract fun isMergable(): Boolean

    override fun invokeUpdateStackLabel(count: Int) {
        this.updateStackLabel(count)
    }

    @Inject(method = ["setItem"], at = [At("TAIL")])
    private fun onSetItem(stack: ItemStack, ci: CallbackInfo) {
        this.updateStackLabel(stack.count)
    }

    @Inject(method = ["tick"], at = [At("HEAD")])
    private fun onTick(ci: CallbackInfo) {
        val entity = this as ItemEntity
        val stack = entity.item

        if (entity.level().isClientSide || !entity.isAlive) return

        // Refresh the label once per second so the despawn countdown ticks down smoothly
        if (DropStackerConfig.showDespawnTimer && entity.age % 20 == 0) {
            updateStackLabel(stack.count)
        }

        if (stack.count < DropStackerConfig.maxStackSize
            && isMergable()
            && entity.age % DropStackerConfig.scanInterval == 0
        ) {
            val targetEntity = entity.level().getEntitiesOfClass(
                ItemEntity::class.java,
                entity.boundingBox.inflate(
                    DropStackerConfig.scanRadiusX,
                    DropStackerConfig.scanRadiusY,
                    DropStackerConfig.scanRadiusZ
                )
            ) {
                it != entity
                && it.isAlive
                && (it as ItemEntityMixin).isMergable()
                && ItemStack.isSameItemSameComponents(stack, it.item)
            }.firstOrNull()

            if (targetEntity != null && entity.uuid.lessThan(targetEntity.uuid)) {
                this.tryToMerge(targetEntity)
            }
        }
    }


    @Inject(method = ["isMergable"], at = [At("HEAD")], cancellable = true)
    private fun onCanMerge(callback: CallbackInfoReturnable<Boolean>) {
        val stack = this.getItem()
        val count = stack.count
        val configMax = DropStackerConfig.maxStackSize
        when {
            // At or above our configured limit — block merging entirely
            count >= configMax -> callback.setReturnValue(false)
            // Above vanilla's per-item limit (usually 64) but below ours — force allow
            // so vanilla's own "count < maxStackSize" check doesn't cap us at 64.
            // INFINITE_PICKUP_DELAY items never reach here (count is always < 64 when spawned).
            count >= stack.maxStackSize -> callback.setReturnValue(true)
            // Below vanilla limit — let vanilla run so INFINITE_PICKUP_DELAY is still respected
        }
    }

    @Inject(method = ["tryToMerge"], at = [At("HEAD")], cancellable = true)
    private fun onTryMerge(other: ItemEntity, ci: CallbackInfo) {
        val thisStack = this.getItem()
        val otherStack = other.item
        val maxStackSize = DropStackerConfig.maxStackSize

        if (ItemStack.isSameItemSameComponents(thisStack, otherStack)) {
            // Cancel vanilla for all same-type merges — we own this path entirely.
            // Must cancel before the early return so vanilla can never bypass maxStackSize.
            ci.cancel()

            if (thisStack.count >= maxStackSize) return

            val canReceive = maxStackSize - thisStack.count
            val transferAmount = minOf(otherStack.count, canReceive)

            if (transferAmount > 0) {
                thisStack.count += transferAmount
                otherStack.count -= transferAmount

                updateStackLabel(thisStack.count)

                if (otherStack.isEmpty) {
                    other.discard()
                } else {
                    (other as ItemEntityAccessor).invokeUpdateStackLabel(otherStack.count)
                }
            }
        }
    }

    private fun java.util.UUID.lessThan(other: java.util.UUID): Boolean {
        return this.compareTo(other) < 0
    }

    private fun updateStackLabel(count: Int) {
        val entity = this as ItemEntity
        if (count <= 0) {
            entity.customName = null
            entity.isCustomNameVisible = false
            return
        }

        // Count color: red for huge stacks, yellow for large, green for small
        val countColor = when {
            count > 500 -> "§c"
            count > 64  -> "§e"
            else        -> "§a"
        }
        val text = StringBuilder("§6[§r$countColor×$count")

        if (DropStackerConfig.showDespawnTimer) {
            val remainingTicks = DESPAWN_TICKS - entity.age
            if (remainingTicks > 0) {
                val totalSeconds = remainingTicks / 20
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                // Timer color: green plenty of time, yellow getting close, red urgent
                val timerColor = when {
                    remainingTicks < 600  -> "§c"
                    remainingTicks < 2400 -> "§e"
                    else                  -> "§a"
                }
                text.append(" §b| §r$timerColor${minutes}:${seconds.toString().padStart(2, '0')}")
            }
        }

        text.append("§6]")

        entity.customName = Component.literal(text.toString())
        entity.isCustomNameVisible = true
    }

}