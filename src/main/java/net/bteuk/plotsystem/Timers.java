package net.bteuk.plotsystem;

import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.api.TimerAPI;
import net.bteuk.plotsystem.utils.Inactive;
import net.bteuk.plotsystem.utils.Outlines;
import net.bteuk.plotsystem.utils.User;

import java.util.ArrayList;

public class Timers {
    private final TimerAPI timerAPI;
    // Users
    private final ArrayList<User> users;
    // Outlines.
    private final Outlines outlines;

    public Timers(PlotSystem instance, NetworkAPI networkAPI) {
        this.users = instance.getUsers();
        this.timerAPI = networkAPI.getTimerAPI();
        outlines = instance.getOutlines();
    }

    public void startTimers() {


        // 1-second timer.
        // Update plot and zone outlines.
        timerAPI.registerTimer(() -> {

            for (User u : users) {

                /*
                Check if the location of the player has changed by more than 50 blocks,
                or if the player has switched world.
                If either are true, recalculate the outlines.
                Else try to update the existing outlines,
                catch a nullpointerexception,
                this implies that the player has no outlines
                then also add the outlines anew.
                 */
                if (!u.player.getWorld().equals(u.lastLocation.getWorld())) {

                    outlines.addNearbyOutlines(u);
                    u.lastLocation = u.player.getLocation();

                } else if (u.player.getLocation().distance(u.lastLocation) >= 50) {

                    outlines.addNearbyOutlines(u);
                    u.lastLocation = u.player.getLocation();

                } else {

                    try {
                        outlines.refreshOutlinesForPlayer(u.player);
                    } catch (NullPointerException e) {
                        outlines.addNearbyOutlines(u);
                        u.lastLocation = u.player.getLocation();
                    }

                }

            }
        }, 20L);

        // 1-hour timer.
        // Remove inactive plots.
        timerAPI.registerTimer(() -> {
            Inactive.cancelInactivePlots();
            Inactive.closeExpiredZones();
        }, 1200L, 72000L);
    }
}