package net.bteuk.plotsystem.events;

import io.papermc.lib.PaperLib;
import net.bteuk.network.api.EventAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.ServerAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.papercore.PlayerAdapter;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.Utils;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;
import static net.bteuk.plotsystem.PlotSystem.SERVER_NAME;

public class PlotsystemTeleportEvent implements Event {

    private final PlotAPI plotAPI;

    private final EventAPI eventAPI;

    private final ServerAPI serverAPI;

    public PlotsystemTeleportEvent(PlotAPI plotAPI, EventAPI eventAPI, ServerAPI serverAPI) {
        this.plotAPI = plotAPI;
        this.eventAPI = eventAPI;
        this.serverAPI = serverAPI;
    }

    public void event(String uuid, String[] event, String message) {

        // Events for teleporting
        if (event[1].equals("plot")) {

            // Get the user.
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));

            if (player == null) {
                // Send warning to console if player can't be found.
                LOGGER.warning(("Attempting to teleport player with uuid " + uuid + " but they are not on this server."));
                return;
            }

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            // Teleport to specific plot id.
            // Get the server of the plot.
            String location = plotAPI.getPlotLocation(id);
            String server = plotAPI.getLocationServer(location);

            // If the plot is on the current server teleport them directly.
            // Else teleport them to the correct server and them teleport them to the plot.
            if (StringUtils.equals(server, SERVER_NAME)) {

                // Get world of plot.
                World world = Bukkit.getWorld(location);

                if (world == null) {
                    player.sendMessage(ChatUtils.error("An error occurred while teleporting to plot %s", String.valueOf(id)));
                    return;
                }

                // Get the plot status
                PlotStatus status = plotAPI.getPlotStatus(id);
                if (status == PlotStatus.COMPLETED) {
                    // Use the plot corners to get the location of the plot, since it no longer exists as a WorldGuard region.
                    int[][] corners = plotAPI.getPlotCorners(id);
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
                    x += plotAPI.getXTransform(location);
                    z += plotAPI.getZTransform(location);

                    Location l = new Location(world, x, Utils.getHighestYAt(world, (int) x, (int) z), z);
                    PaperLib.teleportAsync(player, l);
                } else {
                    // Get location of plot and teleport the player there.
                    try {
                        Location l = WorldGuardFunctions.getCurrentLocation(event[2], world);
                        PaperLib.teleportAsync(player, l);
                    } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                        player.sendMessage(ChatUtils.error("You could not be teleported to the plot, please notify an admin."));
                        e.printStackTrace();
                    }
                }
            } else {

                // Set the server join event.
                eventAPI.createJoinEvent(player.getUniqueId().toString(), "plotsystemteleport plot" + id);

                // Teleport them to another server.
                serverAPI.switchServer(PlayerAdapter.adapt(player), server);

            }
        } else if (event[1].equals("zone")) {

            // Get the user.
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));

            if (player == null) {

                // Send warning to console if player can't be found.
                LOGGER.warning(("Attempting to teleport player with uuid " + uuid + " but they are not on this server."));
                return;

            }

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);
            String zoneName = "z" + event[2];

            // Teleport to specific zone id.
            // Get the server of the zone.
            String location = plotAPI.getZoneLocation(id);
            String server = plotAPI.getLocationServer(location);

            // If the zone is on the current server, teleport them directly.
            // Else teleport them to the correct server and them teleport them to the zone.
            if (server.equals(SERVER_NAME)) {

                // Get world of zone.
                World world = Bukkit.getWorld(location);

                // Get location of zone and teleport the player there.
                try {
                    Location l = WorldGuardFunctions.getCurrentLocation(zoneName, world);
                    player.teleport(l);
                } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                    player.sendMessage(ChatUtils.error("You could not be teleported to the zone, please notify an admin."));
                    e.printStackTrace();
                }

            } else {
                // Set the server join event.
                eventAPI.createJoinEvent(player.getUniqueId().toString(), "plotsystemteleport zone" + id);

                // Teleport them to another server.
                serverAPI.switchServer(PlayerAdapter.adapt(player), server);
            }
        }

    }
}
