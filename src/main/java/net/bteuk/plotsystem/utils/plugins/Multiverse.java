package net.bteuk.plotsystem.utils.plugins;

import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.RemoveWorldOptions;

import java.util.concurrent.atomic.AtomicBoolean;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class Multiverse {

    public static boolean createVoidWorld(String name) {

        MultiverseCoreApi core = MultiverseCoreApi.get();

        if (core == null) {
            LOGGER.severe("Multiverse is a dependency of PlotSystem!");
            return false;
        }

        WorldManager worldManager = core.getWorldManager();

        worldManager.createWorld(
                        CreateWorldOptions.worldName(name).environment(World.Environment.NORMAL).worldType(WorldType.FLAT).generateStructures(false).generator("VoidGen:{biome" +
                                ":PLAINS}"))
                .onSuccess(world -> {
                    world.setGameMode(GameMode.CREATIVE);
                    world.setDifficulty(Difficulty.PEACEFUL);
                    world.setAllowWeather(false);
                    world.setHunger(false);
                    world.setKeepSpawnInMemory(false);
                });


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

        // Disable spawning.
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);

        try {
            WorldGuardFunctions.setWorldFlags(world);
        } catch (RegionManagerNotFoundException e) {
            LOGGER.severe("Failed to set world flags: " + e.getMessage());
        }

        LOGGER.info("Created new world with name " + name);

        return true;
    }

    public static boolean hasWorld(String name) {

        MultiverseCoreApi core = MultiverseCoreApi.get();

        if (core == null) {
            LOGGER.severe("Multiverse is a dependency of PlotSystem!");
            return false;
        }

        WorldManager worldManager = core.getWorldManager();

        return worldManager.getWorld(name).isDefined();
    }

    public static boolean deleteWorld(String name) {

        MultiverseCoreApi core = MultiverseCoreApi.get();

        if (core == null) {
            LOGGER.severe("Multiverse is a dependency of PlotSystem!");
            return false;
        }

        WorldManager worldManager = core.getWorldManager();

        AtomicBoolean success = new AtomicBoolean(false);

        worldManager.getWorld(name).peek(
                world -> success.set(worldManager.removeWorld(RemoveWorldOptions.world(world)).isSuccess())
        );
        return success.get();
    }
}
