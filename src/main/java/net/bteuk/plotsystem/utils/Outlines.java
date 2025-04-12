package net.bteuk.plotsystem.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.bteuk.plotsystem.PlotSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

import static net.bteuk.network.utils.Constants.MAX_Y;
import static net.bteuk.network.utils.Constants.MIN_Y;

/**
 * This class deals with plot and zone outlines.
 * It will have methods to refresh outlines.
 */
public class Outlines {

    // List of block locations where outlines should be generated when the outlines are refreshed.
    // The y-level is calculated when the block is placed, as this could change often.
    final HashMap<Player, BlockLocations> outlineBlockLocations;

    final WorldGuard wg;

    public Outlines() {

        outlineBlockLocations = new HashMap<>();

        wg = WorldGuard.getInstance();

    }

    // Add player
    public BlockLocations addPlayer(Player player) {
        BlockLocations blockLocations = outlineBlockLocations.get(player);
        if (blockLocations == null) {
            blockLocations = new BlockLocations(player);
            outlineBlockLocations.put(player, blockLocations);
        }
        return blockLocations;
    }

    // Remove player
    public void removePlayer(Player player) {
        outlineBlockLocations.remove(player);
    }

    public void addPlotOutlineForPlayer(String plotID, Player player) {
        BlockLocations blockLocations = outlineBlockLocations.get(player);
        if (blockLocations != null) {
            RegionManager regions = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getWorld()));
            if (regions == null) {
                return;
            }
            ProtectedRegion region = regions.getRegion(plotID);
            if (region == null) {
                return;
            }
            // Get plot difficulty.
            int intPlotID = ParseUtils.toInt(plotID);
            if (intPlotID != 0) {
                int difficulty = PlotSystem.getInstance().plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + intPlotID + ";");
                blockLocations.addOutline(region, difficultyMaterial(difficulty));
            }
        }
    }

    public void removePlotOutlineForPlayer(String plotID, Player player) {
        BlockLocations blockLocations = outlineBlockLocations.get(player);
        if (blockLocations != null) {
            RegionManager regions = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getWorld()));
            if (regions == null) {
                return;
            }
            ProtectedRegion region = regions.getRegion(plotID);
            if (region == null) {
                return;
            }
            blockLocations.removeOutline(region);
        }
    }

    public void removeOutlinesForPlayer(Player player) {
        BlockLocations blockLocations = outlineBlockLocations.get(player);
        if (blockLocations != null) {
            blockLocations.removeOutlines();
        }
    }

    /**
     * Refresh outlines for a specific player.
     *
     * @param player the player to refresh the outlines for
     * @throws NullPointerException if the player has no outlines
     */
    public void refreshOutlinesForPlayer(Player player) {
        outlineBlockLocations.get(player).drawOutlines();
    }

    /**
     * Get all outlines near the player, remove all existing outlines from the object, but don't bother removing the blocks.
     * This method assumed the actual regions have not changed,
     * only that the player has moved position sufficiently that new outlines need to be drawn.
     *
     * @param user the user to add outlines for.
     */
    public void addNearbyOutlines(User user) {
        Player player = user.player;

        // If the player does not have a key, add it.
        BlockLocations locations;
        if (outlineBlockLocations.containsKey(player)) {
            locations = outlineBlockLocations.get(player);
            // If the world has changed, clear the list.
            if (!locations.getWorld().equals(player.getWorld())) {
                locations.clear(true);
                locations.setWorld(player.getWorld());
            } else {
                locations.clear(false);
            }
        } else {
            locations = addPlayer(player);
        }

        // Find the nearby regions and add them to the locations.
        RegionManager regions = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getWorld()));

        if (regions == null) {
            return;
        }

        // Get all regions within 100 blocks of the player.
        ProtectedRegion region = new ProtectedCuboidRegion("test",
                BlockVector3.at(player.getLocation().getX() - 100, 1, player.getLocation().getZ() - 100),
                BlockVector3.at(player.getLocation().getX() + 100, 1, player.getLocation().getZ() + 100));
        ApplicableRegionSet set = regions.getApplicableRegions(region);

        // Iterate through the regions and add the outlines.
        for (ProtectedRegion protectedRegion : set) {
            // Skip if this region is to be ignored for the player.
            if (user.isDisableOutlines() || user.getSkipOutlines().contains(protectedRegion.getId())) {
                continue;
            }

            int plotID = ParseUtils.toInt(protectedRegion.getId());

            // If plotID is 0, then it's a zone.
            if (plotID == 0) {

                locations.addOutline(protectedRegion, Material.PURPLE_CONCRETE.createBlockData());

            } else {

                // Get plot difficulty.
                int difficulty = PlotSystem.getInstance().plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + plotID + ";");

                locations.addOutline(protectedRegion, difficultyMaterial(difficulty));

            }
        }

        // Draw the outlines.
        refreshOutlinesForPlayer(player);
    }

    // Add an outline of a specific region for all nearby players.
    public void addOutline(List<BlockVector2> vector, World world, BlockData block) {

        // Find all nearby players.
        for (Player p : Bukkit.getOnlinePlayers()) {

            if (p.getWorld().equals(world)) {

                ProtectedRegion region = new ProtectedPolygonalRegion("test", vector, MIN_Y, (MAX_Y - 1));

                // Check if they are within 100 blocks of the region min and max point.
                if (p.getLocation().getX() > (region.getMinimumPoint().getX() - 100) &&
                        p.getLocation().getZ() > (region.getMinimumPoint().getZ() - 100) &&
                        p.getLocation().getX() < (region.getMaximumPoint().getX() + 100) &&
                        p.getLocation().getZ() < (region.getMaximumPoint().getZ() + 100)) {

                    // If the player does not have a key, add it.
                    BlockLocations locations;
                    if (outlineBlockLocations.containsKey(p)) {
                        locations = outlineBlockLocations.get(p);
                        // If the world has changed, clear the list.
                        if (!locations.getWorld().equals(p.getWorld())) {
                            locations.clear(true);
                            locations.setWorld(p.getWorld());
                        }
                    } else {
                        locations = addPlayer(p);
                    }

                    // Add points and draw it.
                    locations.addOutline(region, block);
                }
            }
        }
    }

    // Remove the outline of a specific region for all nearby players.
    public void removeOutline(ProtectedRegion region, World world) {

        // Find all nearby players.
        for (Player p : Bukkit.getOnlinePlayers()) {

            if (p.getWorld().equals(world)) {

                // Check if they are within 100 blocks of the region min and max point.
                if (p.getLocation().getX() > (region.getMinimumPoint().getX() - 100) &&
                        p.getLocation().getZ() > (region.getMinimumPoint().getZ() - 100) &&
                        p.getLocation().getX() < (region.getMaximumPoint().getX() + 100) &&
                        p.getLocation().getZ() < (region.getMaximumPoint().getZ() + 100)) {

                    // Remove the outline.
                    if (outlineBlockLocations.containsKey(p)) {
                        BlockLocations locations = outlineBlockLocations.get(p);
                        locations.removeOutline(region);
                    }
                }
            }
        }
    }

    /**
     * Add an outline from a list of BlockVector2.
     * This is for players drawing outlines with the selection tool.
     * Additionally, this is used to draw the outline of a plot in the before view.
     * This will be drawn in the same colour as the difficulty of the plot.
     *
     * @param player the player to draw the outline for
     * @param vector the vector that makes up the region
     * @param block  the block to draw the outline with
     */
    public void addOutline(Player player, List<BlockVector2> vector, BlockData block) {

        // If the player does not have a key, add it.
        BlockLocations locations;
        if (outlineBlockLocations.containsKey(player)) {
            locations = outlineBlockLocations.get(player);
            // If the world has changed, clear the list.
            if (!locations.getWorld().equals(player.getWorld())) {
                locations.clear(true);
                locations.setWorld(player.getWorld());
            }
        } else {
            locations = addPlayer(player);
        }

        ProtectedRegion region = new ProtectedPolygonalRegion("test", vector, MIN_Y, (MAX_Y - 1));

        // Add points and draw it.
        locations.addTempOutline(region, block);

    }

    // Remove the outlines from a list of BlockVector2.
    // This is for players drawing outlines with the selectiontool.
    public void removeOutline(Player player, List<BlockVector2> vector) {

        ProtectedRegion region = new ProtectedPolygonalRegion("test", vector, MIN_Y, (MAX_Y - 1));

        // Remove the outline.
        if (outlineBlockLocations.containsKey(player)) {
            BlockLocations locations = outlineBlockLocations.get(player);
            locations.removeTempOutline(region);
        }

    }

    public void addPoint(Player player, BlockVector2 point, BlockData block) {

        // If the player does not have a key, add it.
        BlockLocations locations;
        if (outlineBlockLocations.containsKey(player)) {
            locations = outlineBlockLocations.get(player);
            // If the world has changed, clear the list.
            if (!locations.getWorld().equals(player.getWorld())) {
                locations.clear(true);
                locations.setWorld(player.getWorld());
            }
        } else {
            locations = addPlayer(player);
        }

        // Add points and draw it.
        locations.addPoint(point, block);

    }

    public void removePoint(Player player, BlockVector2 point) {

        // If the player does not have a key, add it.
        BlockLocations locations;
        if (outlineBlockLocations.containsKey(player)) {
            locations = outlineBlockLocations.get(player);
            locations.removePoint(point);
        }
    }

    public void addLine(Player player, BlockVector2 pointMin, BlockVector2 pointMax, BlockData block) {

        // If the player does not have a key, add it.
        BlockLocations locations;
        if (outlineBlockLocations.containsKey(player)) {
            locations = outlineBlockLocations.get(player);
            // If the world has changed, clear the list.
            if (!locations.getWorld().equals(player.getWorld())) {
                locations.clear(true);
                locations.setWorld(player.getWorld());
            }
        } else {
            locations = addPlayer(player);
        }

        // Add points and draw it.
        locations.addLine(pointMin, pointMax, block);

    }

    public void removeLine(Player player, BlockVector2 pointMin, BlockVector2 pointMax) {

        // If the player does not have a key, add it.
        BlockLocations locations;
        if (outlineBlockLocations.containsKey(player)) {
            locations = outlineBlockLocations.get(player);
            locations.removeLine(pointMin, pointMax);
        }
    }

    // Returns the plot difficulty material.
    public BlockData difficultyMaterial(int difficulty) {

        return switch (difficulty) {
            case 1 -> Material.LIME_CONCRETE.createBlockData();
            case 2 -> Material.YELLOW_CONCRETE.createBlockData();
            case 3 -> Material.RED_CONCRETE.createBlockData();
            default -> null;
        };
    }
}
