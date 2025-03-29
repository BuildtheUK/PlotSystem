package net.bteuk.plotsystem.utils;

import net.bteuk.plotsystem.PlotSystem;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Map;
import java.util.Objects;

import static net.bteuk.network.utils.Constants.LOGGER;

public class Config {

    public static FileConfiguration CONFIG;

    public Config() {

        //Create a config instance.
        CONFIG = PlotSystem.getInstance().getConfig();

    }

    //Get old config version.
    private String configVersion() {

        String version = CONFIG.getString("version");
        //If null return default.
        return Objects.requireNonNullElse(version, "1.0.0");
    }

    //Get latest config version.
    private String latestVersion() {
        String version = Objects.requireNonNull(CONFIG.getDefaults()).getString("version");
        //If null return default.
        return Objects.requireNonNullElse(version, "1.7.0");
    }

    //Update config if the version is outdated.
    public void updateConfig() {

        String version = configVersion();

        if (!version.equals(latestVersion())) {
            LOGGER.info("Your config version is outdated, updating to latest version!");

            //Get old config values, these are needed to add them back after updating.
            Map<String, Object> values = CONFIG.getValues(true);

            //Generate a new config file from the default config.
            //Copy any values that can be reused.
            //Delete the current config and set the new one.
            File configFile = new File(PlotSystem.getInstance().getDataFolder(), "config.yml");

            if (!configFile.delete()) {

                //Something went wrong.
                LOGGER.warning("The old config file could not be deleted!");
                return;

            }

            //Copy the default config and get it.
            PlotSystem.getInstance().saveDefaultConfig();
            PlotSystem.getInstance().reloadConfig();
            CONFIG = PlotSystem.getInstance().getConfig();

            for (Map.Entry<String, Object> value : values.entrySet()) {

                if (CONFIG.contains(value.getKey())) {

                    //Check if this is a configuration section, if true skip.
                    if (CONFIG.isConfigurationSection(value.getKey())) {
                        continue;
                    }

                    //Skip the version since that needs to be the latest value.
                    if (value.getKey().equals("version")) {
                        continue;
                    }
                    CONFIG.set(value.getKey(), value.getValue());

                }
            }

            PlotSystem.getInstance().saveConfig();

            CONFIG = PlotSystem.getInstance().getConfig();

            LOGGER.info("Updated config to version " + CONFIG.getString("version"));

        } else {
            LOGGER.info("The config is up to date!");
        }
    }
}
