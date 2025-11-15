package net.bteuk.plotsystem.utils;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.round;

// A list of block locations, can be altered in bulk.
public class BlockLocations {

    // Store the world of the player.
    private final Player player;
    private final ArrayList<BlockLocation> locations;
    private final ArrayList<BlockLocation> tempLocations;
    @Getter
    @Setter
    private World world;

    public BlockLocations(Player player) {

        this.player = player;
        this.world = player.getWorld();
        locations = new ArrayList<>();
        tempLocations = new ArrayList<>();

    }

    // Add all the block locations that make up the outline of the region.
    public void addOutline(ProtectedRegion region, BlockData block) {

        // min and max must be within 128 blocks of the player,
        // this is to prevent outlines for very large regions, when they aren't necessary.
        int minX = region.getMinimumPoint().x() < (player.getLocation().x() - 128) ? (player.getLocation().getBlockX() - 128) : region.getMinimumPoint().x();
        int minZ = region.getMinimumPoint().z() < (player.getLocation().z() - 128) ? (player.getLocation().getBlockZ() - 128) : region.getMinimumPoint().z();

        int maxX = region.getMaximumPoint().x() > (player.getLocation().x() + 128) ? (player.getLocation().getBlockX() + 128) : region.getMaximumPoint().x();
        int maxZ = region.getMaximumPoint().z() > (player.getLocation().z() + 128) ? (player.getLocation().getBlockZ() + 128) : region.getMaximumPoint().z();

        // Iterate in the bounding box.
        for (int i = minX; i <= maxX; i++) {
            for (int j = minZ; j <= maxZ; j++) {

                // Check if the point is contained in the region.
                // If the previous point was not contained, set the block to be an outline.
                if (region.contains(BlockVector2.at(i, j))) {

                    // Check if any of the surrounding blocks are not contained,
                    // if true then this block is an outline.
                    if (!(region.contains(BlockVector2.at(i - 1, j)) && region.contains(BlockVector2.at(i + 1, j)) && region.contains(BlockVector2.at(i, j - 1)) && region.contains(
                            BlockVector2.at(i, j + 1)))) {

                        BlockLocation bl = new BlockLocation(block, i, j);

                        locations.add(bl);

                        drawBlock(bl);
                    }
                }
            }
        }
    }

    // Remove all the block locations that make up the outline of the region.
    // Additionally replace the fake block with air.
    public void removeOutline(ProtectedRegion region) {

        int minX = region.getMinimumPoint().x() < (player.getLocation().x() - 128) ? (player.getLocation().getBlockX() - 128) : region.getMinimumPoint().x();
        int minZ = region.getMinimumPoint().z() < (player.getLocation().z() - 128) ? (player.getLocation().getBlockZ() - 128) : region.getMinimumPoint().z();

        int maxX = region.getMaximumPoint().x() > (player.getLocation().x() + 128) ? (player.getLocation().getBlockX() + 128) : region.getMaximumPoint().x();
        int maxZ = region.getMaximumPoint().z() > (player.getLocation().z() + 128) ? (player.getLocation().getBlockZ() + 128) : region.getMaximumPoint().z();

        // Iterate in the bounding box.
        for (int i = minX; i <= maxX; i++) {
            for (int j = minZ; j <= maxZ; j++) {

                // Check if the point is contained in the region.
                // If the previous point was not contained, set the block to be an outline.
                if (region.contains(BlockVector2.at(i, j))) {

                    // Check if any of the surrounding blocks are not contained,
                    // if true then this block is an outline.
                    if (!(region.contains(BlockVector2.at(i - 1, j)) && region.contains(BlockVector2.at(i + 1, j)) && region.contains(BlockVector2.at(i, j - 1)) && region.contains(
                            BlockVector2.at(i, j + 1)))) {

                        BlockLocation bl = new BlockLocation(Material.AIR.createBlockData(), i, j);
                        locations.remove(bl);

                        drawBlock(bl);

                    }
                }
            }
        }
    }

    /**
     * Remove all outlines and set them to air.
     */
    public void removeOutlines() {
        for (BlockLocation loc : locations) {
            BlockLocation bl = new BlockLocation(Material.AIR.createBlockData(), loc.x(), loc.z());
            drawBlock(bl);
        }
        locations.clear();
    }

    // Add a point.
    public void addPoint(BlockVector2 bv, BlockData block) {

        // Add the point to the list.
        BlockLocation bl = new BlockLocation(block, bv.x(), bv.z());
        tempLocations.add(bl);

        // Draw the point.
        drawBlock(bl);

    }

    // Remove a point.
    public void removePoint(BlockVector2 bv) {

        // Remove the points from the list.
        BlockLocation bl = new BlockLocation(Material.AIR.createBlockData(), bv.x(), bv.z());
        tempLocations.remove(bl);

        // Set the block to air.
        drawBlock(bl);

    }

    // Add a line.
    public void addLine(BlockVector2 bv1, BlockVector2 bv2, BlockData block) {

        // Get length in x and z direction.
        int lengthX = bv2.x() - bv1.x();
        int lengthZ = bv2.z() - bv1.z();

        int length = max(abs(lengthX), abs(lengthZ));

        // Iterate over the largest length of the two.
        for (int i = 0; i <= length; i++) {

            // Remove the points from the list.
            BlockLocation bl = new BlockLocation(block,
                    ((int) (round(bv1.x() + ((i * lengthX) / (double) length)))),
                    ((int) (round(bv1.z() + ((i * lengthZ) / (double) length)))));
            tempLocations.add(bl);

            drawBlock(bl);

        }
    }

    // Remove a line.
    public void removeLine(BlockVector2 bv1, BlockVector2 bv2) {

        // Get length in x and z direction.
        int lengthX = bv2.x() - bv1.x();
        int lengthZ = bv2.z() - bv1.z();

        int length = max(abs(lengthX), abs(lengthZ));

        // Iterate over the largest length of the two.
        for (int i = 0; i <= length; i++) {

            // Remove the points from the list.
            BlockLocation bl = new BlockLocation(Material.AIR.createBlockData(),
                    ((int) (round(bv1.x() + ((i * lengthX) / (double) length)))),
                    ((int) (round(bv1.z() + ((i * lengthZ) / (double) length)))));
            tempLocations.remove(bl);

            drawBlock(bl);

        }
    }

    // Add all the block locations that make up the outline of the region.
    public void addTempOutline(ProtectedRegion region, BlockData block) {

        int minX = region.getMinimumPoint().x();
        int minZ = region.getMinimumPoint().z();

        int maxX = region.getMaximumPoint().x();
        int maxZ = region.getMaximumPoint().z();

        // Iterate in the bounding box.
        for (int i = minX; i <= maxX; i++) {
            for (int j = minZ; j <= maxZ; j++) {

                // Check if the point is contained in the region.
                // If the previous point was not contained, set the block to be an outline.
                if (region.contains(BlockVector2.at(i, j))) {

                    // Check if any of the surrounding blocks are not contained,
                    // if true then this block is an outline.
                    if (!(region.contains(BlockVector2.at(i - 1, j)) && region.contains(BlockVector2.at(i + 1, j)) && region.contains(BlockVector2.at(i, j - 1)) && region.contains(
                            BlockVector2.at(i, j + 1)))) {

                        BlockLocation bl = new BlockLocation(block, i, j);

                        tempLocations.add(bl);

                        drawBlock(bl);
                    }
                }
            }
        }
    }

    // Remove all the block locations that make up the outline of the region.
    // Additionally replace the fake block with air.
    public void removeTempOutline(ProtectedRegion region) {

        int minX = region.getMinimumPoint().x();
        int minZ = region.getMinimumPoint().z();

        int maxX = region.getMaximumPoint().x();
        int maxZ = region.getMaximumPoint().z();

        // Iterate in the bounding box.
        for (int i = minX; i <= maxX; i++) {
            for (int j = minZ; j <= maxZ; j++) {

                // Check if the point is contained in the region.
                // If the previous point was not contained, set the block to be an outline.
                if (region.contains(BlockVector2.at(i, j))) {

                    // Check if any of the surrounding blocks are not contained,
                    // if true then this block is an outline.
                    if (!(region.contains(BlockVector2.at(i - 1, j)) && region.contains(BlockVector2.at(i + 1, j)) && region.contains(BlockVector2.at(i, j - 1)) && region.contains(
                            BlockVector2.at(i, j + 1)))) {

                        BlockLocation bl = new BlockLocation(Material.AIR.createBlockData(), i, j);
                        tempLocations.remove(bl);

                        drawBlock(bl);

                    }
                }
            }
        }
    }

    // Remove all block locations from the list.
    // This would be used when the player moves a certain number of blocks.
    public void clear(boolean temp) {
        locations.clear();

        if (temp) {
            tempLocations.clear();
        }
    }

    // Add all the block locations that make up the line.

    // Remove all the block locations that make up the line.

    // Draw all blocks for a specific player.
    public void drawOutlines() {

        for (BlockLocation bl : locations) {

            drawBlock(bl);

        }

        for (BlockLocation bl : tempLocations) {

            drawBlock(bl);

        }
    }

    // Draw a specific block.
    private void drawBlock(BlockLocation bl) {
        player.sendBlockChange(
                new Location(
                        world, bl.x(),
                        (world.getHighestBlockYAt(bl.x(), bl.z()) + 1),
                        bl.z()
                ), bl.block());
    }
}
