package net.bteuk.plotsystem.utils.plugins;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.mvplugins.multiverse.core.MultiverseCore;
import org.mvplugins.multiverse.core.world.MultiverseWorld;

import java.util.HashMap;
import java.util.Map;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class Multiverse {

    public static boolean createVoidWorld(String name) {

        MultiverseCore core = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");

        if (core == null) {
            LOGGER.severe("Multiverse is a dependency of PlotSystem!");
            return false;
        }

        MVWorldManager worldManager = core.getMVWorldManager();

        worldManager.addWorld(
                name,
                World.Environment.NORMAL,
                null,
                WorldType.FLAT,
                false,
                "VoidGen:{biome:PLAINS}"
        );

        MultiverseWorld MVWorld = worldManager.getMVWorld(name);
        MVWorld.setGameMode(GameMode.CREATIVE);
        MVWorld.setAllowAnimalSpawn(false);
        MVWorld.setAllowMonsterSpawn(false);
        MVWorld.setDifficulty(Difficulty.PEACEFUL);
        MVWorld.setEnableWeather(false);
        MVWorld.setHunger(false);
        MVWorld.setKeepSpawnInMemory(false);

        // Get world from bukkit.
        World world = Bukkit.getWorld(name);

        if (world == null) {
            LOGGER.warning("World is null!");
            return false;
        }

        // Disable daylightcycle.
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(6000);

        // Disable fire tick.
        world.setGameRule(GameRule.DO_FIRE_TICK, false);

        // Disable random tick.
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);

        // Disable wandering traders.
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);

        // Get worldguard.
        WorldGuard wg = WorldGuard.getInstance();
        RegionManager regions = wg.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));

        if (regions == null) {
            LOGGER.warning("Regions is null!");
            return false;
        }

        // Create global region and add all necessary flags.
        GlobalProtectedRegion globalRegion = new GlobalProtectedRegion("__global__");

        Map<Flag<?>, Object> flags = new HashMap<>();
        flags.put(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
        flags.put(Flags.WATER_FLOW, StateFlag.State.DENY);
        flags.put(Flags.LEAF_DECAY, StateFlag.State.DENY);
        flags.put(Flags.CORAL_FADE, StateFlag.State.DENY);
        flags.put(Flags.LIGHTNING, StateFlag.State.DENY);
        flags.put(Flags.SNOW_MELT, StateFlag.State.DENY);
        flags.put(Flags.FROSTED_ICE_FORM, StateFlag.State.DENY);
        flags.put(Flags.ICE_MELT, StateFlag.State.DENY);
        flags.put(Flags.TRAMPLE_BLOCKS, StateFlag.State.DENY);
        flags.put(Flags.FIRE_SPREAD, StateFlag.State.DENY);
        flags.put(Flags.PISTONS, StateFlag.State.DENY);
        flags.put(Flags.SOIL_DRY, StateFlag.State.DENY);
        flags.put(Flags.LAVA_FLOW, StateFlag.State.DENY);
        flags.put(Flags.GRASS_SPREAD, StateFlag.State.DENY);
        flags.put(Flags.LAVA_FIRE, StateFlag.State.DENY);
        flags.put(Flags.SNOW_FALL, StateFlag.State.DENY);
        flags.put(Flags.PASSTHROUGH, StateFlag.State.DENY);
        flags.put(Flags.ICE_FORM, StateFlag.State.DENY);
        flags.put(Flags.GHAST_FIREBALL, StateFlag.State.DENY);
        flags.put(Flags.FROSTED_ICE_MELT, StateFlag.State.DENY);
        flags.put(Flags.CHEST_ACCESS, StateFlag.State.DENY);
        flags.put(Flags.ENDERDRAGON_BLOCK_DAMAGE, StateFlag.State.DENY);
        flags.put(Flags.ENDER_BUILD, StateFlag.State.DENY);
        flags.put(Flags.ENTITY_ITEM_FRAME_DESTROY, StateFlag.State.DENY);
        flags.put(Flags.ENTITY_PAINTING_DESTROY, StateFlag.State.DENY);
        flags.put(Flags.PLACE_VEHICLE, StateFlag.State.DENY);
        flags.put(Flags.POTION_SPLASH, StateFlag.State.DENY);
        flags.put(Flags.RIDE, StateFlag.State.DENY);

        globalRegion.setFlags(flags);

        regions.addRegion(globalRegion);

        try {
            regions.saveChanges();
        } catch (StorageException e) {
            e.printStackTrace();
        }

        LOGGER.info("Created new world with name " + name);

        return true;
    }

    public static boolean hasWorld(String name) {

        MultiverseCore core = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");

        if (core == null) {
            LOGGER.severe("Multiverse is a dependency of PlotSystem!");
            return false;
        }

        // If the world exists return true.

        MVWorldManager worldManager = core.getMVWorldManager();

        MultiverseWorld world = worldManager.getMVWorld(name);

        return world != null;
    }

    public static boolean deleteWorld(String name) {

        MultiverseCore core = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");

        if (core == null) {
            LOGGER.severe("Multiverse is a dependency of PlotSystem!");
            return false;
        }

        // If world exists delete it.
        MVWorldManager worldManager = core.getMVWorldManager();

        MultiverseWorld world = worldManager.getMVWorld(name);

        if (world == null) {
            return false;
        } else {
            worldManager.deleteWorld(name);
            return true;
        }

    }
}
