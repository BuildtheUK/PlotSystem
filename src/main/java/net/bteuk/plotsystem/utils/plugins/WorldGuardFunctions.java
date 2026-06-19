package net.bteuk.plotsystem.utils.plugins;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.exceptions.WorldNotFoundException;
import net.bteuk.plotsystem.utils.Utils;
import net.bteuk.plotsystem.utils.math.Point;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorldGuardFunctions {

    private static RegionManager getRegionManager(World world) throws RegionManagerNotFoundException {
        // Get worldguard instance
        WorldGuard wg = WorldGuard.getInstance();

        // Get worldguard region data
        RegionContainer container = wg.getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));

        if (regionManager == null) {
            throw new RegionManagerNotFoundException("RegionManager for world " + world.getName() + " is null!");
        }

        return regionManager;
    }

    public static Location getCurrentLocation(String regionName, World world) throws RegionNotFoundException, RegionManagerNotFoundException {

        RegionManager buildRegions = getRegionManager(world);

        // Get the worldguard region and teleport to player to one of the corners.
        ProtectedPolygonalRegion region = (ProtectedPolygonalRegion) buildRegions.getRegion(regionName);

        if (region == null) {

            throw new RegionNotFoundException("Region " + regionName + " does not exist!");

        }

        BlockVector2 bv = Point.getAveragePoint(region.getPoints());

        return (new Location(world, bv.x(), Utils.getHighestYAt(world, bv.x(), bv.z()), bv.z()));

    }

    public static Location getBeforeLocation(String regionName, World buildWorld, PlotAPI plotAPI) throws WorldNotFoundException, RegionNotFoundException, RegionManagerNotFoundException {

        // Get instance of plugin and config
        PlotSystem instance = PlotSystem.getInstance();
        FileConfiguration config = instance.getConfig();

        // Get worlds from config
        String saveWorldName = config.getString("save_world");
        if (saveWorldName == null) {

            throw new WorldNotFoundException("Save World is not defined in config, plot delete event has therefore failed!");

        }

        World saveWorld = Bukkit.getServer().getWorld(saveWorldName);

        // Get worldguard instance
        WorldGuard wg = WorldGuard.getInstance();

        // Get worldguard region data
        RegionContainer container = wg.getPlatform().getRegionContainer();
        RegionManager buildRegions = container.get(BukkitAdapter.adapt(buildWorld));

        if (buildRegions == null) {

            throw new RegionManagerNotFoundException("RegionManager for world " + buildWorld.getName() + " is null!");

        }

        ProtectedPolygonalRegion region = (ProtectedPolygonalRegion) buildRegions.getRegion(regionName);

        if (region == null) {

            throw new RegionNotFoundException("Region " + regionName + " does not exist!");

        }

        BlockVector2 bv = Point.getAveragePoint(region.getPoints());

        // To get the actual location we need to take the negative coordinate transform of the plot.
        int xTransform = -plotAPI.getXTransform(buildWorld.getName());
        int zTransform = -plotAPI.getZTransform(buildWorld.getName());

        BlockVector2 bv2 = BlockVector2.at(bv.x() + xTransform, bv.z() + zTransform);

        return (new Location(saveWorld, bv2.x(), Utils.getHighestYAt(saveWorld, bv2.x(), bv2.z()), bv2.z()));

    }

    public static List<BlockVector2> getPoints(String regionName, World world) throws RegionNotFoundException, RegionManagerNotFoundException {

        // Get worldguard instance
        WorldGuard wg = WorldGuard.getInstance();

        // Get worldguard region data
        RegionContainer container = wg.getPlatform().getRegionContainer();
        RegionManager buildRegions = container.get(BukkitAdapter.adapt(world));

        if (buildRegions == null) {

            throw new RegionManagerNotFoundException("RegionManager for world " + world.getName() + " is null!");

        }

        ProtectedPolygonalRegion region = (ProtectedPolygonalRegion) buildRegions.getRegion(regionName);

        if (region == null) {

            throw new RegionNotFoundException("Region " + regionName + " does not exist!");

        }

        return region.getPoints();

    }

    public static boolean inRegion(Block block) throws RegionManagerNotFoundException {

        // Get worldguard instance
        WorldGuard wg = WorldGuard.getInstance();

        // Get worldguard region data
        RegionManager regions = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(block.getWorld()));

        if (regions == null) {

            throw new RegionManagerNotFoundException("RegionManager for world " + block.getWorld().getName() + " is null!");

        }

        // Get the blockvector3 at the block.
        BlockVector3 v = BlockVector3.at(block.getX(), block.getY(), block.getZ());

        // Check whether the region overlaps an existing plot, if true stop the process.
        ApplicableRegionSet set = regions.getApplicableRegions(v);

        return set.size() > 0;
    }

    public static boolean addMember(String regionName, String uuid, World world) throws RegionManagerNotFoundException, RegionNotFoundException {

        // Get instance of WorldGuard.
        WorldGuard wg = WorldGuard.getInstance();

        // Get regions.
        RegionContainer container = wg.getPlatform().getRegionContainer();
        RegionManager buildRegions = container.get(BukkitAdapter.adapt(world));

        if (buildRegions == null) {

            throw new RegionManagerNotFoundException("RegionManager for world " + world.getName() + " is null!");

        }

        ProtectedRegion region = buildRegions.getRegion(regionName);

        if (region == null) {

            throw new RegionNotFoundException("Region " + regionName + " does not exist!");

        }

        // Add the member to the region.
        region.getMembers().addPlayer(UUID.fromString(uuid));

        // Save the changes
        try {
            buildRegions.saveChanges();
            return true;
        } catch (StorageException e1) {
            e1.printStackTrace();
            return false;
        }
    }

    public static void removeMember(String regionName, String uuid, World world) throws RegionManagerNotFoundException, RegionNotFoundException {

        // Get instance of WorldGuard.
        WorldGuard wg = WorldGuard.getInstance();

        // Get regions.
        RegionContainer container = wg.getPlatform().getRegionContainer();
        RegionManager buildRegions = container.get(BukkitAdapter.adapt(world));

        if (buildRegions == null) {

            throw new RegionManagerNotFoundException("RegionManager for world " + world.getName() + " is null!");

        }

        // Check if the member is in the region.
        ProtectedRegion region = buildRegions.getRegion(regionName);

        if (region == null) {

            throw new RegionNotFoundException("Region " + regionName + " does not exist!");

        }

        if (region.getMembers().contains(UUID.fromString(uuid))) {
            // Remove the member to the region.
            region.getMembers().removePlayer(UUID.fromString(uuid));
        } else {
            return;
        }

        // Save the changes
        try {
            buildRegions.saveChanges();
        } catch (StorageException e1) {
            e1.printStackTrace();
        }
    }

    public static void clearMembers(String regionName, World world) throws RegionNotFoundException, RegionManagerNotFoundException {

        // Get instance of WorldGuard.
        WorldGuard wg = WorldGuard.getInstance();

        // Get regions.
        RegionContainer container = wg.getPlatform().getRegionContainer();
        RegionManager buildRegions = container.get(BukkitAdapter.adapt(world));

        if (buildRegions == null) {

            throw new RegionManagerNotFoundException("RegionManager for world " + world.getName() + " is null!");

        }

        ProtectedRegion region = buildRegions.getRegion(regionName);

        if (region == null) {

            throw new RegionNotFoundException("Region " + regionName + " does not exist!");

        }

        // Remove all members from the region.
        region.getMembers().clear();

        // Save the changes
        try {
            buildRegions.saveChanges();
        } catch (StorageException e1) {
            e1.printStackTrace();
        }
    }

    public static boolean delete(String regionName, World world) throws RegionManagerNotFoundException {

        // Get instance of WorldGuard.
        WorldGuard wg = WorldGuard.getInstance();

        // Get regions.
        RegionContainer container = wg.getPlatform().getRegionContainer();
        RegionManager buildRegions = container.get(BukkitAdapter.adapt(world));

        if (buildRegions == null) {

            throw new RegionManagerNotFoundException("RegionManager for world " + world.getName() + " is null!");

        }

        // Get the region to remove the outlines.
        ProtectedRegion region = buildRegions.getRegion(regionName);

        if (region != null) {
            PlotSystem.getInstance().getOutlines().removeOutline(region, world);
        }

        // Attempt to remove the plot.
        buildRegions.removeRegion(regionName);

        // Save the changes
        try {
            buildRegions.saveChanges();
            return true;
        } catch (StorageException e1) {
            e1.printStackTrace();
            return false;
        }
    }

    /**
     * Get the points of a specific plot or zone as if it was located in the save world.
     * This is done by getting the points in the world where the plot or zone is and then applying the negative transform from its original location.
     *
     * @param regionName the name of the plot or zone
     * @param world      the name of the world where the plot or zone exists, NOT the world of the save world
     */
    public static List<BlockVector2> getPointsTransformedToSaveWorld(String regionName, World world, PlotAPI plotAPI) throws RegionNotFoundException, RegionManagerNotFoundException {

        List<BlockVector2> vector = getPoints(regionName, world);
        List<BlockVector2> newVector = new ArrayList<>();

        // Get the negative coordinate transform.
        int xTransform = -plotAPI.getXTransform(world.getName());
        int zTransform = -plotAPI.getZTransform(world.getName());

        // Apply to transform to each coordinate.
        vector.forEach(bv -> newVector.add(BlockVector2.at(bv.x() + xTransform, bv.z() + zTransform)));

        return newVector;

    }

    /**
     * Sets the global flags as intended for a world.
     * @param world the world to set the flags for.
     */
    public static void setWorldFlags(World world) throws RegionManagerNotFoundException {

        RegionManager regionManager = getRegionManager(world);

        // Create the global region and add all necessary flags.
        ProtectedRegion region = regionManager.getRegion("__global__");

        GlobalProtectedRegion globalRegion;
        if (region instanceof GlobalProtectedRegion) {
            globalRegion = (GlobalProtectedRegion) region;
        } else {
            globalRegion = new GlobalProtectedRegion("__global__");
        }

        Map<Flag<?>, Object> flags = new HashMap<>();

        // Destruction
        flags.put(Flags.PASSTHROUGH, StateFlag.State.DENY);
        flags.put(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
        flags.put(Flags.LIGHTNING, StateFlag.State.DENY);
        flags.put(Flags.TRAMPLE_BLOCKS, StateFlag.State.DENY);
        flags.put(Flags.GHAST_FIREBALL, StateFlag.State.DENY);
        flags.put(Flags.ENDERDRAGON_BLOCK_DAMAGE, StateFlag.State.DENY);
        flags.put(Flags.ENDER_BUILD, StateFlag.State.DENY);
        flags.put(Flags.ENTITY_ITEM_FRAME_DESTROY, StateFlag.State.DENY);
        flags.put(Flags.ENTITY_PAINTING_DESTROY, StateFlag.State.DENY);
        flags.put(Flags.TNT, StateFlag.State.DENY);
        flags.put(Flags.WIND_CHARGE_BURST, StateFlag.State.DENY);
        flags.put(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
        flags.put(Flags.BREEZE_WIND_CHARGE, StateFlag.State.DENY);
        flags.put(Flags.WITHER_DAMAGE, StateFlag.State.DENY);
        flags.put(Flags.RAVAGER_RAVAGE, StateFlag.State.DENY);

        // Fluids
        flags.put(Flags.WATER_FLOW, StateFlag.State.DENY);
        flags.put(Flags.LAVA_FLOW, StateFlag.State.DENY);

        // Block updates.
        flags.put(Flags.LEAF_DECAY, StateFlag.State.DENY);
        flags.put(Flags.CORAL_FADE, StateFlag.State.DENY);
        flags.put(Flags.SNOW_MELT, StateFlag.State.DENY);
        flags.put(Flags.FROSTED_ICE_FORM, StateFlag.State.DENY);
        flags.put(Flags.ICE_MELT, StateFlag.State.DENY);
        flags.put(Flags.FIRE_SPREAD, StateFlag.State.DENY);
        flags.put(Flags.PISTONS, StateFlag.State.DENY);
        flags.put(Flags.SOIL_DRY, StateFlag.State.DENY);
        flags.put(Flags.GRASS_SPREAD, StateFlag.State.DENY);
        flags.put(Flags.LAVA_FIRE, StateFlag.State.DENY);
        flags.put(Flags.SNOW_FALL, StateFlag.State.DENY);
        flags.put(Flags.ICE_FORM, StateFlag.State.DENY);
        flags.put(Flags.FROSTED_ICE_MELT, StateFlag.State.DENY);
        flags.put(Flags.SNOWMAN_TRAILS, StateFlag.State.DENY);
        flags.put(Flags.MUSHROOMS, StateFlag.State.DENY);
        flags.put(Flags.VINE_GROWTH, StateFlag.State.DENY);
        flags.put(Flags.MYCELIUM_SPREAD, StateFlag.State.DENY);
        flags.put(Flags.ROCK_GROWTH, StateFlag.State.DENY);
        flags.put(Flags.SCULK_GROWTH, StateFlag.State.DENY);
        flags.put(Flags.CROP_GROWTH, StateFlag.State.DENY);
        flags.put(Flags.COPPER_FADE, StateFlag.State.DENY);
        flags.put(Flags.MOISTURE_CHANGE, StateFlag.State.DENY);

        // Access
        flags.put(Flags.PLACE_VEHICLE, StateFlag.State.DENY);
        flags.put(Flags.POTION_SPLASH, StateFlag.State.DENY);
        flags.put(Flags.RIDE, StateFlag.State.DENY);
        flags.put(Flags.MOB_SPAWNING, StateFlag.State.DENY);
        flags.put(Flags.RESPAWN_ANCHORS, StateFlag.State.DENY);
        flags.put(Flags.FIREWORK_DAMAGE, StateFlag.State.DENY);
        flags.put(Flags.ITEM_DROP, StateFlag.State.DENY);

        globalRegion.setFlags(flags);

        regionManager.addRegion(globalRegion);

        try {
            regionManager.saveChanges();
        } catch (StorageException e) {
            throw new RuntimeException("Failed to save changes to region manager!", e);
        }
    }
}
