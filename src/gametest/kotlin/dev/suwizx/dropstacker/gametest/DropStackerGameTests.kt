package dev.suwizx.dropstacker.gametest

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.phys.Vec3

/**
 * Server gametests, run by `./gradlew runGameTest` (and so by `./gradlew build`).
 *
 * The save/load tests round-trip an entity through NBT in memory, which is the same path a world
 * save, chunk unload, `/data merge` or portal trip takes, without needing a server restart.
 */
class DropStackerGameTests {
    @GameTest
    fun oversizedPileSurvivesSaveLoad(helper: GameTestHelper) {
        val tag = save(helper, pile(helper, 110))
        helper.assertTrue(tag.getCompoundOrEmpty("Item").getIntOr("count", -1) == 99, "saved Item.count should be capped at 99, tag: $tag")
        helper.assertTrue(tag.getIntOr(COUNT_KEY, -1) == 110, "saved $COUNT_KEY should be 110, tag: $tag")

        val loaded = load(helper, tag)
        helper.assertTrue(!loaded.isRemoved, "loaded pile was discarded")
        helper.assertTrue(loaded.item.`is`(Items.DIAMOND), "loaded pile should be diamonds, got ${loaded.item}")
        helper.assertTrue(loaded.item.count == 110, "loaded pile should hold 110, got ${loaded.item.count}")
        helper.succeed()
    }

    @GameTest
    fun pileAtVanillaLimitHasNoExtraField(helper: GameTestHelper) {
        val tag = save(helper, pile(helper, 99))
        helper.assertTrue(!tag.contains(COUNT_KEY), "a 99 pile needs no $COUNT_KEY, tag: $tag")

        val loaded = load(helper, tag)
        helper.assertTrue(loaded.item.count == 99, "loaded pile should hold 99, got ${loaded.item.count}")
        helper.succeed()
    }

    @GameTest
    fun thousandPileSurvivesSaveLoad(helper: GameTestHelper) {
        val loaded = load(helper, save(helper, pile(helper, 1000)))
        helper.assertTrue(!loaded.isRemoved, "loaded pile was discarded")
        helper.assertTrue(loaded.item.count == 1000, "loaded pile should hold 1000, got ${loaded.item.count}")
        helper.succeed()
    }

    /** An admin's `/data merge ... {Item:{count:50}}` must win over a leftover extra field. */
    @GameTest
    fun editedCountBeatsStaleExtraField(helper: GameTestHelper) {
        val tag = save(helper, pile(helper, 99))
        tag.getCompoundOrEmpty("Item").putInt("count", 50)
        tag.putInt(COUNT_KEY, 110)

        val loaded = load(helper, tag)
        helper.assertTrue(loaded.item.count == 50, "edited count should win, got ${loaded.item.count}")
        helper.succeed()
    }

    @GameTest
    fun mergeOnSpawn(helper: GameTestHelper) {
        val pos = helper.absoluteVec(Vec3(2.5, 2.0, 2.5))
        for (count in intArrayOf(40, 40, 30)) {
            helper.level.addFreshEntity(ItemEntity(helper.level, pos.x, pos.y, pos.z, ItemStack(Items.DIAMOND, count)))
        }

        val alive = helper.getEntities(EntityTypes.ITEM).filter { it.isAlive }
        helper.assertTrue(alive.size == 1, "expected one merged pile, got ${alive.size}: ${alive.map { it.item }}")
        helper.assertTrue(alive[0].item.count == 110, "merged pile should hold 110, got ${alive[0].item.count}")
        alive.forEach { it.discard() }
        helper.succeed()
    }

    private fun pile(helper: GameTestHelper, count: Int): ItemEntity {
        val pos = helper.absoluteVec(Vec3(1.5, 2.0, 1.5))
        return ItemEntity(helper.level, pos.x, pos.y, pos.z, ItemStack(Items.DIAMOND, count))
    }

    /** Saves [entity] the way a chunk save does, failing the test on any serialization problem. */
    private fun save(helper: GameTestHelper, entity: ItemEntity): CompoundTag {
        val problems = ProblemReporter.Collector()
        val output = TagValueOutput.createWithContext(problems, helper.level.registryAccess())
        entity.saveWithoutId(output)
        helper.assertTrue(problems.isEmpty, "save reported problems: ${problems.treeReport}")
        return output.buildResult()
    }

    private fun load(helper: GameTestHelper, tag: CompoundTag): ItemEntity {
        val entity = ItemEntity(EntityTypes.ITEM, helper.level)
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.level.registryAccess(), tag))
        return entity
    }

    private companion object {
        const val COUNT_KEY = "DropStackerCount"
    }
}
