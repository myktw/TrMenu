package me.arasple.mc.trmenu.listeners.bukkit

import io.izzel.taboolib.module.packet.Packet
import io.izzel.taboolib.module.packet.TPacket
import me.arasple.mc.trmenu.api.events.MenuClickEvent
import me.arasple.mc.trmenu.api.factory.MenuFactory
import me.arasple.mc.trmenu.api.factory.MenuFactorySession
import me.arasple.mc.trmenu.api.factory.task.ClickTask.Action.ACCESS
import me.arasple.mc.trmenu.api.factory.task.ClickTask.Action.CANCEL_MODIFY
import me.arasple.mc.trmenu.api.inventory.InvClickType
import me.arasple.mc.trmenu.data.MenuSession
import me.arasple.mc.trmenu.data.MenuSession.Companion.TRMENU_WINDOW_ID
import me.arasple.mc.trmenu.modules.packets.PacketsHandler
import me.arasple.mc.trmenu.utils.Msger
import org.bukkit.entity.Player

/**
 * @author Arasple
 * @date 2020/7/6 22:25
 */
object ListenerWindowEvents {

    @TPacket(type = TPacket.Type.RECEIVE)
    fun playInWindowClick(player: Player, packet: Packet): Boolean {
        try {
            if (packet.`is`("PacketPlayInWindowClick")) {

                if (player.hasMetadata("TrMenu:Debug")) {
                    player.sendMessage(arrayOf("§3Packet§fInfo §7WindowClick: ", "", "§7Window ID: §a${packet.read("a")}", "§7Window Slot/Button: §a${packet.read("slot")} / ${packet.read("button")}", "§7Window D: §a${packet.read("d")}", "§7Window ClickType: §a${packet.read("shift")}"))
                }

                val slot = packet.read("slot") as Int
                val type = PacketsHandler.getClickType(packet.read("shift"), packet.read("button") as Int, slot)
                val factorySession = MenuFactory.session(player)
                val session = MenuSession.session(player)

                if (!factorySession.isNull() || !session.isNull()) {
                    val size: Int =
                        if (!factorySession.isNull())
                            processMenuFactory(player, factorySession, slot, type)
                        else
                            processMenuSession(player, session, slot, type)

                    /**
                     * 1.16+ 使用 OFFHAND 键移动菜单物品, 受原版限制, 暂时无解决方案
                     */

                    if (slot >= size || type.isItemMoveable()) {
                        val menu = session.menu
                        val factory = factorySession.menuFactory

                        if ((menu != null && menu.settings.options.hidePlayerInventory) || factory != null)
                            PacketsHandler.clearInventory(player, size, TRMENU_WINDOW_ID)
                        else
                            PacketsHandler.resetInventory(player, size, TRMENU_WINDOW_ID)
                    }

                    return false
                }
            } else if (packet.`is`("PacketPlayInCloseWindow")) {
                val factorySession = MenuFactory.session(player)
                val session = MenuSession.session(player)

                if (!factorySession.isNull()) {
                    factorySession.menuFactory!!.closeTask?.run(player, factorySession.menuFactory!!)
                    factorySession.reset()
                    player.updateInventory()
                } else if (!session.isNull()) {
                    session.layout?.close(player, false)
                    MenuSession.session(player).set(null, null, -1)
                    player.updateInventory()
                }
            }
        } catch (e: Throwable) {
            Msger.printErrors("PACKET", e, packet.javaClass.simpleName)
        }
        return true
    }

    private fun processMenuSession(player: Player, session: MenuSession, slot: Int, type: InvClickType): Int {
        val menu = session.menu!!
        val size = session.layout!!.size()

        menu.getIcon(player, session.page, slot).let {
            PacketsHandler.sendCancelSlot(player)
            it?.displayItemStack(player)
            MenuClickEvent(player, slot, menu, it, type).async(true).call()
        }

        return size
    }

    private fun processMenuFactory(player: Player, session: MenuFactorySession, slot: Int, type: InvClickType): Int {
        val factory = session.menuFactory!!

        (session.getItem(slot) ?: return -1).let {
            factory.clickTask?.run(player, factory, slot, it.first, it.second, type).let { action ->
                val item = it.second
                if (item != null && action != ACCESS) {
                    PacketsHandler.sendCancelSlot(player)
                    if (action == CANCEL_MODIFY) session.setItem(slot, item, true)
                }
            }
        }

        return factory.size()
    }

}