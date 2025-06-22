package net.bteuk.plotsystem.events;

import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

/**
 * Event to allow adding/removing outlines for specific plots.
 */
public class OutlinesEvent {
    public static void event(String uuid, String[] event) {

        // Get the user.
        Player p = Bukkit.getPlayer(UUID.fromString(uuid));
        if (p == null) {
            LOGGER.warning("Player " + uuid + " is not on the server the event was sent to!");
            return;
        }
        User user = PlotSystem.getInstance().getUser(p);
        if (user == null) {
            LOGGER.warning("User " + p.getName() + " is not on the server the event was sent to!");
            return;
        }

        // Events adding/removing outlines for a specific plot.
        if (event[1].equals("toggle")) {// Check if the outlines are currently disabled.
            if (user.getSkipOutlines().contains(event[2])) {
                // Enable:
                // Remove the plot from the list of ignored outlines.
                user.getSkipOutlines().remove(event[2]);
                // Add the outlines back
                PlotSystem.getInstance().getOutlines().addPlotOutlineForPlayer(event[2], p);
                // Notify player.
                p.sendMessage(ChatUtils.success("Enabled outlines for plot " + event[2]));
            } else {
                // Disable:
                // Add the plot to the list of ignored outlines.
                user.getSkipOutlines().add(event[2]);
                // Remove the outline.
                PlotSystem.getInstance().getOutlines().removePlotOutlineForPlayer(event[2], p);
                // Notify player.
                p.sendMessage(ChatUtils.success("Disabled outlines for plot " + event[2]));
            }
        }
    }
}
