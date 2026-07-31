package dev.suwizx.dropstacker.mixin

import dev.suwizx.dropstacker.accessor.ItemEntityAccessor
import dev.suwizx.dropstacker.config.DropStackerConfig
import dev.suwizx.dropstacker.config.LabelMode
import dev.suwizx.dropstacker.stack.StackEngine
import dev.suwizx.dropstacker.stack.StackLabel
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ItemEntity::class)
abstract class ItemEntityMixin : ItemEntityAccessor {
    @Shadow
    abstract fun getItem(): ItemStack

    @Shadow
    private var pickupDelay = 0

    @Shadow
    private var age = 0

    /** The last [StackLabel.labelKey] actually written to this entity's custom name. */
    @Unique
    private var dropstackerLabelKey: Long = Long.MIN_VALUE + 1

    /** Current adaptive scan interval, grown while scans keep finding nothing. */
    @Unique
    private var dropstackerBackoff: Int = 0

    /**
     * The exact component this mixin last wrote to `customName`.
     *
     * Comparing against it is how a foreign name is detected. Tracking "the name it had when we
     * first saw it" instead would be unreliable: entity NBT is read *after* `setItem` runs, so a
     * `/summon ... {CustomName:...}` replaces our label a moment after we apply it.
     */
    @Unique
    private var dropstackerAppliedLabel: Component? = null

    // --- ItemEntityAccessor ---

    override fun dropstacker_pickupDelay(): Int = this.pickupDelay

    override fun dropstacker_setPickupDelay(value: Int) {
        this.pickupDelay = value
    }

    override fun dropstacker_setAge(value: Int) {
        this.age = value
    }

    override fun dropstacker_resetBackoff() {
        this.dropstackerBackoff = DropStackerConfig.scanInterval
    }

    override fun dropstacker_hasForeignName(): Boolean {
        val current = (this as Any as ItemEntity).customName ?: return false
        return current != dropstackerAppliedLabel
    }

    override fun dropstacker_refreshLabel() {
        val entity = this as Any as ItemEntity
        val count = this.getItem().count

        // Never overwrite a genuine name.
        if (dropstacker_hasForeignName()) return

        val key = StackLabel.labelKey(entity, count)
        if (key == dropstackerLabelKey) return
        dropstackerLabelKey = key

        val label = StackLabel.build(entity, count)
        dropstackerAppliedLabel = label
        if (label == null) {
            entity.customName = null
            entity.isCustomNameVisible = false
        } else {
            entity.customName = label
            entity.isCustomNameVisible = true
        }
    }

    // --- injections ---

    @Inject(method = ["setItem"], at = [At("TAIL")])
    private fun dropstackerOnSetItem(stack: ItemStack, ci: CallbackInfo) {
        dropstacker_refreshLabel()
    }

    @Inject(method = ["tick"], at = [At("HEAD")])
    private fun dropstackerOnTick(ci: CallbackInfo) {
        val entity = this as Any as ItemEntity

        // Ordered cheapest-first: the old code read the synched ItemStack before this check, so
        // every client paid a synched-data lookup per item entity per tick for nothing.
        if (entity.level().isClientSide || !DropStackerConfig.enabled || !entity.isAlive) return

        if (DropStackerConfig.showDespawnTimer && DropStackerConfig.labelMode != LabelMode.NEVER) {
            // The rendered timer changes at most once per second, so checking more often than every
            // 20 ticks can only ever be wasted work. Staggered by entity id so a farm's worth of
            // drops don't all rebuild their labels on the same tick.
            if ((entity.tickCount + entity.id) % 20 == 0) {
                dropstacker_refreshLabel()
            }
        }

        if (dropstackerBackoff <= 0) dropstackerBackoff = DropStackerConfig.scanInterval
        if ((entity.tickCount + entity.id) % dropstackerBackoff != 0) return

        if (!StackEngine.isMergeable(entity, this.getItem())) return

        if (StackEngine.absorbNeighbours(entity) == 0) {
            // Nothing nearby: back off, up to maxScanInterval. This is only safe because new drops
            // push into existing piles on spawn (ServerLevelMixin) rather than waiting to be polled.
            val ceiling = DropStackerConfig.maxScanInterval.coerceAtLeast(DropStackerConfig.scanInterval)
            dropstackerBackoff = (dropstackerBackoff * 2).coerceAtMost(ceiling)
        }
    }

    /**
     * Vanilla runs its own neighbour scan every 40 ticks. [StackEngine] fully replaces it, so
     * leaving it enabled would mean two merge systems scanning the same entities.
     */
    @Inject(method = ["mergeWithNeighbours"], at = [At("HEAD")], cancellable = true)
    private fun dropstackerOnMergeWithNeighbours(ci: CallbackInfo) {
        if (DropStackerConfig.enabled) ci.cancel()
    }

    /** Widens vanilla's stack-size ceiling to the configured maximum, keeping every other guard. */
    @Inject(method = ["isMergable"], at = [At("HEAD")], cancellable = true)
    private fun dropstackerOnIsMergable(callback: CallbackInfoReturnable<Boolean>) {
        if (!DropStackerConfig.enabled) return
        val entity = this as Any as ItemEntity
        callback.returnValue = StackEngine.isMergeable(entity, this.getItem())
    }

    /**
     * Kept so callers outside the mod (other mods, or any vanilla path we do not cancel) still get
     * stack-size-aware merging rather than vanilla's 64 cap.
     */
    @Inject(method = ["tryToMerge"], at = [At("HEAD")], cancellable = true)
    private fun dropstackerOnTryToMerge(other: ItemEntity, ci: CallbackInfo) {
        if (!DropStackerConfig.enabled) return
        val entity = this as Any as ItemEntity
        val thisStack = this.getItem()
        if (!ItemStack.isSameItemSameComponents(thisStack, other.item)) return

        ci.cancel()

        val max = StackEngine.effectiveMax(thisStack)
        if (thisStack.count >= max) return
        val taken = StackEngine.drain(entity, other, max - thisStack.count)
        if (taken > 0) {
            // New instance so the synched stack actually changes; also refreshes the label.
            entity.item = thisStack.copyWithCount(thisStack.count + taken)
        }
    }
}
