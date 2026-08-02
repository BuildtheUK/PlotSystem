package net.bteuk.plotsystem.events;

import net.bteuk.network.api.ChatAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import org.btuk.network.lib.dto.DirectMessage;
import org.btuk.network.lib.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class LeaveEvent implements Event {

    private final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    private final ChatAPI chatAPI;

    public LeaveEvent(PlotAPI plotAPI, PlotHelper plotHelper, ChatAPI chatAPI) {
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;
        this.chatAPI = chatAPI;
    }

    public void event(String uuid, String[] event, String message) {

        // Events for leaving
        if (event[1].equals("plot")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            // Get worlds of plot.
            World world = Bukkit.getWorld(plotAPI.getPlotLocation(id));

            if (world == null) {

                // Send error to console.
                LOGGER.severe("Plot leave event failed!");
                LOGGER.severe("Event details:" + Arrays.toString(event));
                return;

            }

            // Remove member from the plot.
            try {
                WorldGuardFunctions.removeMember(event[2], uuid, world);
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("An error occurred while removing you from the plot, please contact an administrator."), false);
                chatAPI.sendDirectMessage(directMessage);
                return;
            }

            // Remove members from plot in database.
            plotAPI.removePlotMember(id, uuid);

            // Send message to plot owner.
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));

            if (p != null) {
                // Update the hologram since they are on the server.
                plotHelper.updatePlotHologram(id);
            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    ChatUtils.success("You have left Plot %s", String.valueOf(id)), true);
            chatAPI.sendDirectMessage(directMessage);

        } else if (event[1].equals("zone")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            // Get worlds of plot.
            World world = Bukkit.getWorld(plotAPI.getZoneLocation(id));

            if (world == null) {

                // Send error to console.
                LOGGER.severe("Zone leave event failed!");
                LOGGER.severe("Event details:" + Arrays.toString(event));
                return;

            }

            // Remove member from zone.
            try {
                WorldGuardFunctions.removeMember("z" + event[2], uuid, world);
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("An error occurred while removing you from the zone, please contact an administrator."), false);
                chatAPI.sendDirectMessage(directMessage);
                return;
            }

            // Remove members from zone in database.
            plotAPI.removeZoneMember(id, uuid);

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    ChatUtils.success("You have left Zone %s", String.valueOf(id)), true);
            chatAPI.sendDirectMessage(directMessage);
        }
    }
}
