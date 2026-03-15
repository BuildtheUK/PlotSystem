package net.bteuk.plotsystem.utils;

import net.bteuk.plotsystem.PlotSystem;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class PlotValues {

    // Returns the plot difficulty name.
    public static String difficultyName(int difficulty) {

        return switch (difficulty) {
            case 1 -> "Easy";
            case 2 -> "Normal";
            case 3 -> "Hard";
            default -> throw new IllegalArgumentException("Difficulty must be 1, 2 or 3!");
        };
    }

    // Returns the plot size name.
    public static String sizeName(int size) {

        return switch (size) {
            case 1 -> "Small";
            case 2 -> "Medium";
            case 3 -> "Large";
            default -> throw new IllegalArgumentException("Size must be 1, 2 or 3!");
        };
    }

    // Returns the plot size material.
    public static Material sizeMaterial(int size) {

        return switch (size) {
            case 1 -> Material.LIME_CONCRETE;
            case 2 -> Material.YELLOW_CONCRETE;
            case 3 -> Material.RED_CONCRETE;
            default -> throw new IllegalArgumentException("Size must be 1, 2 or 3!");
        };
    }

    // Returns the plot difficulty material.
    public static Material difficultyMaterial(int difficulty) {

        return switch (difficulty) {
            case 1 -> Material.LIME_CONCRETE;
            case 2 -> Material.YELLOW_CONCRETE;
            case 3 -> Material.RED_CONCRETE;
            default -> throw new IllegalArgumentException("Difficulty must be 1, 2 or 3!");
        };
    }

    public static int sizeValue(int size) {

        FileConfiguration config = PlotSystem.getInstance().getConfig();

        switch (size) {

            case 1:

                return config.getInt("size.small");

            case 2:

                return config.getInt("size.medium");

            case 3:

                return config.getInt("size.large");

        }

        LOGGER.warning("Plot size was not in the range of possible values!");
        return 0;

    }

    public static int difficultyValue(int difficulty) {

        FileConfiguration config = PlotSystem.getInstance().getConfig();

        switch (difficulty) {

            case 1:

                return config.getInt("difficulty.easy");

            case 2:

                return config.getInt("difficulty.normal");

            case 3:

                return config.getInt("difficulty.hard");

        }

        LOGGER.warning("Plot difficulty was not in the range of possible values!");
        return 0;

    }
}
