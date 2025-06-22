package net.bteuk.plotsystem.events;

import io.papermc.lib.PaperLib;
import net.bteuk.network.eventing.events.EventManager;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.SwitchServer;
import net.bteuk.network.utils.Utils;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

import static net.bteuk.network.utils.Constants.SERVER_NAME;
import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class TeleportEvent {

    public static void event(String uuid, String[] event) {

        // Events for teleporting
        if (event[1].equals("plot")) {

            // Get the user.
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));

            if (p == null) {
                // Send warning to console if player can't be found.
                LOGGER.warning(("Attempting to teleport player with uuid " + uuid + " but they are not on this server."));
                return;
            }

            User u = PlotSystem.getInstance().getUser(p);

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            // Teleport to specific plot id.
            // Get the server of the plot.
            String server = u.plotSQL.getString("SELECT server FROM location_data WHERE name='"
                    + u.plotSQL.getString("SELECT location FROM plot_data WHERE id=" + id + ";")
                    + "';");

            // If the plot is on the current server teleport them directly.
            // Else teleport them to the correct server and them teleport them to the plot.
            if (StringUtils.equals(server, SERVER_NAME)) {

                // Get world of plot.
                World world = Bukkit.getWorld(u.plotSQL.getString("SELECT location FROM plot_data WHERE id=" + id + ";"));

                if (world == null) {
                    u.player.sendMessage(ChatUtils.error("An error occurred while teleporting to plot %s", String.valueOf(id)));
                    return;
                }

                // Get the plot status
                PlotStatus status = PlotStatus.fromDatabaseValue(u.plotSQL.getString("SELECT status FROM plot_data WHERE id=" + id + ";"));
                if (status == PlotStatus.COMPLETED) {
                    // Use the plot corners to get the location of the plot, since it no longer exists as a WorldGuard region.
                    int[][] corners = u.plotSQL.getPlotCorners(id);
                    int sumX = 0;
                    int sumZ = 0;

                    // Find the centre.
                    for (int[] corner : corners) {

                        sumX += corner[0];
                        sumZ += corner[1];

                    }
                    double x = sumX / (double) corners.length;
                    double z = sumZ / (double) corners.length;

                    // Add the coordinate transform to find the location in the build world.
                    x += u.plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + world.getName() + "';");
                    z += u.plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + world.getName() + "';");

                    Location l = new Location(world, x, Utils.getHighestYAt(world, (int) x, (int) z), z);
                    PaperLib.teleportAsync(u.player, l);
                } else {
                    // Get location of plot and teleport the player there.
                    try {
                        Location l = WorldGuardFunctions.getCurrentLocation(event[2], world);
                        PaperLib.teleportAsync(u.player, l);
                    } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                        p.sendMessage(ChatUtils.error("You could not be teleported to the plot, please notify an admin."));
                        e.printStackTrace();
                    }
                }
            } else {

                // Set the server join event.
                EventManager.createJoinEvent(u.player.getUniqueId().toString(), "plotsystem", "teleport plot" + id);

                // Teleport them to another server.
                SwitchServer.switchServer(u.player, server);

            }
        } else if (event[1].equals("zone")) {

            // Get the user.
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));

            if (p == null) {

                // Send warning to console if player can't be found.
                LOGGER.warning(("Attempting to teleport player with uuid " + uuid + " but they are not on this server."));
                return;

            }

            User u = PlotSystem.getInstance().getUser(p);

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);
            String zoneName = "z" + event[2];

            // Teleport to specific zone id.
            // Get the server of the zone.
            String server = u.plotSQL.getString("SELECT server FROM location_data WHERE name='"
                    + u.plotSQL.getString("SELECT location FROM zones WHERE id=" + id + ";")
                    + "';");

            // If the zone is on the current server teleport them directly.
            // Else teleport them to the correct server and them teleport them to the zone.
            if (server.equals(SERVER_NAME)) {

                // Get world of zone.
                World world = Bukkit.getWorld(u.plotSQL.getString("SELECT location FROM zones WHERE id=" + id + ";"));

                // Get location of zone and teleport the player there.
                try {
                    Location l = WorldGuardFunctions.getCurrentLocation(zoneName, world);
                    u.player.teleport(l);
                } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                    p.sendMessage(ChatUtils.error("You could not be teleported to the zone, please notify an admin."));
                    e.printStackTrace();
                }

            } else {

                // Set the server join event.
                EventManager.createJoinEvent(u.player.getUniqueId().toString(), "plotsystem", "teleport zone" + id);

                // Teleport them to another server.
                SwitchServer.switchServer(u.player, server);

            }
        }

    }
}
