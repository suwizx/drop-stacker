package dev.suwizx.dropstacker.config

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/**
 * The config's blacklist / whitelist / per-item overrides, resolved from strings to registry
 * objects exactly once.
 *
 * This exists because the previous implementation called
 * `BuiltInRegistries.ITEM.getKey(stack.item).toString()` inside the neighbour-scan predicate — a
 * String allocation for every candidate of every scan, even when the blacklist was empty. Here the
 * hot path is a reference-identity set lookup.
 *
 * Entries may be exact ids (`minecraft:elytra`) or tags (`#minecraft:shulker_boxes`). Tags are only
 * populated once the server has loaded its tag data, so resolution is deferred until first use and
 * re-run whenever the config or the server's tags change.
 */
object ItemRules {
    private val logger = LoggerFactory.getLogger("drop-stacker-rules")

    private var resolved = false

    private var blockedItems: Set<Item> = emptySet()
    private var blockedTags: List<TagKey<Item>> = emptyList()

    private var allowedItems: Set<Item> = emptySet()
    private var allowedTags: List<TagKey<Item>> = emptyList()
    private var whitelistActive = false

    private var itemOverrides: Map<Item, Int> = emptyMap()
    private var tagOverrides: List<Pair<TagKey<Item>, Int>> = emptyList()

    /** Marks the resolved view stale; the next query rebuilds it. */
    fun invalidate() {
        resolved = false
    }

    private fun ensureResolved() {
        if (resolved) return
        resolved = true

        val blockedI = HashSet<Item>()
        val blockedT = ArrayList<TagKey<Item>>()
        parseInto(DropStackerConfig.blacklist, blockedI, blockedT)
        blockedItems = blockedI
        blockedTags = blockedT

        val allowedI = HashSet<Item>()
        val allowedT = ArrayList<TagKey<Item>>()
        parseInto(DropStackerConfig.whitelist, allowedI, allowedT)
        allowedItems = allowedI
        allowedTags = allowedT
        whitelistActive = allowedI.isNotEmpty() || allowedT.isNotEmpty()

        val overrideItems = HashMap<Item, Int>()
        val overrideTags = ArrayList<Pair<TagKey<Item>, Int>>()
        for ((key, limit) in DropStackerConfig.overrides) {
            if (limit < 1) {
                logger.warn("Ignoring override '{}': limit must be at least 1 (was {})", key, limit)
                continue
            }
            val items = HashSet<Item>()
            val tags = ArrayList<TagKey<Item>>()
            parseInto(listOf(key), items, tags)
            items.forEach { overrideItems[it] = limit }
            tags.forEach { overrideTags += it to limit }
        }
        itemOverrides = overrideItems
        tagOverrides = overrideTags
    }

    private fun parseInto(entries: List<String>, items: MutableSet<Item>, tags: MutableList<TagKey<Item>>) {
        for (raw in entries) {
            val entry = raw.trim()
            if (entry.isEmpty()) continue

            if (entry.startsWith("#")) {
                val id = ResourceLocation.tryParse(entry.substring(1))
                if (id == null) {
                    logger.warn("Ignoring malformed item tag '{}'", entry)
                    continue
                }
                tags += TagKey.create(Registries.ITEM, id)
            } else {
                val id = ResourceLocation.tryParse(entry)
                if (id == null) {
                    logger.warn("Ignoring malformed item id '{}'", entry)
                    continue
                }
                val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null)
                if (item == null) {
                    logger.warn("Ignoring unknown item '{}'", entry)
                    continue
                }
                items += item
            }
        }
    }

    /** True when [stack] is allowed to take part in stacking at all. */
    fun isStackable(stack: ItemStack): Boolean {
        ensureResolved()

        if (blockedItems.isNotEmpty() && stack.item in blockedItems) return false
        if (blockedTags.isNotEmpty() && blockedTags.any { stack.`is`(it) }) return false

        if (!whitelistActive) return true
        if (allowedItems.isNotEmpty() && stack.item in allowedItems) return true
        return allowedTags.isNotEmpty() && allowedTags.any { stack.`is`(it) }
    }

    /** The maximum count [stack] may be stacked to, honouring per-item overrides. */
    fun maxStackSize(stack: ItemStack): Int {
        ensureResolved()

        if (itemOverrides.isNotEmpty()) {
            itemOverrides[stack.item]?.let { return it }
        }
        if (tagOverrides.isNotEmpty()) {
            for ((tag, limit) in tagOverrides) {
                if (stack.`is`(tag)) return limit
            }
        }
        return DropStackerConfig.maxStackSize
    }
}
