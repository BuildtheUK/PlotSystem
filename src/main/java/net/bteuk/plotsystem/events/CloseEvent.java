package net.bteuk.plotsystem.events;

import com.sk89q.worldedit.math.BlockVector2;
import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.plugins.WorldEditor;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class CloseEvent {

    public static void event(String uuid, String[] event) {

        // Event for save and closing.
        if (event[1].equals("zone")) {

            // PlotSQL
            PlotSQL plotSQL = Network.getInstance().getPlotSQL();
            FileConfiguration config = PlotSystem.getInstance().getConfig();

            // Convert the string id to int id.
            int zone = Integer.parseInt(event[2]);

            // Get zone location.
            String location = plotSQL.getString("SELECT location FROM zones WHERE id=" + zone + ";");

            // Check if the zone is on this server.
            if (plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + location +
                    "' AND server='" + PlotSystem.SERVER_NAME + "';")) {

                // Get worlds of plot and save location.
                String save_world = config.getString("save_world");
                if (save_world == null) {
                    LOGGER.warning("Save World is not defined in config, plot delete event has therefore failed!");
                    return;
                }

                World copyWorld = Bukkit.getWorld(location);
                World pasteWorld = Bukkit.getWorld(save_world);

                assert (copyWorld != null);

                int minusXTransform = -plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");
                int minusZTransform = -plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");

                // Get the zone bounds.
                List<BlockVector2> copyVector;
                try {
                    copyVector = WorldGuardFunctions.getPoints("z" + zone, copyWorld);
                } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.error("An error occurred while closing the zone, please contact an administrator."), false);
                    Network.getInstance().getChat().sendSocketMesage(directMessage);
                    return;
                }

                // Create the copyVector by transforming the points in the paste vector with the negative transform.
                // The negative transform is used because the coordinates by default are transformed from the save to the paste world, which in this case it reversed.
                List<BlockVector2> pasteVector = new ArrayList<>();
                for (BlockVector2 bv : copyVector) {
                    pasteVector.add(BlockVector2.at(bv.getX() + minusXTransform, bv.getZ() + minusZTransform));
                }

                Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
                    // Save the zone by copying from the building world to the save world.
                    WorldEditor.updateWorld(copyVector, pasteVector, copyWorld, pasteWorld);

                    // Delete the worldguard region.
                    try {
                        WorldGuardFunctions.delete("z" + zone, copyWorld);
                    } catch (RegionManagerNotFoundException e) {
                        DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                                ChatUtils.error("An error occurred while closing the zone, please contact an administrator."), false);
                        Network.getInstance().getChat().sendSocketMesage(directMessage);
                        return;
                    }

                    // Remove all members of zone in database.
                    plotSQL.update("DELETE FROM zone_members WHERE id=" + zone + ";");

                    // Set the zone status to closed.
                    plotSQL.update("UPDATE zones SET status='closed' WHERE id=" + zone + ";");

                    // Add message for the zone owner to the database to notify them that their zone was closed.
                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.success("Closed zone %s, its content has been saved.", String.valueOf(zone)), true);
                    Network.getInstance().getChat().sendSocketMesage(directMessage);

                    // Log plot removal to console.
                    LOGGER.info("Zone " + zone + " has been closed.");
                });
            }
        }
    }
}
