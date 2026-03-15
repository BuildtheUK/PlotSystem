package net.bteuk.plotsystem.listeners;

import net.bteuk.network.api.NetworkAPI;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
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
    private final PlotSystem instance;
    private final NetworkAPI networkAPI;
    private final PlotHelper plotHelper;

    public JoinServer(PlotSystem plugin, NetworkAPI networkAPI, PlotHelper plotHelper) {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        this.instance = plugin;
        this.networkAPI = networkAPI;
        this.plotHelper = plotHelper;
    }

    @EventHandler
    public void joinEvent(PlayerJoinEvent e) {
        // Create instance of User and add it to list.
        User u = new User(e.getPlayer(), networkAPI, plotHelper);
        instance.addUser(u);

        // Add the player to relevant holograms.
        plotHelper.addPlayer(e.getPlayer());
    }
}
