package net.bteuk.plotsystem.commands;

import com.sk89q.worldedit.math.BlockVector3;
import net.bteuk.network.api.CoordinateAPI;
import net.bteuk.network.api.EventAPI;
import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.network.api.entity.NetworkLocation;
import net.bteuk.network.papercore.LocationAdapter;
import net.bteuk.network.papercore.WorldUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.CopyRegionFormat;
import net.bteuk.plotsystem.utils.Utils;
import net.bteuk.plotsystem.utils.plugins.Multiverse;
import net.bteuk.plotsystem.utils.plugins.WorldEditor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.btuk.network.lib.utils.ChatUtils;
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
import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public final class LocationCommand {

    private static final Component LOCATION_CREATE_COMMAND_FORMAT = ChatUtils.error("/plotsystem create location [name] <Xmin> <Ymin> <Zmin> <Xmax> <Ymax> <Zmax>");
    private static final Component LOCATION_UPDATE_COMMAND_FORMAT = ChatUtils.error("/plotsystem update location [name] <Xmin> <Ymin> <Zmin> <Xmax> <Ymax> <Zmax>");

    private final NetworkAPI networkAPI;

    private final PlotAPI plotAPI;

    private final CoordinateAPI coordinateAPI;

    private final EventAPI eventAPI;

    private final SQLAPI globalSQL;

    public LocationCommand(NetworkAPI networkAPI) {
        this.networkAPI = networkAPI;
        this.plotAPI = networkAPI.getPlotAPI();
        this.coordinateAPI = networkAPI.getCoordinateAPI();
        this.eventAPI = networkAPI.getEventAPI();
        this.globalSQL = networkAPI.getGlobalSQL();
    }

    public void createLocation(CommandSender sender, String[] args) {

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

        // Check if the location name is unique.
        if (plotAPI.hasLocation(commandArguments.location())) {
            sender.sendMessage(
                    ChatUtils.error("The location ").append(Component.text(commandArguments.location(), NamedTextColor.DARK_RED)).append(ChatUtils.error(" already exists.")));
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
        Multiverse.createVoidWorld(commandArguments.location(), commandArguments.location());

        String saveWorld = PlotSystem.getInstance().getConfig().getString("save_world_dimension");

        if (saveWorld == null) {
            sender.sendMessage(ChatUtils.error("The save world is not set in config."));
            return;
        }

        // Get worlds.
        World copy = WorldUtils.getWorld(saveWorld);
        World paste = WorldUtils.getWorld(commandArguments.location());

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

            int coordMin = coordinateAPI.addCoordinate(
                    LocationAdapter.adapt(new Location(WorldUtils.getWorld(commandArguments.location()), (regionXMin * 512), networkAPI.getMinY(), (regionZMin * 512), 0, 0)));

            int coordMax = coordinateAPI.addCoordinate(LocationAdapter.adapt(
                    new Location(WorldUtils.getWorld(commandArguments.location()), ((regionXMax * 512) + 511), networkAPI.getMaxY() - 1, ((regionZMax * 512) + 511), 0, 0)));

            // Add the location to the database.
            if (plotAPI.createLocation(commandArguments.location, commandArguments.location, PlotSystem.SERVER_NAME, coordMin, coordMax, xTransform, zTransform)) {

                sender.sendMessage(ChatUtils.success("Created new location ").append(Component.text(commandArguments.location(), NamedTextColor.DARK_AQUA)));

                // Set the status of all effected regions in the region database.
                for (int i = regionXMin; i <= regionXMax; i++) {
                    for (int j = regionZMin; j <= regionZMax; j++) {

                        String region = i + "," + j;

                        // Change region status in region database.
                        // If it already exists remove members.
                        eventAPI.createEvent(null, globalSQL.getString("SELECT name FROM server_data WHERE type='EARTH';"), "region set plotsystem " + region);

                        // Add region to database.
                        plotAPI.createPlotRegion(region, PlotSystem.SERVER_NAME, commandArguments.location());
                    }
                }

            } else {

                sender.sendMessage(ChatUtils.error("An error occurred, please check the console for more info."));
                LOGGER.warning("An error occured while adding new location!");

            }

            teleportToLocation(sender, commandArguments.location(), coordMin, coordMax);
        });
    }

    public void updateLocation(CommandSender sender, String[] args) {

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

        // Check if the location name exists.
        if (!plotAPI.hasLocation(commandArguments.location())) {
            sender.sendMessage(ChatUtils.error("Location %s does not exist.", commandArguments.location()));
            return;
        }

        // Check if the new area is equal or larger than the existing area.
        final int minCoordinateId = plotAPI.getLocationCoordMin(commandArguments.location());
        final int maxCoordinateId = plotAPI.getLocationCoordMax(commandArguments.location());

        NetworkLocation minCoordinate = coordinateAPI.getLocation(minCoordinateId);
        NetworkLocation maxCoordinate = coordinateAPI.getLocation(maxCoordinateId);

        if (commandArguments.isSmallerThan(minCoordinate, maxCoordinate)) {
            sender.sendMessage(ChatUtils.error("The new area must not be smaller than the current area."));
            return;
        }

        // Get the exact regions of the selected coordinates.
        int regionXMin = Math.floorDiv(commandArguments.xmin(), 512);
        int regionZMin = Math.floorDiv(commandArguments.zmin(), 512);

        int regionXMax = Math.floorDiv(commandArguments.xmax(), 512);
        int regionZMax = Math.floorDiv(commandArguments.zmax(), 512);

        // Get the coordinate transformation of the location.
        int xTransform = plotAPI.getXTransform(commandArguments.location());
        int zTransform = plotAPI.getZTransform(commandArguments.location());

        // Get the worlds.
        String saveWorld = PlotSystem.getInstance().getConfig().getString("save_world_dimension");
        if (saveWorld == null) {
            sender.sendMessage(ChatUtils.error("The save world is not set in config."));
            return;
        }

        // Get worlds.
        World copy = WorldUtils.getWorld(saveWorld);
        World paste = WorldUtils.getWorld(commandArguments.location());

        // Check that the worlds are not null, else delete the Multiverse world.
        if (copy == null || paste == null) {
            sender.sendMessage("An error occurred, please contact an admin.");
            return;
        }

        // Determine which regions are new, only copy them.
        List<String> existingRegions = plotAPI.getLocationRegions(commandArguments.location());
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
        RegionHolder regionHolder = new RegionHolder(regionsToAdd, (int) minCoordinate.y(), (int) maxCoordinate.y(), copy, paste, xTransform, zTransform);
        List<CopyRegionFormat> regions = getCopyRegions(sender, regionHolder);

        // Copy-paste the regions in the save world.
        // Iterate through the regions one-by-one.
        // Run it asynchronously to not freeze the server.
        Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {

            copyRegions(sender, regions);

            World world = WorldUtils.getWorld(commandArguments.location());
            coordinateAPI.updateCoordinate(minCoordinateId, LocationAdapter.adapt(new Location(world, (regionXMin * 512), world.getMinHeight(), (regionZMin * 512), 0, 0)));
            coordinateAPI.updateCoordinate(maxCoordinateId,
                    LocationAdapter.adapt(new Location(world, ((regionXMax * 512) + 511), world.getMaxHeight() - 1, ((regionZMax * 512) + 511), 0, 0)));

            // Add the location to the database.
            for (Region region : regionsToAdd) {
                // Change region status in region database.
                // If it already exists remove members.
                eventAPI.createEvent(null, globalSQL.getString("SELECT name FROM server_data WHERE type='EARTH';"), "region set plotsystem " + region.name());

                // Add region to database.
                plotAPI.createPlotRegion(region.name(), PlotSystem.SERVER_NAME, commandArguments.location());
            }

            sender.sendMessage(ChatUtils.success("Updated location %s", commandArguments.location()));

            teleportToLocation(sender, commandArguments.location(), minCoordinateId, maxCoordinateId);
        });
    }

    public void deleteLocation(CommandSender sender, String[] args) {

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

        // Check if location exists.
        if (!(plotAPI.hasLocation(args[2]))) {
            sender.sendMessage(ChatUtils.error("The location %s does not exist.", args[2]));
            return;
        }

        // Check if the location is on this server.
        if (!(plotAPI.getLocationServer(args[2]).equals(PlotSystem.SERVER_NAME))) {
            sender.sendMessage(ChatUtils.error("This location is not on this server."));
            return;
        }

        // If location has plots, cancel.
        if (!plotAPI.getActivePlotsForLocation(args[2]).isEmpty()) {
            sender.sendMessage(ChatUtils.error("This location active has plots, all plots must be deleted or completed to remove the location."));
            return;
        }

        // Teleport all players out of the world, so it can be deleted.
        // Get the worlds.
        String saveWorldName = PlotSystem.getInstance().getConfig().getString("save_world_dimension");
        if (saveWorldName == null) {
            sender.sendMessage(ChatUtils.error("The save world is not set in config."));
            return;
        }

        // Get save world.
        World saveWorld = WorldUtils.getWorld(saveWorldName);

        teleportPlayersFromLocation(args[2], saveWorld);

        // Delete location.
        if (Multiverse.deleteWorld(args[2])) {

            // Delete location from database.
            plotAPI.deleteLocation(args[2]);
            sender.sendMessage(ChatUtils.success("Deleted location ").append(Component.text(args[2], NamedTextColor.DARK_AQUA)));
            LOGGER.info("Deleted location " + args[2] + ".");

            // Get regions from the database.
            List<String> regions = plotAPI.getLocationRegions(args[2]);

            // Delete regions from database.
            plotAPI.deleteRegionsForLocation(args[2]);

            // Iterate through regions to unlock them on Earth.
            for (String region : regions) {
                eventAPI.createEvent(null, globalSQL.getString("SELECT name FROM server_data WHERE type='EARTH';"), "region set default " + region);
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

        // Ensure the location name is lowercase.
        args[2] = args[2].toLowerCase();

        return new CommandArguments(args[2], xmin, ymin, zmin, xmax, ymax, zmax);
    }

    private List<CopyRegionFormat> getCopyRegions(CommandSender sender, RegionHolder regionHolder) {
        List<CopyRegionFormat> regionsToCopy = new ArrayList<>();

        final int yMin = max(regionHolder.ymin(), networkAPI.getMinY());
        final int yMax = min(regionHolder.ymax(), networkAPI.getMaxY() - 1);

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

    private void teleportToLocation(CommandSender sender, String location, int coordMin, int coordMax) {
        // If sender is a player teleport them to the location.
        if (sender instanceof Player p) {

            // Get middle.
            double x = ((coordinateAPI.getX(coordMax) + coordinateAPI.getX(coordMin)) / 2) + plotAPI.getXTransform(location);

            double z = ((coordinateAPI.getZ(coordMax) + coordinateAPI.getZ(coordMin)) / 2) + plotAPI.getZTransform(location);

            // Teleport to the location.
            World world = WorldUtils.getWorld(location);

            double y = 64;
            if (world != null) {
                y = world.getHighestBlockYAt((int) x, (int) z);
                y++;
            }

            eventAPI.createTeleportEvent(false, p.getUniqueId().toString(),
                    "teleport " + location + " " + x + " " + y + " " + z + " " + p.getLocation().getYaw() + " " + p.getLocation().getPitch(),
                    "&aTeleported to location &3" + plotAPI.getLocationAlias(location), LocationAdapter.adapt(p.getLocation()));
        }
    }

    private void teleportPlayersFromLocation(String location, World saveWorld) {
        int coordMin = plotAPI.getLocationCoordMin(location);
        int coordMax = plotAPI.getLocationCoordMax(location);

        // Get middle.
        double x = ((coordinateAPI.getX(coordMax) + coordinateAPI.getX(coordMin)) / 2);
        double z = ((coordinateAPI.getZ(coordMax) + coordinateAPI.getZ(coordMin)) / 2);

        // Teleport all players away from the location.
        Location teleportLocation = new Location(saveWorld, x, Utils.getHighestYAt(saveWorld, (int) x, (int) z), z);
        PlotSystem.getInstance().getServer().getOnlinePlayers().forEach(player -> {
            if (player.getWorld().key().asMinimalString().equals(location)) {
                player.teleport(teleportLocation);
                player.sendMessage(ChatUtils.success("Teleported to save world, location %s is being deleted.", location));
            }
        });
    }

    private record CommandArguments(String location, int xmin, int ymin, int zmin, int xmax, int ymax, int zmax) {

        /**
         * Checks whether the area defined by the command arguments is smaller than the area defined by the given coordinates.
         *
         * @param coordMin the min coordinate
         * @param coordMax the max coordinate
         * @return whether the area is smaller than the input coordinates
         */
        private boolean isSmallerThan(NetworkLocation coordMin, NetworkLocation coordMax) {
            boolean smaller = coordMin == null || coordMax == null;
            smaller = smaller || (xmin > coordMin.x());
            smaller = smaller || (zmin > coordMin.z());
            smaller = smaller || (xmax < coordMax.x());
            smaller = smaller || (zmax < coordMax.z());
            return smaller;
        }
    }

    private record Region(String name, int regionX, int regionZ) {
    }

    private record RegionHolder(List<Region> regionsToAdd, int ymin, int ymax, World copyWorld, World pasteWorld, int xTransform, int zTransform) {
    }
}
