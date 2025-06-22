package net.bteuk.plotsystem.events;

import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class LeaveEvent {

    public static void event(String uuid, String[] event) {

        // Events for leaving
        if (event[1].equals("plot")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            // Get worlds of plot.
            World world = Bukkit.getWorld(PlotSystem.getInstance().plotSQL.getString("SELECT location FROM plot_data WHERE id=" + id + ";"));

            if (world == null) {

                // Send error to console.
                LOGGER.severe("Plot leave event failed!");
                LOGGER.severe("Event details:" + Arrays.toString(event));
                return;

            }

            // Remove member from plot.
            try {
                WorldGuardFunctions.removeMember(event[2], uuid, world);
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("An error occurred while removing you from the plot, please contact an administrator."), false);
                Network.getInstance().getChat().sendSocketMesage(directMessage);
                return;
            }

            // Remove members from plot in database.
            PlotSystem.getInstance().plotSQL.update("DELETE FROM plot_members WHERE id=" + id + " AND uuid='" + uuid + "';");

            // Send message to plot owner.
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));

            if (p != null) {
                // Update the hologram since they are on the server.
                PlotHelper.updatePlotHologram(id);
            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    ChatUtils.success("You have left Plot %s", String.valueOf(id)), true);
            Network.getInstance().getChat().sendSocketMesage(directMessage);

        } else if (event[1].equals("zone")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            // Get worlds of plot.
            World world = Bukkit.getWorld(PlotSystem.getInstance().plotSQL.getString("SELECT location FROM zones WHERE id=" + id + ";"));

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
                Network.getInstance().getChat().sendSocketMesage(directMessage);
                return;
            }

            // Remove members from zone in database.
            PlotSystem.getInstance().plotSQL.update("DELETE FROM zone_members WHERE id=" + id + " AND uuid='" + uuid + "';");

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    ChatUtils.success("You have left Zone %s", String.valueOf(id)), true);
            Network.getInstance().getChat().sendSocketMesage(directMessage);
        }
    }
}
