package dev.suwizx.dropstacker.gametest

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

/**
 * Server gametests, run by `./gradlew runGameTest` (and so by `./gradlew build`).
 *
 * The save/load tests round-trip an entity through NBT in memory, which is the same path a world
 * save, chunk unload, `/data merge` or portal trip takes, without needing a server restart.
 */
class DropStackerGameTests : FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun oversizedPileSurvivesSaveLoad(helper: GameTestHelper) {
        val tag = save(pile(helper, 110))
        helper.assertTrue(tag.getCompound("Item").getInt("count") == 99, "saved Item.count should be capped at 99, tag: $tag")
        helper.assertTrue(tag.getInt(COUNT_KEY) == 110, "saved $COUNT_KEY should be 110, tag: $tag")

        val loaded = load(helper, tag)
        helper.assertTrue(!loaded.isRemoved, "loaded pile was discarded")
        helper.assertTrue(loaded.item.`is`(Items.DIAMOND), "loaded pile should be diamonds, got ${loaded.item}")
        helper.assertTrue(loaded.item.count == 110, "loaded pile should hold 110, got ${loaded.item.count}")
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun pileAtVanillaLimitHasNoExtraField(helper: GameTestHelper) {
        val tag = save(pile(helper, 99))
        helper.assertTrue(!tag.contains(COUNT_KEY), "a 99 pile needs no $COUNT_KEY, tag: $tag")

        val loaded = load(helper, tag)
        helper.assertTrue(loaded.item.count == 99, "loaded pile should hold 99, got ${loaded.item.count}")
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun thousandPileSurvivesSaveLoad(helper: GameTestHelper) {
        val loaded = load(helper, save(pile(helper, 1000)))
        helper.assertTrue(!loaded.isRemoved, "loaded pile was discarded")
        helper.assertTrue(loaded.item.count == 1000, "loaded pile should hold 1000, got ${loaded.item.count}")
        helper.succeed()
    }

    /** An admin's `/data merge ... {Item:{count:50}}` must win over a leftover extra field. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun editedCountBeatsStaleExtraField(helper: GameTestHelper) {
        val tag = save(pile(helper, 99))
        tag.getCompound("Item").putInt("count", 50)
        tag.putInt(COUNT_KEY, 110)

        val loaded = load(helper, tag)
        helper.assertTrue(loaded.item.count == 50, "edited count should win, got ${loaded.item.count}")
        helper.succeed()
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    fun mergeOnSpawn(helper: GameTestHelper) {
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        for (count in intArrayOf(40, 40, 30)) {
            helper.level.addFreshEntity(ItemEntity(helper.level, pos.x, pos.y, pos.z, ItemStack(Items.DIAMOND, count)))
        }

        val alive = helper.getEntities(EntityType.ITEM).filter { it.isAlive }
        helper.assertTrue(alive.size == 1, "expected one merged pile, got ${alive.size}: ${alive.map { it.item }}")
        helper.assertTrue(alive[0].item.count == 110, "merged pile should hold 110, got ${alive[0].item.count}")
        alive.forEach { it.discard() }
        helper.succeed()
    }

    private fun pile(helper: GameTestHelper, count: Int): ItemEntity {
        val pos = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        return ItemEntity(helper.level, pos.x, pos.y, pos.z, ItemStack(Items.DIAMOND, count))
    }

    /** Saves [entity] the way a chunk save does. On 1.21.1 an unencodable stack throws here. */
    private fun save(entity: ItemEntity): CompoundTag = entity.saveWithoutId(CompoundTag())

    private fun load(helper: GameTestHelper, tag: CompoundTag): ItemEntity {
        val entity = ItemEntity(EntityType.ITEM, helper.level)
        entity.load(tag)
        return entity
    }

    private companion object {
        const val COUNT_KEY = "DropStackerCount"
    }
}
