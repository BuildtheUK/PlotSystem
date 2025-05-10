package net.bteuk.plotsystem.commands;

import com.sk89q.worldedit.math.BlockVector3;
import net.bteuk.network.Network;
import net.bteuk.network.eventing.events.EventManager;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Utils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.CopyRegionFormat;
import net.bteuk.plotsystem.utils.plugins.Multiverse;
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

public final class LocationCommand {

    private static final Component LOCATION_CREATE_COMMAND_FORMAT = ChatUtils.error("/plotsystem create location [name] <Xmin> <Ymin> <Zmin> <Xmax> <Ymax> <Zmax>");
    private static final Component LOCATION_UPDATE_COMMAND_FORMAT = ChatUtils.error("/plotsystem update location [name] <Xmin> <Ymin> <Zmin> <Xmax> <Ymax> <Zmax>");

    private LocationCommand() {
        // Do nothing
    }

    public static void createLocation(CommandSender sender, String[] args) {

        // Check if the sender is a player.
        // If so, check if they have permission.
        if (sender instanceof Player p) {
            if (!p.hasPermission("uknet.plots.create.location")) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
                return;
            }
        }

        CommandArguments commandArguments = parseCommandArguments(sender, args, LOCATION_CREATE_COMMAND_FORMAT);
        if (commandArguments == null) {
            return;
        }

        final PlotSQL plotSQL = Network.getInstance().getPlotSQL();

        // Check if the location name is unique.
        if (plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + commandArguments.location() + "';")) {
            sender.sendMessage(ChatUtils.error("The location ")
                    .append(Component.text(commandArguments.location(), NamedTextColor.DARK_RED))
                    .append(ChatUtils.error(" already exists.")));
            return;
        }

        // Get the exact regions of the selected coordinates.
        int regionXMin = Math.floorDiv(commandArguments.xmin(), 512);
        int regionZMin = Math.floorDiv(commandArguments.zmin(), 512);

        int regionXMax = Math.floorDiv(commandArguments.xmax(), 512);
        int regionZMax = Math.floorDiv(commandArguments.zmax(), 512);

        // Calculate the coordinate transformation.
        int xTransform = -(regionXMin * 512);
        int zTransform = -(regionZMin * 512);

        // Create the world and add the regions.
        Multiverse.createVoidWorld(commandArguments.location());

        String saveWorld = PlotSystem.getInstance().getConfig().getString("save_world");

        if (saveWorld == null) {
            sender.sendMessage(ChatUtils.error("The save world is not set in config."));
            return;
        }

        // Get worlds.
        World copy = Bukkit.getWorld(saveWorld);
        World paste = Bukkit.getWorld(commandArguments.location());

        // Check that the worlds are not null, else delete the Multiverse world.
        if (copy == null || paste == null) {
            sender.sendMessage("An error occurred, please contact an admin.");
            Multiverse.deleteWorld(commandArguments.location());
            return;
        }

        // Determine which regions are new, only copy them.
        List<Region> regionsToAdd = new ArrayList<>();
        for (int i = regionXMin; i <= regionXMax; i++) {
            for (int j = regionZMin; j <= regionZMax; j++) {
                String region = String.format("%d,%d", i, j);
                regionsToAdd.add(new Region(region, i, j));
            }
        }

        // Create a list of regions to copy-paste.
        RegionHolder regionHolder = new RegionHolder(regionsToAdd, commandArguments.ymin(), commandArguments.ymax(), copy, paste, xTransform, zTransform);
        List<CopyRegionFormat> regions = getCopyRegions(sender, regionHolder);

        // Copy-paste the regions in the save world.
        // Iterate through the regions one-by-one.
        // Run it asynchronously to not freeze the server.
        Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {

            copyRegions(sender, regions);

            final GlobalSQL globalSQL = Network.getInstance().getGlobalSQL();

            int coordMin = globalSQL.addCoordinate(new Location(
                    Bukkit.getWorld(commandArguments.location()),
                    (regionXMin * 512), MIN_Y, (regionZMin * 512), 0, 0));

            int coordMax = globalSQL.addCoordinate(new Location(
                    Bukkit.getWorld(commandArguments.location()),
                    ((regionXMax * 512) + 511), MAX_Y - 1, ((regionZMax * 512) + 511), 0, 0));

            // Add the location to the database.
            if (plotSQL.update("INSERT INTO location_data(name, alias, server, coordMin, coordMax, xTransform, zTransform) VALUES('"
                    + commandArguments.location() + "','" + commandArguments.location() + "','" + PlotSystem.SERVER_NAME + "'," + coordMin + "," + coordMax + "," + xTransform +
                    "," + zTransform + ");")) {

                sender.sendMessage(ChatUtils.success("Created new location ")
                        .append(Component.text(commandArguments.location(), NamedTextColor.DARK_AQUA)));

                // Set the status of all effected regions in the region database.
                for (int i = regionXMin; i <= regionXMax; i++) {
                    for (int j = regionZMin; j <= regionZMax; j++) {

                        String region = i + "," + j;

                        // Change region status in region database.
                        // If it already exists remove members.
                        globalSQL.update("INSERT INTO server_events(uuid,type,server,event) VALUES(NULL,'network','"
                                + globalSQL.getString("SELECT name FROM server_data WHERE type='EARTH';") + "'," +
                                "'region set plotsystem " + region + "');");

                        // Add region to database.
                        plotSQL.update(
                                "INSERT INTO regions(region,server,location) VALUES('" + region + "','" + PlotSystem.SERVER_NAME + "','" + commandArguments.location() + "');");

                    }
                }

            } else {

                sender.sendMessage(ChatUtils.error("An error occurred, please check the console for more info."));
                LOGGER.warning("An error occured while adding new location!");

            }

            teleportToLocation(sender, globalSQL, plotSQL, commandArguments.location(), coordMin, coordMax);
        });
    }

    public static void updateLocation(CommandSender sender, String[] args) {

        // Check if the sender is a player.
        // If so, check if they have permission.
        if (sender instanceof Player p) {
            if (!p.hasPermission("uknet.plots.update.location")) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
                return;
            }
        }

        CommandArguments commandArguments = parseCommandArguments(sender, args, LOCATION_UPDATE_COMMAND_FORMAT);
        if (commandArguments == null) {
            return;
        }

        PlotSQL plotSQL = Network.getInstance().getPlotSQL();

        // Check if the location name exists.
        if (!plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + commandArguments.location() + "';")) {
            sender.sendMessage(ChatUtils.error("Location %s does not exist.", commandArguments.location()));
            return;
        }

        // Get the exact regions of the selected coordinates.
        int regionXMin = Math.floorDiv(commandArguments.xmin(), 512);
        int regionZMin = Math.floorDiv(commandArguments.zmin(), 512);

        int regionXMax = Math.floorDiv(commandArguments.xmax(), 512);
        int regionZMax = Math.floorDiv(commandArguments.zmax(), 512);

        // Get the coordinate transformation of the location.
        int xTransform = plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + commandArguments.location() + "';");
        int zTransform = plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + commandArguments.location() + "';");

        // Get the worlds.
        String saveWorld = PlotSystem.getInstance().getConfig().getString("save_world");
        if (saveWorld == null) {
            sender.sendMessage(ChatUtils.error("The save world is not set in config."));
            return;
        }

        // Get worlds.
        World copy = Bukkit.getWorld(saveWorld);
        World paste = Bukkit.getWorld(commandArguments.location());

        // Check that the worlds are not null, else delete the Multiverse world.
        if (copy == null || paste == null) {
            sender.sendMessage("An error occurred, please contact an admin.");
            return;
        }

        // Determine which regions are new, only copy them.
        List<String> existingRegions = plotSQL.getStringList("SELECT region FROM regions WHERE location='" + commandArguments.location() + "';");
        List<Region> regionsToAdd = new ArrayList<>();
        for (int i = regionXMin; i <= regionXMax; i++) {
            for (int j = regionZMin; j <= regionZMax; j++) {
                String region = String.format("%d,%d", i, j);
                if (!existingRegions.contains(region)) {
                    regionsToAdd.add(new Region(region, i, j));
                }
            }
        }

        // Create a list of regions to copy-paste.
        RegionHolder regionHolder = new RegionHolder(regionsToAdd, commandArguments.ymin(), commandArguments.ymax(), copy, paste, xTransform, zTransform);
        List<CopyRegionFormat> regions = getCopyRegions(sender, regionHolder);

        // Copy-paste the regions in the save world.
        // Iterate through the regions one-by-one.
        // Run it asynchronously to not freeze the server.
        Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {

            copyRegions(sender, regions);

            GlobalSQL globalSQL = Network.getInstance().getGlobalSQL();

            int minCoordinateId = globalSQL.getInt("SELECT coordMin FROM location_data WHERE name='" + commandArguments.location() + "';");
            int maxCoordinateId = globalSQL.getInt("SELECT coordMax FROM location_data WHERE name='" + commandArguments.location() + "';");

            globalSQL.updateCoordinate(minCoordinateId, new Location(Bukkit.getWorld(commandArguments.location()), (regionXMin * 512), MIN_Y, (regionZMin * 512), 0, 0));
            globalSQL.updateCoordinate(maxCoordinateId,
                    new Location(Bukkit.getWorld(commandArguments.location()), ((regionXMax * 512) + 511), MAX_Y - 1, ((regionZMax * 512) + 511), 0, 0));

            // Add the location to the database.
            for (Region region : regionsToAdd) {
                // Change region status in region database.
                // If it already exists remove members.
                globalSQL.update("INSERT INTO server_events(uuid,type,server,event) VALUES(NULL,'network','" + globalSQL.getString(
                        "SELECT name FROM server_data WHERE type='EARTH';") + "'," + "'region set plotsystem " + region.name() + "');");

                // Add region to database.
                plotSQL.update(
                        "INSERT INTO regions(region,server,location) VALUES('" + region.name() + "','" + PlotSystem.SERVER_NAME + "','" + commandArguments.location() + "');");
            }

            sender.sendMessage(ChatUtils.success("Updated location %s", commandArguments.location()));

            teleportToLocation(sender, globalSQL, plotSQL, commandArguments.location(), minCoordinateId, maxCoordinateId);
        });
    }

    public static void deleteLocation(CommandSender sender, String[] args) {

        // If sender is a player, check for permission.
        if (sender instanceof Player p) {
            if (!(p.hasPermission("uknet.plots.delete.location"))) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command."));
                return;
            }
        }

        // Check arg count.
        if (args.length < 3) {
            sender.sendMessage(ChatUtils.error("/plotsystem delete location [name]"));
            return;
        }

        PlotSQL plotSQL = Network.getInstance().getPlotSQL();
        GlobalSQL globalSQL = Network.getInstance().getGlobalSQL();

        // Check if location exists.
        if (!(plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + args[2] + "';"))) {
            sender.sendMessage(ChatUtils.error("The location %s does not exist.", args[2]));
            return;
        }

        // Check if the location is on this server.
        if (!(plotSQL.getString("SELECT server FROM location_data WHERE name='" + args[2] + "';").equals(PlotSystem.SERVER_NAME))) {
            sender.sendMessage(ChatUtils.error("This location is not on this server."));
            return;
        }

        // If location has plots, cancel.
        if (plotSQL.hasRow("SELECT id FROM plot_data WHERE location='" + args[2] + "' AND status<>'completed' AND status<>'deleted';")) {
            sender.sendMessage(ChatUtils.error("This location active has plots, all plots must be deleted or completed to remove the location."));
            return;
        }

        // Teleport all players out of the world, so it can be deleted.
        // Get the worlds.
        String saveWorldName = PlotSystem.getInstance().getConfig().getString("save_world");
        if (saveWorldName == null) {
            sender.sendMessage(ChatUtils.error("The save world is not set in config."));
            return;
        }

        // Get save world.
        World saveWorld = Bukkit.getWorld(saveWorldName);

        teleportPlayersFromLocation(args[2], saveWorld, plotSQL, globalSQL);

        // Delete location.
        if (Multiverse.deleteWorld(args[2])) {

            // Delete location from database.
            plotSQL.update("DELETE FROM location_data WHERE name='" + args[2] + "';");
            sender.sendMessage(ChatUtils.success("Deleted location ")
                    .append(Component.text(args[2], NamedTextColor.DARK_AQUA)));
            LOGGER.info("Deleted location " + args[2] + ".");

            // Get regions from database.
            ArrayList<String> regions = plotSQL.getStringList("SELECT region FROM regions WHERE location='" + args[2] + "';");

            // Delete regions from database.
            plotSQL.update("DELETE FROM regions WHERE location='" + args[2] + "';");

            // Iterate through regions to unlock them on Earth.
            for (String region : regions) {
                globalSQL.update("INSERT INTO server_events(uuid,type,server,event) VALUES(NULL,'network','"
                        + globalSQL.getString("SELECT name FROM server_data WHERE type='earth';") + "'," +
                        "'region set default " + region + "');");
            }
        } else {
            sender.sendMessage(ChatUtils.error("An error occurred while deleting the world."));
            LOGGER.warning("An error occurred while deleting world " + args[2] + ".");
        }
    }

    private static CommandArguments parseCommandArguments(CommandSender sender, String[] args, Component error) {
        // Check if they have enough args.
        if (args.length < 9) {
            sender.sendMessage(error);
            return null;
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
            sender.sendMessage(error);
            return null;
        }

        return new CommandArguments(args[2], xmin, ymin, zmin, xmax, ymax, zmax);
    }

    private static List<CopyRegionFormat> getCopyRegions(CommandSender sender, RegionHolder regionHolder) {
        List<CopyRegionFormat> regionsToCopy = new ArrayList<>();

        final int yMin = max(regionHolder.ymin(), MIN_Y);
        final int yMax = min(regionHolder.ymax(), MAX_Y - 1);

        for (Region region : regionHolder.regionsToAdd()) {
            // Split the region into 4 equal segments of 256x256.
            regionsToCopy.add(new CopyRegionFormat(regionHolder.copyWorld(), regionHolder.pasteWorld(), BlockVector3.at(region.regionX() * 512, yMin, region.regionZ() * 512),
                    BlockVector3.at(region.regionX() * 512 + 255, yMax, region.regionZ() * 512 + 255),
                    BlockVector3.at(region.regionX() * 512 + regionHolder.xTransform(), yMin, region.regionZ() * 512 + regionHolder.zTransform())));

            regionsToCopy.add(new CopyRegionFormat(regionHolder.copyWorld(), regionHolder.pasteWorld(), BlockVector3.at(region.regionX() * 512 + 256, yMin, region.regionZ() * 512),
                    BlockVector3.at(region.regionX() * 512 + 511, yMax, region.regionZ() * 512 + 255),
                    BlockVector3.at(region.regionX() * 512 + 256 + regionHolder.xTransform(), yMin, region.regionZ() * 512 + regionHolder.zTransform())));

            regionsToCopy.add(new CopyRegionFormat(regionHolder.copyWorld(), regionHolder.pasteWorld(), BlockVector3.at(region.regionX() * 512, yMin, region.regionZ() * 512 + 256),
                    BlockVector3.at(region.regionX() * 512 + 255, yMax, region.regionZ() * 512 + 511),
                    BlockVector3.at(region.regionX() * 512 + regionHolder.xTransform(), yMin, region.regionZ() * 512 + 256 + regionHolder.zTransform())));

            regionsToCopy.add(
                    new CopyRegionFormat(regionHolder.copyWorld(), regionHolder.pasteWorld(), BlockVector3.at(region.regionX() * 512 + 256, yMin, region.regionZ() * 512 + 256),
                            BlockVector3.at(region.regionX() * 512 + 511, yMax, region.regionZ() * 512 + 511),
                            BlockVector3.at(region.regionX() * 512 + 256 + regionHolder.xTransform(), yMin, region.regionZ() * 512 + 256 + regionHolder.zTransform())));
        }

        LOGGER.info("Add segments to list, there are " + regionsToCopy.size());
        sender.sendMessage(ChatUtils.success("Added " + regionsToCopy.size() + " segments of 256x256 to the list to be copied."));

        return regionsToCopy;
    }

    private static void copyRegions(CommandSender sender, List<CopyRegionFormat> regions) {

        sender.sendMessage(ChatUtils.success("Transferring terrain, this may take a while."));
        sender.sendMessage(ChatUtils.success("Please don't leave this server while this is in progress."));

        // Create atomic boolean to query whether a region can be copied.
        AtomicBoolean isReady = new AtomicBoolean(true);

        while (!regions.isEmpty()) {
            if (isReady.get()) {
                // Set isReady to false so the loop will wait until the previous copy-paste is done.
                isReady.set(false);

                CopyRegionFormat regionFormat = regions.getFirst();

                Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
                    if (!WorldEditor.largeCopy(regionFormat.minPoint(), regionFormat.maxPoint(), regionFormat.pasteMinPoint(), regionFormat.copyWorld(),
                            regionFormat.pasteWorld())) {
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

        sender.sendMessage(ChatUtils.success("Terrain transfer has been completed."));
    }

    private static void teleportToLocation(CommandSender sender, GlobalSQL globalSQL, PlotSQL plotSQL, String location, int coordMin, int coordMax) {
        // If sender is a player teleport them to the location.
        if (sender instanceof Player p) {

            // Get middle.
            double x = ((globalSQL.getDouble("SELECT x FROM coordinates WHERE id=" + coordMax + ";") +
                    globalSQL.getDouble("SELECT x FROM coordinates WHERE id=" + coordMin + ";")) / 2) +
                    plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");

            double z = ((globalSQL.getDouble("SELECT z FROM coordinates WHERE id=" + coordMax + ";") +
                    globalSQL.getDouble("SELECT z FROM coordinates WHERE id=" + coordMin + ";")) / 2) +
                    plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");

            // Teleport to the location.
            World world = Bukkit.getWorld(location);

            double y = 64;
            if (world != null) {
                y = world.getHighestBlockYAt((int) x, (int) z);
                y++;
            }

            EventManager.createTeleportEvent(false, p.getUniqueId().toString(), "network", "teleport " + location + " " + x + " " + y + " " + z + " "
                            + p.getLocation().getYaw() + " " + p.getLocation().getPitch(),
                    "&aTeleported to location &3" + plotSQL.getString("SELECT alias FROM location_data WHERE name='" + location + "';"), p.getLocation());
        }
    }

    private static void teleportPlayersFromLocation(String location, World saveWorld, PlotSQL plotSQL, GlobalSQL globalSQL) {
        int coordMin = globalSQL.getInt("SELECT coordMin FROM location_data WHERE name='" + location + "';");
        int coordMax = globalSQL.getInt("SELECT coordMax FROM location_data WHERE name='" + location + "';");

        // Get middle.
        double x = ((globalSQL.getDouble("SELECT x FROM coordinates WHERE id=" + coordMax + ";") +
                globalSQL.getDouble("SELECT x FROM coordinates WHERE id=" + coordMin + ";")) / 2) +
                plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");

        double z = ((globalSQL.getDouble("SELECT z FROM coordinates WHERE id=" + coordMax + ";") +
                globalSQL.getDouble("SELECT z FROM coordinates WHERE id=" + coordMin + ";")) / 2) +
                plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");

        x -= plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");
        z -= plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");

        // Teleport all players away from the location.
        Location teleportLocation = new Location(saveWorld, x, Utils.getHighestYAt(saveWorld, (int) x, (int) z), z);
        PlotSystem.getInstance().getServer().getOnlinePlayers().forEach(player -> {
            player.teleport(teleportLocation);
            player.sendMessage(ChatUtils.success("Teleported to save world, location %s is being deleted.", location));
        });
    }

    private record CommandArguments(String location, int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {
    }

    private record Region(String name, int regionX, int regionZ) {
    }

    private record RegionHolder(List<Region> regionsToAdd, int ymin, int ymax, World copyWorld, World pasteWorld, int xTransform, int zTransform) {
    }
}
