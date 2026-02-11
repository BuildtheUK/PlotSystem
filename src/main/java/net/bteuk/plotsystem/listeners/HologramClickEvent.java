package net.bteuk.plotsystem.listeners;

import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.ParseUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Arrays;

public class HologramClickEvent implements Listener {

    private final PlotSystem instance;

    public HologramClickEvent(PlotSystem instance) {
        this.instance = instance;
        Bukkit.getServer().getPluginManager().registerEvents(this, instance);
    }

    @EventHandler
    private void onHologramClick(eu.decentsoftware.holograms.event.HologramClickEvent e) {
        // Get the click event based on the hologram name.
        String name = e.getHologram().getName();
        String[] args = name.split("_");
        if (args.length >= 2) {
            int plot = ParseUtils.toInt(args[0]);
            String type = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (plot != 0) {
                if (type.equals(PlotStatus.UNCLAIMED.name())) {
                    Bukkit.getScheduler().runTask(instance, () -> e.getPlayer().performCommand("claim " + plot));
                } else {
                    Bukkit.getScheduler().runTask(instance, () -> e.getPlayer().performCommand("plot info " + plot));
                }
            }
        }
    }
}
