package dev.suwizx.dropstacker.accessor

/**
 * Duck-typed interface mixed into every `ItemEntity` by `ItemEntityMixin`, exposing the mod's
 * per-entity state to code outside the mixin.
 *
 * Note there is deliberately no age getter: vanilla's `ItemEntity.getAge()` is already public.
 */
interface ItemEntityAccessor {
    /** Vanilla's private `pickupDelay`. */
    fun dropstacker_pickupDelay(): Int

    fun dropstacker_setPickupDelay(value: Int)

    fun dropstacker_setAge(value: Int)

    /** Rebuilds the name tag, but only if the rendered text would differ from the last one applied. */
    fun dropstacker_refreshLabel()

    /**
     * True when this entity carries a custom name the mod did not write — a name-tagged drop, a
     * `/summon` with `CustomName`, or another mod's label.
     */
    fun dropstacker_hasForeignName(): Boolean

    /**
     * Resets the adaptive scan backoff, so this entity polls its neighbourhood again at the
     * configured [dev.suwizx.dropstacker.config.DropStackerConfig.scanInterval].
     */
    fun dropstacker_resetBackoff()
}
