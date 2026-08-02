package net.bteuk.plotsystem.utils;

import com.sk89q.worldedit.math.BlockVector2;
import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.plugins.WorldEditor;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import org.btuk.network.lib.dto.DirectMessage;
import org.btuk.network.lib.dto.DiscordDirectMessage;
import org.btuk.network.lib.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class Inactive {

    public static void cancelInactivePlots(NetworkAPI networkAPI, PlotHelper plotHelper) {

        // Get config.
        FileConfiguration config = PlotSystem.getInstance().getConfig();

        // Get all plots claimed by inactive players.
        int plotInactivityDays = config.getInt("plot_inactive_cancel");
        long time = System.currentTimeMillis();
        long timeCap = plotInactivityDays * 24L * 60L * 60L * 1000L;
        long timeDif = time - timeCap;

        // Get plots that will be deleted for inactive in 1 day.
        // If the inactivity is less than 3 days, don't bother.
        if (plotInactivityDays >= 3) {
            long timeCapPlus1 = timeDif + (24 * 60 * 60 * 1000);
            List<Integer> nearlyInactivePlots = networkAPI.getPlotAPI()
                    .getClaimedPlotsLastEnteredBetweenWithoutInactivityNoticeForServer(timeDif, timeCapPlus1, PlotSystem.SERVER_NAME);
            // Send DM to users that their plot will be deleted in 24 hours.
            if (nearlyInactivePlots != null) {
                nearlyInactivePlots.forEach(plotId -> {
                    // Get the uuid of the plot owner.
                    String uuid = networkAPI.getPlotAPI().getPlotOwner(plotId);

                    if (uuid != null) {
                        // Set the inactivity notice to 1 and send a dm.
                        networkAPI.getPlotAPI().setPlotInactivityNotice(plotId, uuid);

                        DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(uuid,
                                String.format("Plot %d has been inactive for %d days. The plot will be deleted in 24 hours, to prevent this please enter the plot.", plotId,
                                        plotInactivityDays - 1));
                        networkAPI.getChat().sendDiscordDirectMessage(discordDirectMessage);
                    }
                });
            }
        }

        // Get inactive plots.
        // Check if they are claimed (not submitted), the last enter time is greater than the inactivity time and the location is on this server.
        List<Integer> inactivePlots = networkAPI.getPlotAPI().getInactivePlotsForServer(timeDif, PlotSystem.SERVER_NAME);

        // If there are no inactive plots, end the method.
        if (inactivePlots == null || inactivePlots.isEmpty()) {
            return;
        }

        LOGGER.info("Found " + inactivePlots.size() + " inactive plots, clearing them.");

        // Iterate through all inactive plots and cancel them.
        for (int plot : inactivePlots) {

            // Get plot location.
            String location = networkAPI.getPlotAPI().getPlotLocation(plot);

            // Get worlds of plot and save location.
            String save_world = config.getString("save_world");
            if (save_world == null) {
                LOGGER.warning("Save World is not defined in config, plot delete event has therefore failed!");
                continue;
            }

            World copyWorld = Bukkit.getWorld(save_world);
            World pasteWorld = Bukkit.getWorld(location);

            int minusXTransform = -networkAPI.getPlotAPI().getXTransform(location);
            int minusZTransform = -networkAPI.getPlotAPI().getZTransform(location);

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
                copyVector.add(BlockVector2.at(bv.x() + minusXTransform, bv.z() + minusZTransform));
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
                String uuid = networkAPI.getPlotAPI().getPlotOwner(plot);

                // Remove all members of plot in database.
                networkAPI.getPlotAPI().clearPlotMembers(plot);

                // Set plot status to unclaimed.
                plotHelper.updatePlotStatus(plot, PlotStatus.UNCLAIMED);

                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("Plot %s has been removed due to inactivity!", String.valueOf(plot)), true);
                DiscordDirectMessage discordDirectMessage = new DiscordDirectMessage(uuid, String.format("Plot %d has been removed due to inactivity!", plot));
                networkAPI.getChat().sendDirectMessage(directMessage);
                networkAPI.getChat().sendDiscordDirectMessage(discordDirectMessage);

                // Log plot removal to console.
                LOGGER.info("Plot " + plot + " removed due to inactivity!");

            });
        }
    }

    public static void closeExpiredZones(NetworkAPI networkAPI) {

        // Get config.
        FileConfiguration config = PlotSystem.getInstance().getConfig();

        // Get current time, this will be compared with the expiration time.
        long time = System.currentTimeMillis();

        // Get active zones that have expired.
        List<Integer> expiredZones = networkAPI.getPlotAPI().getExpiredZonesForServer(time, PlotSystem.SERVER_NAME);

        // If there are no inactive plots, end the method.
        if (expiredZones == null || expiredZones.isEmpty()) {
            return;
        }

        // Iterate through all expired zones, save and close them.
        for (int zone : expiredZones) {

            // Get zone location.
            String location = networkAPI.getPlotAPI().getZoneLocation(zone);

            // Get worlds of plot and save location.
            String save_world = config.getString("save_world");
            if (save_world == null) {
                LOGGER.warning("Save World is not defined in config, plot delete event has therefore failed!");
                continue;
            }

            World copyWorld = Bukkit.getWorld(location);
            World pasteWorld = Bukkit.getWorld(save_world);

            int minusXTransform = -networkAPI.getPlotAPI().getXTransform(location);
            int minusZTransform = -networkAPI.getPlotAPI().getZTransform(location);

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
                pasteVector.add(BlockVector2.at(bv.x() + minusXTransform, bv.z() + minusZTransform));
            }

            assert copyWorld != null;

            // Save the zone by copying from the building world to the save world.
            Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
                LOGGER.info("Zone " + zone + " has expired, saving it.");
                long start = System.currentTimeMillis();
                WorldEditor.updateWorld(copyVector, pasteVector, copyWorld, pasteWorld);
                LOGGER.info("Zone " + zone + " has expired, saved in " + (System.currentTimeMillis() - start) + "ms.");

                // Delete the worldguard region.
                try {
                    WorldGuardFunctions.delete("z" + zone, copyWorld);
                } catch (RegionManagerNotFoundException e) {
                    e.printStackTrace();
                }

                // Get the uuid of the zone owner.
                String uuid = networkAPI.getPlotAPI().getZoneOwner(zone);

                // Remove all members of zone in database.
                networkAPI.getPlotAPI().clearZoneMembers(zone);

                // Set the zone status to closed.
                networkAPI.getPlotAPI().setZoneStatus(zone, "closed");

                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("Zone %s has expired, its content has been saved.", String.valueOf(zone)), true);
                networkAPI.getChat().sendDirectMessage(directMessage);

                // Log plot removal to console.
                LOGGER.info("Zone " + zone + " has expired.");
            });
        }
    }
}
