package net.bteuk.plotsystem.utils;

import com.sk89q.worldedit.math.BlockVector2;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Time;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.plugins.WGCreatePlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;

public class SelectionTool extends WGCreatePlot {

    public final BlockData outlineBlock = Material.LIGHT_BLUE_CONCRETE.createBlockData();
    public final BlockData limeConc = Material.LIME_CONCRETE.createBlockData();
    public final BlockData yellowConc = Material.YELLOW_CONCRETE.createBlockData();
    public final BlockData redConc = Material.RED_CONCRETE.createBlockData();
    // Stores a reference to the user for simplicity.
    private final User u;
    // This vector of BlockVector2 (2d points (x,z)) represent the selected points.
    private final ArrayList<BlockVector2> vector;
    // PlotSQL
    private final PlotSQL plotSQL;
    // Outlines
    private final Outlines outlines;
    // Size and difficulty of the plot.
    // Represented by integer values of 1-3.
    // Size: 1=small, 2=medium, 3=large
    // Difficulty: 1=easy, 2=normal, 3=hard
    public int size;
    public int difficulty;
    // Zones settings.
    public int hours;
    public boolean is_public;
    // The world where the selection is being made.
    private World world;
    // The location (plot system location) where the plot is.
    private String location;
    // Area of the plot (m^2).
    private int area;

    // Constructor, sets up the basics of the selection tool, including default values fo size and difficulty.
    public SelectionTool(User u, PlotSQL plotSQL) {

        this.u = u;
        vector = new ArrayList<>();
        this.plotSQL = plotSQL;

        // Set default size and difficulty
        size = 1;
        difficulty = 1;

        hours = 2;
        is_public = false;

        outlines = PlotSystem.getInstance().getOutlines();

    }

    // Clear the selection.
    // This is executed when the player starts a new selection (by left-clicking with the selection tool), or when the player creates a plot.
    public void clear() {

        world = null;
        location = null;

        size = 1;
        difficulty = 1;

        hours = 2;
        is_public = false;

        // Remove outline blocks based on the previous selection.
        clearOutlines();

        vector.clear();

    }

    // Remove old outlines based on the vector.
    private void clearOutlines() {

        // Remove outline blocks based on the previous selection.
        if (vector.size() == 1) {

            // Remove the single point.
            outlines.removePoint(u.player, vector.getFirst());

        } else if (vector.size() == 2) {

            // Remove the line.
            outlines.removeLine(u.player, vector.get(0), vector.get(1));

        } else if (vector.size() > 2) {

            // Remove the outline.
            outlines.removeOutline(u.player, vector);

        }
    }

    // Starts a new selection with the selection tool, represents left-clicking.
    public void startSelection(Block block, String location) {

        // Since this is the start of a selection make sure the vector is empty.
        clear();

        // Set the world.
        world = block.getWorld();

        // Get the x,z of the block clicked and store it in the vector.
        BlockVector2 bv2 = BlockVector2.at(block.getX(), block.getZ());
        vector.add(bv2);

        Bukkit.getScheduler().scheduleSyncDelayedTask(PlotSystem.getInstance(),
                () -> outlines.addPoint(u.player, bv2, outlineBlock), 1L);

        // Set the location.
        this.location = location;

    }

    // Add a point to the selection, represents right-clicking.
    public boolean addPoint(Block block) {

        // Create the blockvector2.
        BlockVector2 bv2 = BlockVector2.at(block.getX(), block.getZ());

        // If the distance in a plot exceeds 500 blocks it's too large.
        // Send an error message to the player.
        if (bv2.distance(vector.get(0)) > 500) {

            u.player.sendMessage(ChatUtils.error("This point is over 500 blocks from the first point, please make the selection smaller."));
            return false;

        } else {

            // Clear previous selection outline.
            clearOutlines();

            vector.add(bv2);

            // Create new outline.
            // Adding a point already means at least 2 points, so we can ignore the 1 point case.
            if (vector.size() == 2) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(PlotSystem.getInstance(),
                        () -> outlines.addLine(u.player, vector.getFirst(), vector.get(1), outlineBlock), 1L);
            } else {
                Bukkit.getScheduler().scheduleSyncDelayedTask(PlotSystem.getInstance(),
                        () -> outlines.addOutline(u.player, vector, outlineBlock), 1L);
            }

            return true;

        }
    }

    public void giveSelectionTool() {

        // Get the player inventory and check whether they already have the selection tool.
        PlayerInventory i = u.player.getInventory();

        // Check if the player already has the selection tool in their inventory.
        if (i.contains(PlotSystem.selectionTool)) {

            // Get the selection tool from their inventory and swap it with the item in their hand.
            i.setItem(i.first(PlotSystem.selectionTool), i.getItemInMainHand());
            i.setItemInMainHand(PlotSystem.selectionTool);

            u.player.sendMessage(ChatUtils.success("Switched to selection tool from inventory."));

        } else {

            // If they don't have the selection tool already set it in their main hand.
            i.setItemInMainHand(PlotSystem.selectionTool);

            u.player.sendMessage(ChatUtils.success("Set selection tool to main hand."));

        }
    }

    // Return number of elements in vector.
    public int size() {

        return vector.size();

    }

    public World world() {

        return world;

    }

    // Sets the area of the selection.
    public void area() {

        // If the vector has less than 3 points you can't get an area.
        if (size() < 3) {
            area = 0;
        }

        int sum = 0;

        for (int i = 0; i < size(); i++) {

            if (i == (size() - 1)) {

                sum += (((vector.get(i).getZ() + vector.getFirst().getZ()) / 2) * (vector.getFirst().getX() - vector.get(i).getX()));

            } else {

                sum += (((vector.get(i).getZ() + vector.get(i + 1).getZ()) / 2) * (vector.get(i + 1).getX() - vector.get(i).getX()));

            }
        }

        area = Math.abs(sum);

    }

    // Sets the default plot size.
    public void setDefaultSize() {

        if (area <= PlotSystem.getInstance().getConfig().getInt("default_size.small")) {

            size = 1;

        } else if (area <= PlotSystem.getInstance().getConfig().getInt("default_size.medium")) {

            size = 2;

        } else {

            size = 3;

        }
    }

    // Before this method can be run the player must have gone through the plot creation gui.
    // This will make sure the difficulty and size are set.
    public void createPlot() {

        // Create the plot.
        if (createPlot(u.player, world, location, vector, plotSQL, size, difficulty)) {

            // Store plot bounds.
            int i = 1;
            for (BlockVector2 point : vector) {

                plotSQL.update("INSERT INTO plot_corners(id,corner,x,z) VALUES(" +
                        plotID + "," + i + "," + point.getX() + "," + point.getZ() + ");");
                i++;

            }

            // Send feedback.
            u.player.sendMessage(ChatUtils.success("Plot created with ID ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(", difficulty "))
                    .append(Component.text(PlotValues.difficultyName(difficulty), NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(" and size "))
                    .append(Component.text(PlotValues.sizeName(size), NamedTextColor.DARK_AQUA)));
            PlotSystem.LOGGER.info("Plot created with ID " + plotID +
                    ", difficulty " + PlotValues.difficultyName(difficulty) +
                    " and size " + PlotValues.sizeName(size));

            // Clear previous blocks.
            clearOutlines();

            // Change plot outline to blockType of plot, rather than of selection.
            outlines.addOutline(vector, world, difficultyMaterial(difficulty));

        }
    }

    // Before this method can be run the player must have gone through the zone creation gui.
    // This will make sure public/private and expiration time has been set.
    public void createZone() {

        long expiration = Time.currentTime() + (hours * 1000L * 60L * 60L);

        // Create the zone.
        if (createZone(u.player, world, location, vector, plotSQL, expiration, is_public)) {

            // Add owner.
            plotSQL.update("INSERT INTO zone_members(id,uuid,is_owner) VALUES(" + plotID + ",'" + u.player.getUniqueId() + "',1);");

            // Store zone bounds.
            int i = 1;
            for (BlockVector2 point : vector) {

                plotSQL.update("INSERT INTO zone_corners(id,corner,x,z) VALUES(" +
                        plotID + "," + i + "," + point.getX() + "," + point.getZ() + ");");
                i++;

            }

            // Send feedback.
            u.player.sendMessage(ChatUtils.success("Zone created with ID ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(", it will expire at "))
                    .append(Component.text(Time.getDateTime(expiration), NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(", this can be extended in the Zone Menu.")));
            PlotSystem.LOGGER.info("Zone created with ID " + plotID +
                    ", it will expire at " + Time.getDateTime(expiration));

            // Clear previous blocks.
            clearOutlines();

            // Change plot outline to blockType of plot, rather than of selection.
            outlines.addOutline(vector, world, Material.PURPLE_CONCRETE.createBlockData());

        }
    }

    // Returns the plot difficulty material.
    public BlockData difficultyMaterial(int difficulty) {

        return switch (difficulty) {
            case 1 -> limeConc;
            case 2 -> yellowConc;
            case 3 -> redConc;
            default -> null;
        };
    }
}
