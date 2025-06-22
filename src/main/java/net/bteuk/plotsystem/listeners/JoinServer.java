package net.bteuk.plotsystem.listeners;

import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.events.EventManager;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/*
This class will be a global class, used for all server types.
It will create the initial user class with basic information, such as uuid, name, player.
Additionally, the tutorial data will be loaded to check whether the player needs to complete the tutorial first.
If this server does not have a tutorial, but it has not been completed, then the player will be sent to
an alternative server which does have a tutorial.
 */
public class JoinServer implements Listener {

    private final GlobalSQL globalSQL;
    private final PlotSQL plotSQL;
    private final PlotSystem instance;

    public JoinServer(PlotSystem plugin, GlobalSQL globalSQL, PlotSQL plotSQL) {

        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        this.instance = plugin;
        this.globalSQL = globalSQL;
        this.plotSQL = plotSQL;

    }

    // Must run last.
    @EventHandler(priority = EventPriority.HIGH)
    public void joinEvent(PlayerJoinEvent e) {

        // Create instance of User and add it to list.
        User u = new User(e.getPlayer(), globalSQL, plotSQL);
        instance.addUser(u);

        // Add the player to relevant holograms.
        PlotHelper.addPlayer(e.getPlayer());

        // If the player has a join event, execute it.
        // Delay by 1 second for all plugins to run their join events.
        Bukkit.getScheduler().scheduleSyncDelayedTask(instance, () -> {
            if (globalSQL.hasRow("SELECT uuid FROM join_events WHERE uuid='" + u.player.getUniqueId() + "' AND type='plotsystem';")) {

                // Get the event from the database.
                String event = globalSQL.getString("SELECT event FROM join_events WHERE uuid='" + u.player.getUniqueId() + "' AND type='plotsystem'");

                // Split the event by word.
                String[] aEvent = event.split(" ");

                // Send the event to the event handler.
                EventManager.event(u.uuid, aEvent);

                // Clear the events.
                globalSQL.update("DELETE FROM join_events WHERE uuid='" + u.player.getUniqueId() + "' AND type='plotsystem';");

            }
        }, 20L);

    }
}
