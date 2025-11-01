package net.bteuk.plotsystem;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import lombok.Getter;
import net.bteuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.commands.ClaimCommand;
import net.bteuk.plotsystem.commands.LocationCommand;
import net.bteuk.plotsystem.commands.PlotSystemCommand;
import net.bteuk.plotsystem.commands.ReviewCommand;
import net.bteuk.plotsystem.commands.ToggleOutlines;
import net.bteuk.plotsystem.events.ClaimEvent;
import net.bteuk.plotsystem.events.CloseEvent;
import net.bteuk.plotsystem.listeners.ClaimEnter;
import net.bteuk.plotsystem.listeners.CloseInventory;
import net.bteuk.plotsystem.listeners.HologramClickEvent;
import net.bteuk.plotsystem.listeners.JoinServer;
import net.bteuk.plotsystem.listeners.PlayerInteract;
import net.bteuk.plotsystem.listeners.QuitServer;
import net.bteuk.plotsystem.utils.Config;
import net.bteuk.plotsystem.utils.Outlines;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.PlotHologram;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.Multiverse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static net.bteuk.plotsystem.utils.Config.CONFIG;

public class PlotSystem extends JavaPlugin {

    // Logger
    public static Logger LOGGER;
    // Items
    public static ItemStack selectionTool;
    public static ItemStack gui;
    // Server Name
    public static String SERVER_NAME;
    // Returns an instance of the plugin.
    @Getter
    static PlotSystem instance;
    public Timers timers;
    // Listeners
    public ClaimEnter claimEnter;
    // Returns the User ArrayList.
    @Getter
    private ArrayList<User> users;
    // Outline manager.
    @Getter
    private Outlines outlines;

    @Getter
    private boolean isClosing = false;

    private GuiManager guiManager;

    private PlotHelper plotHelper;;

    @Override
    public void onEnable() {

        LOGGER = getLogger();

        // Config Setup
        PlotSystem.instance = this;

        // Sets the config if the file has not yet been created.
        ConfigurationSerialization.registerClass(ConfigurationSerializable.class);
        saveDefaultConfig();

        // Update the config to the latest version if it's outdated.
        // It will copy over any keys that remain the same.
        // This will also set the status variable to access the config project-wide.
        Config config = new Config();
        config.updateConfig();

        if (!CONFIG.getBoolean("enabled")) {

            LOGGER.warning("The config must be configured before the plugin can be enabled!");
            LOGGER.warning("Please edit the database values in the config, give the server a unique name and then set 'enabled: true'");
            return;

        }

        // Set the server name from config.
        SERVER_NAME = CONFIG.getString("server_name");

        // Add save world if it does not yet exist.
        // Save world name is in config.
        // This implies first launch with plugin.
        if (!Multiverse.hasWorld(CONFIG.getString("save_world"))) {
            // Create save world.
            if (!Multiverse.createVoidWorld(CONFIG.getString("save_world"))) {

                LOGGER.warning("Failed to create save world!");

            }
        }

        LOGGER.info("Enabling Plugin");
        enablePlugin();
    }

    // Server enabling procedure when the config has been set up.
    public void enablePlugin() {

        // Get the NetworkAPI.
        RegisteredServiceProvider<NetworkAPI> networkProvider = Bukkit.getServicesManager().getRegistration(NetworkAPI.class);
        if (networkProvider == null) {
            LOGGER.severe("Failed to get NetworkAPI, disabling plugin!");
            return;
        }
        final NetworkAPI networkAPI = networkProvider.getProvider();

        this.guiManager = new GuiManager();
        this.plotHelper = new PlotHelper();

        // Register hologram click event.
        new HologramClickEvent(this);

        // General Setup
        // Create list of users.
        users = new ArrayList<>();

        networkAPI.getPlotAPI().resetPlotSubmissions(SERVER_NAME);

        // Create gui item
        gui = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta2 = gui.getItemMeta();
        meta2.displayName(ChatUtils.title("Building Menu"));
        gui.setItemMeta(meta2);

        // Outlines, this will be accessed from other classes, so it must have a getter and setter.
        outlines = new Outlines();

        // Setup Timers
        timers = new Timers(this, networkAPI);

        // Create bungeecord channel
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Create selection tool item
        selectionTool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = selectionTool.getItemMeta();
        meta.displayName(ChatUtils.success("Selection Tool"));
        selectionTool.setItemMeta(meta);

        // Listeners
        new JoinServer(this, plotHelper);
        new QuitServer(this);
        new PlayerInteract(instance, networkAPI.getPlotAPI());
        new CloseInventory(this);

        // Events
        networkAPI.getEventAPI().registerEvent("claim", new ClaimEvent(networkAPI, guiManager));
        networkAPI.getEventAPI().registerEvent("close", new CloseEvent(networkAPI.getPlotAPI(), networkAPI.getChat()));

        // Deals with tracking where players are in relation to plots.
        claimEnter = new ClaimEnter(this, networkAPI.getPlotAPI(), networkAPI.getGlobalSQL());

        // Commands
        LifecycleEventManager<@NotNull Plugin> manager = instance.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register("plotsystem", "Deals will all plotsystem related commands.", List.of("ps"), new PlotSystemCommand(networkAPI.getPlotAPI(), plotHelper, networkAPI.getCoordinateAPI(), guiManager, new LocationCommand(networkAPI)));
            commands.register("claim", "Used to claim the plot you're standing in.", List.of("claim"), new ClaimCommand(networkAPI, guiManager, plotHelper));
            commands.register("toggleoutlines", "Toggles the visibility of outlines.", new ToggleOutlines(this));
            commands.register("review", "Command for editing selections to reviewing categories during the reviewing process.", new ReviewCommand(this));

        });

        // Get all active plots (unclaimed, claimed, submitted, reviewing) and add holograms.
        List<Integer> active_plots =  networkAPI.getPlotAPI().getActivePlots(SERVER_NAME);
        active_plots.forEach(plot -> plotHelper.addPlotHologram(new PlotHologram(plot)));
    }

    public void onDisable() {

        this.isClosing = true;

        // Remove all players who are in review.
        for (User user : users) {

            // If the player is in a review, cancel it.
            if (user.getReview() != null) {
                user.getReview().cancel();
            }
        }

        // Disable bungeecord channel.
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this);

        LOGGER.info("Disabled PlotSystem");
    }

    // Returns the specific user based on Player instance.
    public User getUser(Player p) {
        for (User u : users) {
            if (u.player.equals(p)) {
                return u;
            }
        }
        return null;
    }

    // Add user to list.
    public void addUser(User u) {
        users.add(u);
    }

    // Get user from player.
    public void removeUser(User u) {
        users.remove(u);
    }
}