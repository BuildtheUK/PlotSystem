package net.bteuk.plotsystem.commands;

import com.sk89q.worldedit.math.BlockVector3;
import net.bteuk.network.Network;
import net.bteuk.network.eventing.events.EventManager;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.NetworkUser;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.gui.CreatePlotGui;
import net.bteuk.plotsystem.gui.CreateZoneGui;
import net.bteuk.plotsystem.utils.CopyRegionFormat;
import net.bteuk.plotsystem.utils.User;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.bteuk.network.utils.Constants.MAX_Y;
import static net.bteuk.network.utils.Constants.MIN_Y;
import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class CreateCommand {

    private final GlobalSQL globalSQL;
    private final PlotSQL plotSQL;

    public CreateCommand(GlobalSQL globalSQL, PlotSQL plotSQL) {

        this.globalSQL = globalSQL;
        this.plotSQL = plotSQL;

    }

    public void create(CommandSender sender, String[] args) {

        if (args.length < 2) {

            sender.sendMessage(ChatUtils.error("/plotsystem create [plot, location, zone]"));
            return;

        }

        switch (args[1]) {
            case "plot" -> createPlot(sender);
            case "location" -> createLocation(sender, args);
            case "zone" -> createZone(sender);
            default -> sender.sendMessage(ChatUtils.error("/plotsystem create [plot, location, zone]"));
        }

    }

    private void createPlot(CommandSender sender) {

        // Check if the sender is a player
        if (!(sender instanceof Player)) {

            sender.sendMessage(ChatUtils.error("This command can only be used by players!"));
            return;

        }

        // Get the user
        User u = PlotSystem.getInstance().getUser((Player) sender);

        // Check if the user has permission to use this command
        if (!u.player.hasPermission("uknet.plots.create.plot")) {

            u.player.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
            return;

        }

        // Check if the plot is valid, meaning that at least 3 points are selected with the selection tool.
        if (u.selectionTool.size() < 3) {

            u.player.sendMessage(ChatUtils.error("You must select at least 3 points for a valid plot!"));
            return;

        }

        // Open the plot creation menu
        // Calculate the area of the plot and set a default size estimate.
        u.selectionTool.area();
        u.selectionTool.setDefaultSize();

        // Get the user from the network plugin, this plugin handles all guis.
        NetworkUser user = Network.getInstance().getUser(u.player);

        // Open the create gui.
        u.createPlotGui = new CreatePlotGui(u);
        u.createPlotGui.open(user);

    }

    private void createLocation(CommandSender sender, String[] args) {

        // Check if the sender is a player.
        // If so, check if they have permission.
        if (sender instanceof Player p) {
            if (!p.hasPermission("uknet.plots.create.location")) {

                p.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
                return;

            }
        }

        // Check if they have enough args.
        if (args.length < 9) {

            sender.sendMessage(ChatUtils.error("/plotsystem create location [name] <Xmin> <Ymin> <Zmin> <Xmax> <Ymax> <Zmax>"));
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

            sender.sendMessage(ChatUtils.error("/plotsystem create location [name] <Xmin> <Ymin> <Zmin> <Xmax> <Ymax> <Zmax>"));
            return;

        }

        // Check if the location name is unique.
        if (plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + args[2] + "';")) {

            sender.sendMessage(ChatUtils.error("The location ")
                    .append(Component.text(args[2], NamedTextColor.DARK_RED))
                    .append(ChatUtils.error(" already exists.")));
            return;

        }

        // Get the exact regions of the selected coordinates.
        int regionXMin = Math.floorDiv(xmin, 512);
        int regionZMin = Math.floorDiv(zmin, 512);

        int regionXMax = Math.floorDiv(xmax, 512);
        int regionZMax = Math.floorDiv(zmax, 512);

        // Calculate the coordinate transformation.
        int xTransform = -(regionXMin * 512);
        int zTransform = -(regionZMin * 512);

        // Create the world and add the regions.
        Multiverse.createVoidWorld(args[2]);

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
            Multiverse.deleteWorld(args[2]);
            return;

        }

        // Copy paste the regions in the save world.
        // Iterate through the regions one-by-one.
        // Run it asynchronously to not freeze the server.
        sender.sendMessage(ChatUtils.success("Transferring terrain, this may take a while."));

        // Create atomic boolean to query whether a region can be copied.
        AtomicBoolean isReady = new AtomicBoolean(true);

        // Create a list of regions to copy paste.
        ArrayList<CopyRegionFormat> regions = new ArrayList<>();

        final int yMin = max(ymin, MIN_Y);
        final int yMax = min(ymax, MAX_Y - 1);

        for (int i = regionXMin; i <= regionXMax; i++) {
            for (int j = regionZMin; j <= regionZMax; j++) {

                // Split the region into 4 equal segments of 256x256.
                regions.add(new CopyRegionFormat(
                        copy, paste,
                        BlockVector3.at(i * 512, yMin, j * 512),
                        BlockVector3.at(i * 512 + 255, yMax, j * 512 + 255),
                        BlockVector3.at(i * 512 + xTransform, yMin, j * 512 + zTransform))
                );

                regions.add(new CopyRegionFormat(
                        copy, paste,
                        BlockVector3.at(i * 512 + 256, yMin, j * 512),
                        BlockVector3.at(i * 512 + 511, yMax, j * 512 + 255),
                        BlockVector3.at(i * 512 + 256 + xTransform, yMin, j * 512 + zTransform))
                );

                regions.add(new CopyRegionFormat(
                        copy, paste,
                        BlockVector3.at(i * 512, yMin, j * 512 + 256),
                        BlockVector3.at(i * 512 + 255, yMax, j * 512 + 511),
                        BlockVector3.at(i * 512 + xTransform, yMin, j * 512 + 256 + zTransform))
                );

                regions.add(new CopyRegionFormat(
                        copy, paste,
                        BlockVector3.at(i * 512 + 256, yMin, j * 512 + 256),
                        BlockVector3.at(i * 512 + 511, yMax, j * 512 + 511),
                        BlockVector3.at(i * 512 + 256 + xTransform, yMin, j * 512 + 256 + zTransform))
                );
            }
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
                            sender.sendMessage(ChatUtils.success("Segment copied, there are ")
                                    .append(Component.text(regions.size(), NamedTextColor.DARK_AQUA))
                                    .append(ChatUtils.success(" remaining.")));
                            LOGGER.info("Segment copied, there are " + regions.size() + " remaining.");
                            isReady.set(true);
                        }

                    });
                }
            }

            sender.sendMessage(ChatUtils.success("Terrain transfer has been completed."));

            int coordMin = globalSQL.addCoordinate(new Location(
                    Bukkit.getWorld(args[2]),
                    (regionXMin * 512), MIN_Y, (regionZMin * 512), 0, 0));

            int coordMax = globalSQL.addCoordinate(new Location(
                    Bukkit.getWorld(args[2]),
                    ((regionXMax * 512) + 511), MAX_Y - 1, ((regionZMax * 512) + 511), 0, 0));

            // Add the location to the database.
            if (plotSQL.update("INSERT INTO location_data(name, alias, server, coordMin, coordMax, xTransform, zTransform) VALUES('"
                    + args[2] + "','" + args[2] + "','" + PlotSystem.SERVER_NAME + "'," + coordMin + "," + coordMax + "," + xTransform + "," + zTransform + ");")) {

                sender.sendMessage(ChatUtils.success("Created new location ")
                        .append(Component.text(args[2], NamedTextColor.DARK_AQUA)));

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
                        plotSQL.update("INSERT INTO regions(region,server,location) VALUES('" + region + "','" + PlotSystem.SERVER_NAME + "','" + args[2] + "');");

                    }
                }

            } else {

                sender.sendMessage(ChatUtils.error("An error occurred, please check the console for more info."));
                LOGGER.warning("An error occured while adding new location!");

            }

            // If sender is a player teleport them to the location.
            if (sender instanceof Player p) {

                // Get middle.
                double x = ((globalSQL.getDouble("SELECT x FROM coordinates WHERE id=" + coordMax + ";") +
                        globalSQL.getDouble("SELECT x FROM coordinates WHERE id=" + coordMin + ";")) / 2) +
                        plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + args[2] + "';");

                double z = ((globalSQL.getDouble("SELECT z FROM coordinates WHERE id=" + coordMax + ";") +
                        globalSQL.getDouble("SELECT z FROM coordinates WHERE id=" + coordMin + ";")) / 2) +
                        plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + args[2] + "';");

                // Teleport to the location.
                World world = Bukkit.getWorld(args[2]);

                double y = 64;
                if (world != null) {
                    y = world.getHighestBlockYAt((int) x, (int) z);
                    y++;
                }

                EventManager.createTeleportEvent(false, p.getUniqueId().toString(), "network", "teleport " + args[2] + " " + x + " " + y + " " + z + " "
                                + p.getLocation().getYaw() + " " + p.getLocation().getPitch(),
                        "&aTeleported to location &3" + plotSQL.getString("SELECT alias FROM location_data WHERE name='" + args[2] + "';"), p.getLocation());
            }

        });
    }

    public void createZone(CommandSender sender) {

        // Check if the sender is a player
        if (!(sender instanceof Player)) {

            sender.sendMessage(ChatUtils.error("This command can only be used by players!"));
            return;

        }

        // Get the user
        User u = PlotSystem.getInstance().getUser((Player) sender);

        // Check if the user has permission to use this command
        if (!u.player.hasPermission("uknet.plots.create.zone")) {

            u.player.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
            return;

        }

        // Check if the selection is valid, meaning that at least 3 points are selected with the selection tool.
        if (u.selectionTool.size() < 3) {

            u.player.sendMessage(ChatUtils.error("You must select at least 3 points for a valid zone!"));
            return;

        }

        // If the player already has a zones, cancel, as this is the maximum.
        // Lastly there is a limit of 21 total zones at a time.
        if (plotSQL.hasRow("SELECT id FROM zone_members WHERE uuid='" + u.player.getUniqueId() + "' AND is_owner=1;")) {

            u.player.sendMessage(ChatUtils.error("You already have a zone, close this before creating a new one."));
            return;

        } else if (plotSQL.getInt("SELECT count(id) FROM zones WHERE status='open';") >= 21) {

            u.player.sendMessage(ChatUtils.error("There are currently 21 zones, this is the maximum."));
            return;

        }

        // Get the user from the network plugin, this plugin handles all guis.
        NetworkUser user = Network.getInstance().getUser(u.player);

        // Open the create zone gui.
        u.createZoneGui = new CreateZoneGui(u);
        u.createZoneGui.open(user);
    }
}
