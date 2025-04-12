package net.bteuk.plotsystem;

import com.sk89q.worldguard.WorldGuard;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.plotsystem.events.EventManager;
import net.bteuk.plotsystem.utils.Inactive;
import net.bteuk.plotsystem.utils.Outlines;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Bukkit;

import java.util.ArrayList;

import static net.bteuk.network.utils.Constants.LOGGER;

public class Timers {

    final WorldGuard wg;
    // Plugin
    private final PlotSystem instance;
    // Users
    private final ArrayList<User> users;
    // Server name
    private final String SERVER_NAME;
    // SQL
    private final GlobalSQL globalSQL;
    // Outlines.
    private final Outlines outlines;
    // Server events
    private ArrayList<String[]> events;
    private boolean isBusy = false;

    public Timers(PlotSystem instance, GlobalSQL globalSQL) {

        this.instance = instance;
        this.users = instance.getUsers();

        this.globalSQL = globalSQL;

        SERVER_NAME = PlotSystem.SERVER_NAME;

        events = new ArrayList<>();

        wg = WorldGuard.getInstance();

        outlines = instance.getOutlines();

    }

    public void startTimers() {

        // 1 tick timer.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(instance, () -> {

            // Check for new server_events.
            if (globalSQL.hasRow("SELECT uuid FROM server_events WHERE server='" + SERVER_NAME + "' AND type='plotsystem';")) {

                // If events is not empty, skip this iteration.
                // Additionally isBusy needs to be false, implying that the server is not still running a previous iteration.
                if (events.isEmpty() && !isBusy) {

                    isBusy = true;

                    // Get events for this server.
                    events = globalSQL.getEvents(SERVER_NAME, "plotsystem", events);

                    for (String[] event : events) {

                        // Deal with events here.
                        LOGGER.info("Event: " + event[1]);

                        // Split the event by word.
                        String[] aEvent = event[1].split(" ");

                        // Send the event to the event handler.
                        EventManager.event(event[0], aEvent);

                    }

                    // Clear events when done.
                    events.clear();
                    isBusy = false;
                }
            }
        }, 0L, 1L);

        // 1 second timer.
        // Update plot and zone outlines.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(instance, () -> {

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
        }, 0L, 20L);

        // 1 hour timer.
        // Remove inactive plots.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(instance, () -> {
            Inactive.cancelInactivePlots();
            Inactive.closeExpiredZones();
        }, 1200L, 72000L);
    }
}