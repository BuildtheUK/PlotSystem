package net.bteuk.plotsystem.utils;

import com.sk89q.worldedit.math.BlockVector2;
import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.DiscordDirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Time;
import net.bteuk.network.utils.enums.PlotStatus;
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

public class Inactive {

    public static void cancelInactivePlots() {

        // Get config.
        FileConfiguration config = PlotSystem.getInstance().getConfig();

        // Get all plots claimed by inactive players.
        int plotInactivityDays = config.getInt("plot_inactive_cancel");
        long time = Time.currentTime();
        long timeCap = plotInactivityDays * 24L * 60L * 60L * 1000L;
        long timeDif = time - timeCap;

        // Get plot sql.
        PlotSQL plotSQL = Network.getInstance().getPlotSQL();

        // Get plots that will be deleted for inactive in 1 day.
        // If the inactivity is less than 3 days don't bother.
        if (plotInactivityDays >= 3) {
            // The bound will be 1 hour, since this timer goes all every hour.
            long timeCapPlus1 = timeDif + (24 * 60 * 60 * 1000);
            List<Integer> nearlyInactivePlots = plotSQL.getIntList("SELECT pm.id FROM plot_members AS pm INNER JOIN plot_data AS pd ON pd.id=pm.id " +
                    "WHERE pm.is_owner=1 AND pm.last_enter>=" + timeDif + " AND pm.last_enter<" + timeCapPlus1 + " AND pd.status='claimed' AND pm.inactivity_notice=0 AND pd" +
                    ".location IN (" +
                    "SELECT ld.name FROM location_data AS ld WHERE ld.server='" + PlotSystem.SERVER_NAME + "');");
            // Send DM to users that their plot will be deleted in 24 hours.
            if (nearlyInactivePlots != null) {
                nearlyInactivePlots.forEach(plotId -> {
                    // Get the uuid of the plot owner.
                    String uuid = plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + plotId + " AND is_owner=1;");

                    if (uuid != null) {
                        // Set the inactivity notice to 1 and send a dm.
                        plotSQL.update("UPDATE plot_members SET inactivity_notice=1 WHERE id=" + plotId + " AND uuid='" + uuid + "';");

                        DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(uuid,
                                String.format("Plot %d has been inactive for %d days. The plot will be deleted in 24 hours, to prevent this please enter the plot.", plotId,
                                        plotInactivityDays - 1));
                        Network.getInstance().getChat().sendSocketMesage(discordDirectMessage);
                    }
                });
            }
        }

        // Get inactive plots.
        // Check if they are claimed (not submitted), the last enter time is greater than the inactivity time and the location is on this server.
        List<Integer> inactivePlots = plotSQL.getIntList("SELECT pm.id FROM plot_members AS pm INNER JOIN plot_data AS pd ON pd.id=pm.id " +
                "WHERE pm.is_owner=1 AND pm.last_enter<" + timeDif + " AND pd.status='claimed' AND pd.location IN (" +
                "SELECT ld.name FROM location_data AS ld WHERE ld.server='" + PlotSystem.SERVER_NAME + "');");

        // If there are no inactive plots, end the method.
        if (inactivePlots == null || inactivePlots.isEmpty()) {
            return;
        }

        PlotSystem.LOGGER.info("Found " + inactivePlots.size() + " inactive plots, clearing them.");

        // Iterate through all inactive plots and cancel them.
        for (int plot : inactivePlots) {

            // Get plot location.
            String location = plotSQL.getString("SELECT location FROM plot_data WHERE id=" + plot + ";");

            // Get worlds of plot and save location.
            String save_world = config.getString("save_world");
            if (save_world == null) {
                PlotSystem.LOGGER.warning("Save World is not defined in config, plot delete event has therefore failed!");
                continue;
            }

            World copyWorld = Bukkit.getWorld(save_world);
            World pasteWorld = Bukkit.getWorld(location);

            int minusXTransform = -plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");
            int minusZTransform = -plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");

            // Get the plot bounds.
            List<BlockVector2> pasteVector;
            try {
                pasteVector = WorldGuardFunctions.getPoints(String.valueOf(plot), pasteWorld);
            } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                e.printStackTrace();
                continue;
            }

            // Create the copyVector by transforming the points in the paste vector with the negative transform.
            // The negative transform is used because the coordinates by default are transformed from the save to the paste world, which in this case it reversed.
            List<BlockVector2> copyVector = new ArrayList<>();
            for (BlockVector2 bv : pasteVector) {
                copyVector.add(BlockVector2.at(bv.getX() + minusXTransform, bv.getZ() + minusZTransform));
            }

            assert copyWorld != null;

            // Revert plot to original state.
            Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
                WorldEditor.updateWorld(copyVector, pasteVector, copyWorld, pasteWorld);

                // Remove all members from the worldguard plot.
                try {
                    WorldGuardFunctions.clearMembers(String.valueOf(plot), pasteWorld);
                } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                    e.printStackTrace();
                }

                // Get the uuid of the plot owner.
                String uuid = plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + plot + " AND is_owner=1;");

                // Remove all members of plot in database.
                plotSQL.update("DELETE FROM plot_members WHERE id=" + plot + ";");

                // Set plot status to unclaimed.
                PlotHelper.updatePlotStatus(plot, PlotStatus.UNCLAIMED);

                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("Plot %s has been removed due to inactivity!", String.valueOf(plot)), true);
                DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(uuid, String.format("Plot %d has been removed due to inactivity!", plot));
                Network.getInstance().getChat().sendSocketMesage(directMessage);
                Network.getInstance().getChat().sendSocketMesage(discordDirectMessage);

                // Log plot removal to console.
                PlotSystem.LOGGER.info("Plot " + plot + " removed due to inactivity!");

            });
        }
    }

    public static void closeExpiredZones() {

        // Get config.
        FileConfiguration config = PlotSystem.getInstance().getConfig();

        // Get current time, this will be compared with the expiration time.
        long time = Time.currentTime();

        // Get plot sql.
        PlotSQL plotSQL = Network.getInstance().getPlotSQL();

        // Get active zones that have expired.
        List<Integer> expiredZones = plotSQL.getIntList("SELECT id FROM zones WHERE status='open' AND expiration<" + time + ";");

        // If there are no inactive plots, end the method.
        if (expiredZones == null || expiredZones.isEmpty()) {
            return;
        }

        // Iterate through all expired zones, save and close them.
        for (int zone : expiredZones) {

            // Get zone location.
            String location = plotSQL.getString("SELECT location FROM zones WHERE id=" + zone + ";");

            // Check if the zone is on this server.
            if (plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + location +
                    "' AND server='" + PlotSystem.SERVER_NAME + "';")) {

                // Get worlds of plot and save location.
                String save_world = config.getString("save_world");
                if (save_world == null) {
                    PlotSystem.LOGGER.warning("Save World is not defined in config, plot delete event has therefore failed!");
                    continue;
                }

                World copyWorld = Bukkit.getWorld(location);
                World pasteWorld = Bukkit.getWorld(save_world);

                int minusXTransform = -plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");
                int minusZTransform = -plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");

                // Get the zone bounds.
                List<BlockVector2> copyVector;
                try {
                    copyVector = WorldGuardFunctions.getPoints("z" + zone, copyWorld);
                } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                    e.printStackTrace();
                    continue;
                }

                // Create the copyVector by transforming the points in the paste vector with the negative transform.
                // The negative transform is used because the coordinates by default are transformed from the save to the paste world, which in this case it reversed.
                List<BlockVector2> pasteVector = new ArrayList<>();
                for (BlockVector2 bv : copyVector) {
                    pasteVector.add(BlockVector2.at(bv.getX() + minusXTransform, bv.getZ() + minusZTransform));
                }

                assert copyWorld != null;

                // Save the zone by copying from the building world to the save world.
                Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
                    WorldEditor.updateWorld(copyVector, pasteVector, copyWorld, pasteWorld);

                    // Delete the worldguard region.
                    try {
                        WorldGuardFunctions.delete("z" + zone, copyWorld);
                    } catch (RegionManagerNotFoundException e) {
                        e.printStackTrace();
                    }

                    // Get the uuid of the zone owner.
                    String uuid = plotSQL.getString("SELECT uuid FROM zone_members WHERE id=" + zone + " AND is_owner=1;");

                    // Remove all members of zone in database.
                    plotSQL.update("DELETE FROM zone_members WHERE id=" + zone + ";");

                    // Set the zone status to closed.
                    plotSQL.update("UPDATE zones SET status='closed' WHERE id=" + zone + ";");

                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.error("Zone %s has expired, its content has been saved.", String.valueOf(zone)), true);
                    Network.getInstance().getChat().sendSocketMesage(directMessage);

                    // Log plot removal to console.
                    PlotSystem.LOGGER.info("Zone " + zone + " has expired.");

                });
            }
        }
    }
}
