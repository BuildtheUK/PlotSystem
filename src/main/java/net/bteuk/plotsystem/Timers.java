package net.bteuk.plotsystem;

import net.bteuk.network.api.NetworkAPI;
import net.bteuk.plotsystem.utils.Inactive;
import net.bteuk.plotsystem.utils.Outlines;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;

import java.util.List;

public final class Timers {

    private static boolean registered = false;

    private Timers() {
        // Private constructor
    }

    public static void registerTimers(NetworkAPI networkAPI, PlotHelper plotHelper, Outlines outlines, List<User> users) {

        if (registered) {
            PlotSystem.LOGGER.warning("Timers already registered!");
            return;
        }

        // 1-second timer.
        // Update plot and zone outlines.
        networkAPI.getTimerAPI().registerTimer(() -> {

            for (User u : users) {

                /* Check if the location of the player has changed by more than 50 blocks, or if the player has switched world. If either are true, recalculate the outlines.
                Else try to update the existing outlines, catch a nullpointerexception, this implies that the player has no outlines then also add the outlines anew.
                 */
                if (!u.player.getWorld().equals(u.lastLocation.getWorld())) {

                    outlines.addNearbyOutlines(u, networkAPI.getPlotAPI());
                    u.lastLocation = u.player.getLocation();

                } else if (u.player.getLocation().distance(u.lastLocation) >= 50) {

                    outlines.addNearbyOutlines(u, networkAPI.getPlotAPI());
                    u.lastLocation = u.player.getLocation();

                } else {

                    try {
                        outlines.refreshOutlinesForPlayer(u.player);
                    } catch (NullPointerException e) {
                        outlines.addNearbyOutlines(u, networkAPI.getPlotAPI());
                        u.lastLocation = u.player.getLocation();
                    }

                }

            }
        }, 1000L);

        // 1-hour timer.
        // Remove inactive plots.
        networkAPI.getTimerAPI().registerTimer(() -> {
            Inactive.cancelInactivePlots(networkAPI, plotHelper);
            Inactive.closeExpiredZones(networkAPI);
        }, 3600000L, 60000L);

        registered = true;
    }
}