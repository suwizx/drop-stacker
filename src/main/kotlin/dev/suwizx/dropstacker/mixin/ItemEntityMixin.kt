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

@Mixin(ItemEntity::class)
abstract class ItemEntityMixin : ItemEntityAccessor {
    @Shadow
    abstract fun getItem() : ItemStack

    @Shadow
    protected abstract fun tryToMerge(other: ItemEntity)

    @Shadow
    protected abstract fun isMergable(): Boolean

    @Shadow
    private var pickupDelay = 0

    @Shadow
    private var age = 0

    override fun invokeUpdateStackLabel(count: Int) {
        this.updateStackLabel(count)
    }

    override fun invokeGetAge(): Int = this.age

    override fun invokeGetPickupDelay(): Int = this.pickupDelay

    @Inject(method = ["setItem"], at = [At("TAIL")])
    private fun onSetItem(stack: ItemStack, ci: CallbackInfo) {
        this.updateStackLabel(stack.count)
    }

    @Inject(method = ["tick"], at = [At("HEAD")])
    private fun onTick(ci: CallbackInfo) {
        val entity = this as Any as ItemEntity
        val stack = entity.item

        if (entity.level().isClientSide || !entity.isAlive) return

        // Smart Throttling: Refresh the label based on urgency to save bandwidth
        if (DropStackerConfig.showDespawnTimer) {
            val interval = when {
                this.age <= -32768    -> 600 // Infinite: update every 30s just in case
                DropStackerConfig.despawnTicks - this.age < 600  -> 20  // Urgent (<30s): every second
                DropStackerConfig.despawnTicks - this.age < 2400 -> 60  // Warning (<2m): every 3 seconds
                else                  -> 100 // Healthy (>2m): every 5 seconds
            }

            // Stagger updates using entity ID to prevent network spikes. Using tickCount ensures constant progress.
            if ((entity.tickCount + entity.id) % interval == 0) {
                updateStackLabel(stack.count)
            }
        }

        if (stack.count < DropStackerConfig.maxStackSize
            && isMergable()
            && entity.tickCount % DropStackerConfig.scanInterval == 0
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
                && (it as Any as ItemEntityMixin).isMergable()
                && ItemStack.isSameItemSameComponents(stack, it.item)
            }.firstOrNull()

            if (targetEntity != null && entity.uuid.lessThan(targetEntity.uuid)) {
                this.tryToMerge(targetEntity)
            }
        }
    }


    @Inject(method = ["isMergable"], at = [At("HEAD")], cancellable = true)
    private fun onCanMerge(callback: CallbackInfoReturnable<Boolean>) {
        val entity = this as Any as ItemEntity
        // Bug fix: large stacks must still respect pickup delay and life state
        if (!entity.isAlive || this.pickupDelay > 0) {
            callback.setReturnValue(false)
            return
        }

        val stack = this.getItem()
        val count = stack.count
        val configMax = DropStackerConfig.maxStackSize

        // Blacklist check
        val itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()
        if (DropStackerConfig.blacklist.contains(itemId)) {
            callback.setReturnValue(false)
            return
        }

        when {
            count >= configMax -> callback.setReturnValue(false)
            count >= stack.maxStackSize -> callback.setReturnValue(true)
        }
    }

    @Inject(method = ["tryToMerge"], at = [At("HEAD")], cancellable = true)
    private fun onTryMerge(other: ItemEntity, ci: CallbackInfo) {
        val entity = this as Any as ItemEntity
        val thisStack = this.getItem()
        val otherStack = other.item
        val maxStackSize = DropStackerConfig.maxStackSize

        if (ItemStack.isSameItemSameComponents(thisStack, otherStack)) {
            ci.cancel()

            if (thisStack.count >= maxStackSize) return

            val canReceive = maxStackSize - thisStack.count
            val transferAmount = minOf(otherStack.count, canReceive)

            if (transferAmount > 0) {
                thisStack.count += transferAmount
                otherStack.count -= transferAmount

                // Vanilla Parity: Preserve the 'best' state
                // Keep the item that has more time left (smaller age)
                this.age = minOf(this.age, (other as ItemEntityAccessor).invokeGetAge())
                // Keep the longest pickup delay to prevent exploitation
                this.pickupDelay = maxOf(this.pickupDelay, (other as ItemEntityAccessor).invokeGetPickupDelay())

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
        val entity = this as Any as ItemEntity
        
        // Handle single item visibility and empty stacks
        if (count <= 0 || (count == 1 && DropStackerConfig.hideSingleItemLabel)) {
            entity.customName = null
            entity.isCustomNameVisible = false
            return
        }

        // Count color: configurable thresholds
        val countColor = when {
            count >= DropStackerConfig.countHighThreshold -> net.minecraft.ChatFormatting.RED
            count >= DropStackerConfig.countLowThreshold  -> net.minecraft.ChatFormatting.YELLOW
            else                                          -> net.minecraft.ChatFormatting.GREEN
        }

        val text = Component.empty()
            .append(Component.literal("[").withStyle(net.minecraft.ChatFormatting.GOLD))
            .append(Component.literal("×$count").withStyle(countColor))

        if (DropStackerConfig.showDespawnTimer) {
            if (this.age <= -32768) {
                text.append(Component.literal(" | ").withStyle(net.minecraft.ChatFormatting.AQUA))
                text.append(Component.literal("∞").withStyle(net.minecraft.ChatFormatting.GREEN))
            } else {
                val remainingTicks = DropStackerConfig.despawnTicks - this.age
                if (remainingTicks > 0) {
                    val totalSeconds = remainingTicks / 20
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    // Timer color: green plenty of time, yellow getting close, red urgent
                    val timerColor = when {
                        remainingTicks < 600  -> net.minecraft.ChatFormatting.RED
                        remainingTicks < 2400 -> net.minecraft.ChatFormatting.YELLOW
                        else                  -> net.minecraft.ChatFormatting.GREEN
                    }
                    text.append(Component.literal(" | ").withStyle(net.minecraft.ChatFormatting.AQUA))
                    text.append(Component.literal("${minutes}:${seconds.toString().padStart(2, '0')}").withStyle(timerColor))
                }
            }
        }

        text.append(Component.literal("]").withStyle(net.minecraft.ChatFormatting.GOLD))

        entity.customName = text
        entity.isCustomNameVisible = true
    }

}