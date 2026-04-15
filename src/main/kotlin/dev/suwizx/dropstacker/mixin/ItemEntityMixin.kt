package dev.suwizx.dropstacker.mixin

import dev.suwizx.dropstacker.accessor.ItemEntityAccessor
import dev.suwizx.dropstacker.config.DropStackerConfig
import net.minecraft.world.entity.Entity
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
        if (!entity.level().isClientSide
            && entity.isAlive
            && stack.count < DropStackerConfig.maxStackSize
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
        // Only block merging when at max stack size.
        // Do NOT force true — let vanilla run its own checks (e.g. INFINITE_PICKUP_DELAY)
        // so items that should never merge (e.g. from /give) are still protected.
        if (this.getItem().count >= DropStackerConfig.maxStackSize) {
            callback.setReturnValue(false)
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

    private fun updateStackLabel(count : Int){
        val entity = this as Entity
        if(count <= 0){
            entity.customName =null ;
            entity.isCustomNameVisible = false
            return
        }

        val color = if (count > 64) "§e" else "§f"

        entity.customName = Component.literal("$color×$count")
        entity.isCustomNameVisible = true
    }


}