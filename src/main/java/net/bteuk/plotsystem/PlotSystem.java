package net.bteuk.plotsystem;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import lombok.Getter;
import net.bteuk.network.Network;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.commands.ClaimCommand;
import net.bteuk.plotsystem.commands.PlotSystemCommand;
import net.bteuk.plotsystem.commands.ReviewCommand;
import net.bteuk.plotsystem.commands.ToggleOutlines;
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
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

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
    // SQL Classes.
    public GlobalSQL globalSQL;
    public PlotSQL plotSQL;
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

        // Set databases from Network dependency.
        globalSQL = Network.getInstance().getGlobalSQL();
        plotSQL = Network.getInstance().getPlotSQL();

        // Set the server name from config.
        SERVER_NAME = CONFIG.getString("server_name");

        // If the server is in the database.
        if (globalSQL.hasRow("SELECT name FROM server_data WHERE name='" + SERVER_NAME + "';")) {

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

        } else {

            // If the server is not in the database the network plugin was not successful.
            LOGGER.severe("Server is not in database, check that the Network plugin is working correctly.");

        }
    }

    // Server enabling procedure when the config has been set up.
    public void enablePlugin() {

        // Register hologram click event.
        new HologramClickEvent(this);

        // Initialise the plot helper.
        PlotHelper.init(plotSQL);

        // General Setup
        // Create list of users.
        users = new ArrayList<>();

        // TODO: Ensure no plots on this server are under review or under verification.
        // plotSQL.update(
        //         "UPDATE plot_data AS pd INNER JOIN location_data AS ld ON ld.name=pd.location SET pd.status='submitted' WHERE pd.status='reviewing' AND ld.server='" + SERVER_NAME + "';");

        // Create gui item
        gui = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta2 = gui.getItemMeta();
        meta2.displayName(ChatUtils.title("Building Menu"));
        gui.setItemMeta(meta2);

        // Outlines, this will be accessed from other classes, so it must have a getter and setter.
        outlines = new Outlines();

        // Setup Timers
        timers = new Timers(this, globalSQL);
        timers.startTimers();

        // Create bungeecord channel
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Create selection tool item
        selectionTool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = selectionTool.getItemMeta();
        meta.displayName(ChatUtils.success("Selection Tool"));
        selectionTool.setItemMeta(meta);

        // Listeners
        new JoinServer(this, globalSQL, plotSQL);
        new QuitServer(this);
        new PlayerInteract(instance, plotSQL);
        new CloseInventory(this);

        // Deals with tracking where players are in relation to plots.
        claimEnter = new ClaimEnter(this, plotSQL, globalSQL);

        // Commands
        LifecycleEventManager<Plugin> manager = instance.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register("plotsystem", "Deals will all plotsystem related commands.", List.of("ps"), new PlotSystemCommand(globalSQL, plotSQL));
            commands.register("claim", "Used to claim the plot you're standing in.", List.of("claim"), new ClaimCommand(plotSQL));
            commands.register("toggleoutlines", "Toggles the visibility of outlines.", new ToggleOutlines(this));
            commands.register("review", "Command for editing selections to reviewing categories during the reviewing process.", new ReviewCommand(this));

        });

        // Get all active plots (unclaimed, claimed, submitted, reviewing) and add holograms.
        List<Integer> active_plots = plotSQL.getIntList(
                "SELECT pd.id FROM plot_data AS pd INNER JOIN location_data AS ld ON ld.name=pd.location WHERE pd.status IN ('unclaimed','claimed','submitted') AND " +
                        "ld.server='" + SERVER_NAME + "';");
        active_plots.forEach(plot -> PlotHelper.addPlotHologram(new PlotHologram(plot)));
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