package net.bteuk.plotsystem.utils;

import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.plugins.WGCreatePlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.btuk.minecraft.selection.EditableSelection;
import org.btuk.outlines.geometry.IntPoint2d;

import java.util.List;
import java.util.Objects;

public class SelectionTool extends WGCreatePlot {

    private final EditableSelection editableSelection;

    // Stores a reference to the user for simplicity.
    private final User u;

    // Size and difficulty of the plot.
    // Represented by integer values of 1-3.
    // Size: 1=small, 2=medium, 3=large
    // Difficulty: 1=easy, 2=normal, 3=hard
    public int size;
    public int difficulty;

    // Zones settings.
    public int hours;
    public boolean isPublic;
    // Area of the plot (m^2).
    private int area;

    public SelectionTool(User u, NetworkAPI networkAPI, PlotHelper plotHelper, EditableSelection editableSelection) {
        super(networkAPI, plotHelper);

        this.editableSelection = editableSelection;

        this.u = u;

        // Set default size and difficulty
        size = 1;
        difficulty = 1;

        hours = 2;
        isPublic = false;
    }

    public void giveSelectionTool() {
        editableSelection.giveSelectionTool(u.player);
    }

    // Return number of elements in vector.
    public int size() {
        List<IntPoint2d> selection = editableSelection.getPlayerSelection(u.player.getUniqueId());
        if (selection == null) {
            return 0;
        }
        return selection.size();
    }

    // Sets the area of the selection.
    public void area() {

        // If the vector has less than 3 points you can't get an area.
        if (size() < 3) {
            area = 0;
        }

        int sum = 0;

        List<IntPoint2d> selection = editableSelection.getPlayerSelection(u.player.getUniqueId());
        for (int i = 0; i < selection.size(); i++) {

            if (i == (size() - 1)) {

                sum += (((selection.get(i).z() + selection.getFirst().z()) / 2) * (selection.getFirst().x() - selection.get(i).x()));

            } else {

                sum += (((selection.get(i).z() + selection.get(i + 1).z()) / 2) * (selection.get(i + 1).x() - selection.get(i).x()));

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

        List<IntPoint2d> selection = editableSelection.getPlayerSelection(u.player.getUniqueId());
        if (selection == null) {
            return;
        }

        // Create the plot.
        if (createPlot(u.player, u.player.getWorld(), u.player.getWorld().getName(), selection, size, difficulty)) {

            int xTransform = plotAPI.getXTransform(u.player.getWorld().getName());
            int zTransform = plotAPI.getZTransform(u.player.getWorld().getName());

            // Store the plot corners with coordinate transform.
            int i = 1;
            for (IntPoint2d point : selection) {
                plotAPI.createPlotCorner(plotID, i, (point.x() - xTransform), (point.z() - zTransform));
                i++;
            }

            // Send feedback.
            u.player.sendMessage(ChatUtils.success("Plot created with ID ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(", difficulty "))
                    .append(Component.text(Objects.requireNonNull(PlotValues.difficultyName(difficulty)), NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(" and size "))
                    .append(Component.text(Objects.requireNonNull(PlotValues.sizeName(size)), NamedTextColor.DARK_AQUA)));
            PlotSystem.LOGGER.info("Plot created with ID " + plotID +
                    ", difficulty " + PlotValues.difficultyName(difficulty) +
                    " and size " + PlotValues.sizeName(size));
        }
    }

    // Before this method can be run the player must have gone through the zone creation gui.
    // This will make sure public/private and expiration time has been set.
    public void createZone() {

        List<IntPoint2d> selection = editableSelection.getPlayerSelection(u.player.getUniqueId());
        if (selection == null) {
            return;
        }

        long expiration = System.currentTimeMillis() + (hours * 1000L * 60L * 60L);

        // Create the zone.
        if (createZone(u.player, u.player.getWorld(), u.player.getWorld().getName(), selection, expiration, isPublic)) {

            // Add owner.
            plotAPI.createZoneOwner(plotID, u.player.getUniqueId().toString());

            // Store zone bounds.
            int i = 1;
            for (IntPoint2d point : selection) {
                plotAPI.createZoneCorner(plotID, i, point.x(), point.z());
                i++;
            }

            // Send feedback.
            u.player.sendMessage(ChatUtils.success("Zone created with ID ")
                    .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(", it will expire at "))
                    .append(Component.text(Utils.getDateTime(expiration), NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(", this can be extended in the Zone Menu.")));
            PlotSystem.LOGGER.info("Zone created with ID " + plotID +
                    ", it will expire at " + Utils.getDateTime(expiration));
        }
    }
}
