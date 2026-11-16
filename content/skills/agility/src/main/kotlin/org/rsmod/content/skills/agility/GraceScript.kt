package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GraceScript
@Inject
constructor(private val shops: Shops, private val shopOps: ShopOperationMap) : PluginScript() {
    override fun ScriptContext.startup() {
        shopOps.costOf(CURRENCY) { it.cost.coerceAtLeast(1) }
        onOpNpc1(GRACE) { startDialogue(it.npc) { greeting() } }
        onOpNpc3(GRACE) { player.openGraceShop() }
        onOpNpc5(GRACE) { toggleCounter(it.npc) }
    }

    private suspend fun Dialogue.greeting() {
        chatNpc(neutral, "What can I do for you?")
        val choice =
            choice4(
                "I don't know. What can you do for me?",
                Choice.Explain,
                "Can I see what you're selling?",
                Choice.Shop,
                "Can I toggle my lap counter?",
                Choice.Toggle,
                "I'm alright, thanks.",
                Choice.Leave,
            )
        when (choice) {
            Choice.Explain -> explain()
            Choice.Shop -> {
                chatPlayer(neutral, "Can I see what you're selling?")
                player.openGraceShop()
            }
            Choice.Toggle -> {
                chatPlayer(neutral, "Can I toggle my lap counter?")
                toggleLapMessages()
            }
            Choice.Leave -> chatPlayer(neutral, "I'm alright, thanks.")
        }
    }

    private suspend fun Dialogue.explain() {
        chatPlayer(neutral, "I don't know. What can you do for me?")
        chatNpc(
            happy,
            "A good question indeed! I'm selling special clothing for Agility enthusiasts.",
        )
        chatNpc(
            neutral,
            "Sometimes, when you're exploring the hidden rooftops of our cities, you'll find " +
                "one of my Marks.",
        )
        chatNpc(
            neutral,
            "Once you've got enough Marks, I'll exchange them for my Graceful clothing. So, " +
                "does that interest you?",
        )
        val shop = choice2("Can I see what you're selling?", true, "I'm alright, thanks.", false)
        if (shop) {
            chatPlayer(neutral, "Can I see what you're selling?")
            player.openGraceShop()
        }
    }

    private suspend fun ProtectedAccess.toggleCounter(npc: Npc) {
        startDialogue(npc) { toggleLapMessages() }
    }

    private suspend fun Dialogue.toggleLapMessages() {
        if (player.lapMessagesOff) {
            player.lapMessagesOff = false
            chatNpc(
                neutral,
                "Sure, I've turned on your lap counter, you'll get a message when you complete " +
                    "a lap on a rooftop course. To help you keep track.",
            )
        } else {
            player.lapMessagesOff = true
            chatNpc(
                neutral,
                "Sure, I've disabled your lap counter messages, if you want to turn it back on, " +
                    "so you can see your total laps after you finish one, speak to me again.",
            )
        }
    }

    private fun Player.openGraceShop() {
        shops.open(
            player = this,
            title = "Grace's Graceful Clothing",
            shopInv = SHOP_INV,
            buyPercentage = 80.0,
            sellPercentage = 100.0,
            changePercentage = 0.0,
            currency = CURRENCY,
        )
    }

    private enum class Choice {
        Explain,
        Shop,
        Toggle,
        Leave,
    }

    private companion object {
        const val GRACE = "npc.rooftops_grace"
        const val SHOP_INV = "inv.agility_grace_shop"
        const val CURRENCY = "currency.mark_of_grace"
    }
}
