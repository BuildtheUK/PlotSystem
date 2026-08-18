package net.bteuk.plotsystem.events;

import net.bteuk.network.api.ChatAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.network.papercore.WorldUtils;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import org.btuk.network.lib.dto.DirectMessage;
import org.btuk.network.lib.utils.ChatUtils;

public class PlotsystemKickEvent implements Event {

    private final PlotAPI plotAPI;

    private final ChatAPI chatAPI;

    private final SQLAPI globalSQL;

    public PlotsystemKickEvent(PlotAPI plotAPI, ChatAPI chatAPI, SQLAPI globalSQL) {
        this.plotAPI = plotAPI;
        this.chatAPI = chatAPI;
        this.globalSQL = globalSQL;
    }

    public void event(String uuid, String[] event, String message) {

        // Events for retracting
        if (event[1].equals("plot")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            String ownerId = plotAPI.getPlotOwner(id);

            Component messageOwner = ChatUtils.success("You have kicked %s from plot %s", globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + uuid + "';"),
                    String.valueOf(id));
            Component messageMember = ChatUtils.error("You have been kicked from Plot %s", String.valueOf(id));

            // Remove the player to the database.
            plotAPI.removePlotMember(id, uuid);

            // Remove the player to the worldguard region.
            try {
                WorldGuardFunctions.removeMember(String.valueOf(id), uuid, WorldUtils.getWorld(plotAPI.getPlotLocation(id)));
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", ownerId, "server",
                        ChatUtils.error("An error occurred while trying to kick the user from the plot, please contact an administrator."), false);
                chatAPI.sendDirectMessage(directMessage);
                return;
            }

            // Send message to plot owner.
            DirectMessage ownerMessage = new DirectMessage("global", ownerId, "server",
                    messageOwner, true);
            chatAPI.sendDirectMessage(ownerMessage);

            // Send message to plot member.
            DirectMessage memberMessage = new DirectMessage("global", uuid, "server",
                    messageMember, true);
            chatAPI.sendDirectMessage(memberMessage);

        } else if (event[1].equals("zone")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            String ownerId = plotAPI.getZoneOwner(id);

            Component messageOwner = ChatUtils.success("You have kicked %s from Zone %s", globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + uuid + "';"),
                    String.valueOf(id));
            Component messageMember = ChatUtils.error("You have been kicked from Zone %s", String.valueOf(id));

            // Remove the player to the database.
            plotAPI.removeZoneMember(id, uuid);

            // Remove the player to the worldguard region.
            try {
                WorldGuardFunctions.removeMember("z" + event[2], uuid, WorldUtils.getWorld(plotAPI.getZoneLocation(id)));
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", ownerId, "server",
                        ChatUtils.error("An error occurred while trying to kick the user from the zone, please contact an administrator."), false);
                chatAPI.sendDirectMessage(directMessage);
                return;
            }

            // Send message to zone owner.
            DirectMessage ownerMessage = new DirectMessage("global", ownerId, "server",
                    messageOwner, true);
            chatAPI.sendDirectMessage(ownerMessage);

            // Send message to zone member.
            DirectMessage memberMessage = new DirectMessage("global", uuid, "server",
                    messageMember, true);
            chatAPI.sendDirectMessage(memberMessage);
        }
    }

}
