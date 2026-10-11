package org.rsmod.content.other.welcome

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftQueue
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val INTERFACE = "interface.welcome_terms"

private var Player.welcomeAccepted by boolVarBit("varbit.welcome_terms_accepted")
private var Player.welcomeTicked by boolVarBit("varbit.welcome_terms_ticked")

class WelcomeScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            if (!player.welcomeAccepted) {
                player.softQueue("queue.welcome_terms_open", 2)
            }
        }

        onPlayerSoftQueue("queue.welcome_terms_open") { player.openWelcome() }

        onCommand("welcome") {
            desc = "Reopen the welcome interface"
            cheat { player.openWelcome() }
        }

        onIfModalButton("component.welcome_terms:checkbox") { toggleTick() }

        onIfModalButton("component.welcome_terms:accept") { accept() }
    }

    private fun Player.openWelcome() {
        welcomeTicked = false
        ifOpenMainModal(INTERFACE, eventBus)
        drawWelcome()
    }

    private fun ProtectedAccess.toggleTick() {
        player.welcomeTicked = !player.welcomeTicked
        player.drawWelcome()
    }

    private fun ProtectedAccess.accept() {
        if (!player.welcomeTicked) {
            mes("You need to tick the box before you can continue.")
            return
        }
        player.welcomeAccepted = true
        ifClose()
        mes("Thanks for accepting. Enjoy your stay!")
    }

    private fun Player.drawWelcome() {
        runClientScript(
            "clientscript.welcome_terms_draw".asRSCM(RSCMType.CLIENTSCRIPT),
            if (welcomeTicked) 1 else 0,
        )
    }
}
