package net.bteuk.plotsystem.listeners;

import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class CloseInventory implements Listener {

    public CloseInventory(PlotSystem instance) {

        Bukkit.getServer().getPluginManager().registerEvents(this, instance);

    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {

        Player p = (Player) e.getPlayer();
        User u = PlotSystem.getInstance().getUser(p);

        if (u == null) {
            return;
        }

        // If the player has a claim or create gui delete it.
        if (u.claimGui != null) {
            u.claimGui.delete();
            u.claimGui = null;
        }

        if (u.createPlotGui != null) {
            u.createPlotGui.delete();
            u.createPlotGui = null;
        }

    }

}
