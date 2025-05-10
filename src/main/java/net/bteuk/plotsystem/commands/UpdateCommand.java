package net.bteuk.plotsystem.commands;

import com.sk89q.worldedit.math.BlockVector3;
import net.bteuk.network.Network;
import net.bteuk.network.lib.enums.PlotDifficulties;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.CopyRegionFormat;
import net.bteuk.plotsystem.utils.plugins.WorldEditor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.bteuk.network.utils.Constants.MAX_Y;
import static net.bteuk.network.utils.Constants.MIN_Y;
import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class UpdateCommand {

    private static final Component GENERIC_ERROR_MESSAGE = ChatUtils.error("/plotsystem update [plot, location]");

    private static final Component PLOT_ERROR_MESSAGE = ChatUtils.error("/plotsystem update plot <plotID> set difficulty [easy|normal|hard]");

    public static void update(CommandSender sender, String[] args) {

        if (args.length < 2) {
            sender.sendMessage(GENERIC_ERROR_MESSAGE);
            return;
        }

        switch (args[1]) {
            case "plot" -> updatePlot(sender, args);
            case "location" -> updateLocation(sender, args);
            default -> sender.sendMessage(GENERIC_ERROR_MESSAGE);
        }
    }

    private static void updatePlot(CommandSender sender, String[] args) {
        // Check if the sender is a player.
        // If so, check if they have permission.
        if (sender instanceof Player p) {
            if (!p.hasPermission("uknet.plots.update.plot")) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
                return;
            }
        }

        // Check if they have enough args and that the correct args have been given.
        if (args.length < 6 || !args[3].equalsIgnoreCase("set") || !args[4].equalsIgnoreCase("difficulty")) {
            sender.sendMessage(PLOT_ERROR_MESSAGE);
            return;
        }

        int plotID;
        try {
            plotID = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PLOT_ERROR_MESSAGE);
            return;
        }

        // Check if a valid difficulty was selected.
        PlotDifficulties plotDifficulty;
        try {
            plotDifficulty = PlotDifficulties.valueOf(args[5].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PLOT_ERROR_MESSAGE);
            return;
        }

        PlotSQL plotSQL = Network.getInstance().getPlotSQL();

        // Check if plot exists.
        if (!plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + plotID + " AND status IN ('unclaimed','claimed','submitted');")) {
            sender.sendMessage(ChatUtils.error("Plot %s does not exist.", args[2]));
            return;
        }

        // Update the plot difficulty.
        plotSQL.update("UPDATE plot_data SET difficulty=" + plotDifficulty.getValue() + " WHERE id=" + plotID + ";");
        sender.sendMessage(ChatUtils.success("Updated difficulty of plot %s to %s.", args[2], args[5]));
    }

    private static void updateLocation(CommandSender sender, String[] args) {

        // Check if the sender is a player.
        // If so, check if they have permission.
        if (sender instanceof Player p) {
            if (!p.hasPermission("uknet.plots.update.location")) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
                return;
            }
        }

        // Check if they have enough args.
        if (args.length < 9) {
            sender.sendMessage(ChatUtils.error("/plotsystem update location [name] <Xmin> <Ymin> <Zmin> <Xmax> <Ymax> <Zmax>"));
            return;
        }

        int xmin;
        int ymin;
        int zmin;

        int xmax;
        int ymax;
        int zmax;

        // Check if the coordinates are actual numbers.
        try {

            xmin = Integer.parseInt(args[3]);
            ymin = Integer.parseInt(args[4]);
            zmin = Integer.parseInt(args[5]);

            xmax = Integer.parseInt(args[6]);
            ymax = Integer.parseInt(args[7]);
            zmax = Integer.parseInt(args[8]);

        } catch (NumberFormatException e) {

            sender.sendMessage(ChatUtils.error("/plotsystem update location [name] <Xmin> <Ymin> <Zmin> <Xmax> <Ymax> <Zmax>"));
            return;

        }

        PlotSQL plotSQL = Network.getInstance().getPlotSQL();

        // Check if the location name exists.
        if (!plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + args[2] + "';")) {
            sender.sendMessage(ChatUtils.error("Location %s does not exist.", args[2]));
            return;
        }

        // Get the exact regions of the selected coordinates.
        int regionXMin = Math.floorDiv(xmin, 512);
        int regionZMin = Math.floorDiv(zmin, 512);

        int regionXMax = Math.floorDiv(xmax, 512);
        int regionZMax = Math.floorDiv(zmax, 512);

        // Get the coordinate transformation of the location.
        int xTransform = plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + args[2] + "';");
        int zTransform = plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + args[2] + "';");

        // Get the worlds.
        String saveWorld = PlotSystem.getInstance().getConfig().getString("save_world");
        if (saveWorld == null) {
            sender.sendMessage(ChatUtils.error("The save world is not set in config."));
            return;
        }

        // Get worlds.
        World copy = Bukkit.getWorld(saveWorld);
        World paste = Bukkit.getWorld(args[2]);

        // Check that the worlds are not null, else delete the Multiverse world.
        if (copy == null || paste == null) {
            sender.sendMessage("An error occurred, please contact an admin.");
            return;
        }

        // Determine which regions are new, only copy them.
        List<String> existingRegions = plotSQL.getStringList("SELECT region FROM regions WHERE location='" + args[2] + "';");
        List<Region> regionsToAdd = new ArrayList<>();

        for (int i = regionXMin; i <= regionXMax; i++) {
            for (int j = regionZMin; j <= regionZMax; j++) {
                String region = String.format("%d,%d", i, j);
                if (!existingRegions.contains(region)) {
                    regionsToAdd.add(new Region(region, i, j));
                }
            }
        }

        // Copy paste the regions in the save world.
        // Iterate through the regions one-by-one.
        // Run it asynchronously to not freeze the server.
        sender.sendMessage(ChatUtils.success("Transferring terrain, this may take a while."));
        sender.sendMessage(ChatUtils.success("Please don't leave this server while this is in progress."));

        // Create atomic boolean to query whether a region can be copied.
        AtomicBoolean isReady = new AtomicBoolean(true);

        // Create a list of regions to copy paste.
        ArrayList<CopyRegionFormat> regions = new ArrayList<>();

        final int yMin = max(ymin, MIN_Y);
        final int yMax = min(ymax, MAX_Y - 1);

        for (Region region : regionsToAdd) {
            // Split the region into 4 equal segments of 256x256.
            regions.add(new CopyRegionFormat(copy, paste, BlockVector3.at(region.regionX() * 512, yMin, region.regionZ() * 512),
                    BlockVector3.at(region.regionX() * 512 + 255, yMax, region.regionZ() * 512 + 255),
                    BlockVector3.at(region.regionX() * 512 + xTransform, yMin, region.regionZ() * 512 + zTransform)));

            regions.add(new CopyRegionFormat(copy, paste, BlockVector3.at(region.regionX() * 512 + 256, yMin, region.regionZ() * 512),
                    BlockVector3.at(region.regionX() * 512 + 511, yMax, region.regionZ() * 512 + 255),
                    BlockVector3.at(region.regionX() * 512 + 256 + xTransform, yMin, region.regionZ() * 512 + zTransform)));

            regions.add(new CopyRegionFormat(copy, paste, BlockVector3.at(region.regionX() * 512, yMin, region.regionZ() * 512 + 256),
                    BlockVector3.at(region.regionX() * 512 + 255, yMax, region.regionZ() * 512 + 511),
                    BlockVector3.at(region.regionX() * 512 + xTransform, yMin, region.regionZ() * 512 + 256 + zTransform)));

            regions.add(new CopyRegionFormat(copy, paste, BlockVector3.at(region.regionX() * 512 + 256, yMin, region.regionZ() * 512 + 256),
                    BlockVector3.at(region.regionX() * 512 + 511, yMax, region.regionZ() * 512 + 511),
                    BlockVector3.at(region.regionX() * 512 + 256 + xTransform, yMin, region.regionZ() * 512 + 256 + zTransform)));
        }

        LOGGER.info("Add segments to list, there are " + regions.size());
        sender.sendMessage(ChatUtils.success("Added " + regions.size() + " segments of 256x256 to the list to be copied."));

        // Iterate until all regions are done.
        Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {

            while (!regions.isEmpty()) {

                if (isReady.get()) {

                    // Set isReady to false so the loop will wait until the previous copy-paste is done.
                    isReady.set(false);

                    CopyRegionFormat regionFormat = regions.getFirst();

                    Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {

                        if (!WorldEditor.largeCopy(regionFormat.minPoint(), regionFormat.maxPoint(), regionFormat.pasteMinPoint(), copy, paste)) {
                            sender.sendMessage(ChatUtils.error("An error occured while transferring the terrain."));
                        } else {
                            regions.remove(regionFormat);
                            sender.sendMessage(ChatUtils.success("Segment copied, there are ").append(Component.text(regions.size(), NamedTextColor.DARK_AQUA))
                                    .append(ChatUtils.success(" remaining.")));
                            LOGGER.info("Segment copied, there are " + regions.size() + " remaining.");
                            isReady.set(true);
                        }

                    });
                }
            }

            GlobalSQL globalSQL = Network.getInstance().getGlobalSQL();

            sender.sendMessage(ChatUtils.success("Terrain transfer has been completed."));

            int minCoordinateId = globalSQL.getInt("SELECT coordMin FROM location_data WHERE name='" + args[2] + "';");
            int maxCoordinateId = globalSQL.getInt("SELECT coordMax FROM location_data WHERE name='" + args[2] + "';");

            globalSQL.updateCoordinate(minCoordinateId, new Location(Bukkit.getWorld(args[2]), (regionXMin * 512), MIN_Y, (regionZMin * 512), 0, 0));
            globalSQL.updateCoordinate(maxCoordinateId, new Location(Bukkit.getWorld(args[2]), ((regionXMax * 512) + 511), MAX_Y - 1, ((regionZMax * 512) + 511), 0, 0));

            // Add the location to the database.
            for (Region region : regionsToAdd) {
                // Change region status in region database.
                // If it already exists remove members.
                globalSQL.update("INSERT INTO server_events(uuid,type,server,event) VALUES(NULL,'network','" + globalSQL.getString(
                        "SELECT name FROM server_data WHERE type='EARTH';") + "'," + "'region set plotsystem " + region.name() + "');");

                // Add region to database.
                plotSQL.update("INSERT INTO regions(region,server,location) VALUES('" + region.name() + "','" + PlotSystem.SERVER_NAME + "','" + args[2] + "');");
            }

            sender.sendMessage(ChatUtils.success("Update location %s", args[2]));
        });
    }

    private record Region(String name, int regionX, int regionZ) {
    }
}
