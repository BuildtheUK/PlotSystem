package net.bteuk.plotsystem.events;

import io.papermc.lib.PaperLib;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.network.api.plotsystem.SubmittedStatus;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.reviewing.Review;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class ReviewEvent implements Event {

    private final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    public ReviewEvent(PlotAPI plotAPI, PlotHelper plotHelper) {
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;
    }

    public void event(String uuid, String[] event, String message) {

        // Events for claiming
        if (event[1].equals("plot")) {

            // Get the user.
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));

            if (player == null) {
                LOGGER.warning("Player " + uuid + " is not on the server the event was sent to!");
                return;
            }

            User user = PlotSystem.getInstance().getUser(player);

            // If user is null stop.
            if (user == null) {
                LOGGER.severe(String.format("User for player %s is null, this should not be possible!!!", player.getName()));
                return;
            }

            // If the user is already reviewing, prevent this was happening.
            if (user.getReview() != null) {
                user.player.sendMessage(ChatUtils.error("You are already reviewing/verifying a plot, please complete this first!"));
                return;
            }

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            // Get world of plot.
            String location = plotAPI.getPlotLocation(id);
            World world = Bukkit.getWorld(location);

            // Check if the plot is still submitted.
            if (plotAPI.getPlotSubmissionStatus(id) == SubmittedStatus.SUBMITTED) {

                // Set the plot to under review.
                plotHelper.updateSubmittedStatus(id, SubmittedStatus.UNDER_REVIEW);

                // Create new review instance for user.
                user.setReview(new Review(PlotSystem.getInstance(), id, user));

                // Add the reviewer to the plot.
                try {
                    WorldGuardFunctions.addMember(String.valueOf(id), uuid, world);
                } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                    user.player.sendMessage(Component.text("Unable to add you as a member of the plot you are reviewing, please contact an admin to report this issue."));
                    e.printStackTrace();
                }

                // Teleport the reviewer to the plot.
                try {
                    Location l = WorldGuardFunctions.getCurrentLocation(event[2], world);
                    CompletableFuture<Boolean> teleport = PaperLib.teleportAsync(player, l);
                    teleport.whenComplete((bool, throwable) -> {
                        // Send link to plot in Google Maps once teleport is complete.
                        player.performCommand("ll");
                    });
                } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                    user.player.sendMessage(Component.text("Unable to teleport you to the plot, please contact an admin to report this issue."));
                    e.printStackTrace();
                }

            } else {

                player.sendMessage(ChatUtils.error("The plot is no longer submitted."));

            }
        }
    }
}
