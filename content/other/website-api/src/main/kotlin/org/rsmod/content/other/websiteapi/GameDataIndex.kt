package org.rsmod.content.other.websiteapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import io.github.classgraph.ClassGraph
import org.rsmod.api.droptable.toml.DropTableTomlTextFixer
import org.rsmod.api.droptable.toml.TomlDropTableDef

/**
 * Searchable items and monsters for the website's database pages, built once from the cache and
 * the drop-table TOML files on the classpath.
 */
class GameDataIndex private constructor(
    private val items: List<ItemSummary>,
    private val itemDetails: Map<Int, ItemServerType>,
    private val monsters: List<MonsterSummary>,
    private val monsterDrops: Map<Int, List<MonsterDrop>>,
    private val droppedBy: Map<Int, List<DropSource>>,
) {
    fun searchItems(query: String, limit: Int): List<ItemSummary> = search(items, query, limit) { it.name }

    fun searchMonsters(query: String, limit: Int): List<MonsterSummary> =
        search(monsters, query, limit) { it.name }

    fun item(id: Int): ItemView? {
        val type = itemDetails[id] ?: return null
        return ItemView(
            id = type.id,
            name = type.name,
            examine = type.examine,
            value = type.cost.coerceAtLeast(0),
            highAlch = type.highAlch.coerceAtLeast(0),
            lowAlch = type.lowAlch.coerceAtLeast(0),
            weight = type.weight,
            tradeable = type.tradeable,
            stackable = type.stackable,
            equipable = type.isEquipable,
            droppedBy = droppedBy[id].orEmpty().sortedByDescending { it.chance },
        )
    }

    fun monster(id: Int): MonsterView? {
        val drops = monsterDrops[id] ?: return null
        val type = ServerCacheManager.getNpc(id) ?: return null
        return MonsterView(
            id = id,
            name = type.name,
            combatLevel = type.combatLevel,
            hitpoints = type.hitpoints,
            attack = type.attack,
            strength = type.strength,
            defence = type.defence,
            magic = type.magic,
            ranged = type.ranged,
            size = type.size,
            drops = drops,
        )
    }

    private fun <T> search(all: List<T>, query: String, limit: Int, name: (T) -> String): List<T> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return all
            .filter { name(it).lowercase().contains(needle) }
            .sortedWith(
                compareBy<T> { !name(it).lowercase().startsWith(needle) }
                    .thenBy { name(it).length }
                    .thenBy { name(it) }
            )
            .take(limit)
    }

    companion object {
        private val logger = InlineLogger()
        private const val DROP_TABLE_ROOT = "drops/tables"

        fun build(): GameDataIndex {
            val items =
                ServerCacheManager.getItems().values.filter { type ->
                    type.name.isNotBlank() && type.name != "null" && !type.isCert && !type.isPlaceholder
                }
            val itemDetails = items.associateBy { it.id }
            val droppedBy = HashMap<Int, MutableList<DropSource>>()
            val monsterDrops = HashMap<Int, List<MonsterDrop>>()
            val monsterSummaries = LinkedHashMap<String, MonsterSummary>()

            for (def in loadDropTables()) {
                val lines = DropRates.lines(def)
                val drops = lines.map { line -> MonsterDrop.of(line, line.obj?.let(::objId)) }
                for (npcName in def.npcs) {
                    val npcId = rscm(npcName, RSCMType.NPC) ?: continue
                    val npc = ServerCacheManager.getNpc(npcId) ?: continue
                    if (npc.name.isBlank() || npc.name == "null") continue
                    monsterDrops.putIfAbsent(npcId, drops)
                    monsterSummaries.putIfAbsent(
                        "${npc.name.lowercase()}:${npc.combatLevel}",
                        MonsterSummary(npcId, npc.name, npc.combatLevel),
                    )
                    for (drop in drops) {
                        val itemId = drop.itemId ?: continue
                        droppedBy
                            .getOrPut(itemId) { mutableListOf() }
                            .add(DropSource(npcId, npc.name, npc.combatLevel, drop.quantity, drop.chance, drop.rarity))
                    }
                }
            }
            val dedupedDroppedBy =
                droppedBy.mapValues { (_, sources) ->
                    sources.distinctBy { "${it.monster.lowercase()}:${it.combatLevel}:${it.quantity}:${it.rarity}" }
                }
            logger.info {
                "Website database: ${items.size} items, ${monsterSummaries.size} monsters with drop tables"
            }
            return GameDataIndex(
                items = items.map { ItemSummary(it.id, it.name, it.cost.coerceAtLeast(0)) },
                itemDetails = itemDetails,
                monsters = monsterSummaries.values.toList(),
                monsterDrops = monsterDrops,
                droppedBy = dedupedDroppedBy,
            )
        }

        private fun loadDropTables(): List<TomlDropTableDef> {
            val mapper =
                ObjectMapper(TomlFactory())
                    .registerKotlinModule()
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            return ClassGraph().acceptPaths(DROP_TABLE_ROOT).scan().use { scan ->
                scan.allResources
                    .filter { it.path.endsWith(".toml") }
                    .mapNotNull { resource ->
                        runCatching {
                                val raw = DropTableTomlTextFixer.hoistTableLevelKeys(resource.contentAsString)
                                mapper.readValue<TomlDropTableDef>(raw)
                            }
                            .onFailure { logger.warn { "Skipping drop table ${resource.path}: ${it.message}" } }
                            .getOrNull()
                    }
            }
        }

        private fun objId(internal: String): Int? = rscm(internal, RSCMType.OBJ)

        private fun rscm(internal: String, type: RSCMType): Int? =
            runCatching { internal.asRSCM(type) }.getOrNull()
    }
}

data class ItemSummary(val id: Int, val name: String, val value: Int)

data class MonsterSummary(val id: Int, val name: String, val combatLevel: Int)

data class DropSource(
    val monsterId: Int,
    val monster: String,
    val combatLevel: Int,
    val quantity: String,
    val chance: Double,
    val rarity: String,
)

data class MonsterDrop(
    val itemId: Int?,
    val name: String,
    val quantity: String,
    val chance: Double,
    val rarity: String,
    val section: String,
) {
    companion object {
        fun of(line: DropLine, itemId: Int?): MonsterDrop {
            val name =
                when {
                    line.shared != null -> sharedLabel(line.shared)
                    itemId != null -> ServerCacheManager.getItem(itemId)?.name ?: line.obj.orEmpty()
                    else -> line.obj.orEmpty().removePrefix("obj.")
                }
            return MonsterDrop(itemId, name, line.quantity, line.chance, line.rarity, line.section)
        }

        private fun sharedLabel(shared: String): String {
            val words = shared.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
            return words.replaceFirstChar(Char::uppercaseChar) + " drop table"
        }
    }
}

data class ItemView(
    val id: Int,
    val name: String,
    val examine: String,
    val value: Int,
    val highAlch: Int,
    val lowAlch: Int,
    val weight: Double,
    val tradeable: Boolean,
    val stackable: Boolean,
    val equipable: Boolean,
    val droppedBy: List<DropSource>,
)

data class MonsterView(
    val id: Int,
    val name: String,
    val combatLevel: Int,
    val hitpoints: Int,
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val magic: Int,
    val ranged: Int,
    val size: Int,
    val drops: List<MonsterDrop>,
)
