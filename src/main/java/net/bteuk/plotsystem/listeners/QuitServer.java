package net.bteuk.plotsystem.listeners;

import net.bteuk.network.Network;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitServer implements Listener {

    public QuitServer(PlotSystem plugin) {

        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void quitEvent(PlayerQuitEvent e) {

        //Get instance of plugin.
        PlotSystem instance = PlotSystem.getInstance();

        //Get user from the list.
        User user = instance.getUser(e.getPlayer());

        //If no user was found print error in console.
        if (user == null) {
            PlotSystem.LOGGER.warning("Error: User " + e.getPlayer().getName() + " not found in the list of online users!");
            return;
        }

        //If the player is in a review, cancel it.
        if (user.getReview() != null) {

            PlotSQL plotSQL = Network.getInstance().getPlotSQL();

            //Remove the reviewer from the plot.
            try {
                WorldGuardFunctions.removeMember(String.valueOf(user.getReview().getPlotID()), user.uuid, Bukkit.getWorld(plotSQL.getString("SELECT location FROM plot_data WHERE id=" + user.getReview().getPlotID() + ";")));
            } catch (RegionManagerNotFoundException | RegionNotFoundException ex) {
                ex.printStackTrace();
            }

            // Set submitted status back to the previous status.
            switch (user.getReview().getMode()) {
                case REVIEWING -> PlotHelper.updateSubmittedStatus(user.getReview().getPlotID(), SubmittedStatus.SUBMITTED);
                case VERIFYING -> PlotHelper.updateSubmittedStatus(user.getReview().getPlotID(), SubmittedStatus.AWAITING_VERIFICATION);
            }

            //Close review.
            user.getReview().closeReview();

        }

        //If the player has a claim or create gui delete it.
        if (user.claimGui != null) {
            user.claimGui.delete();
        }

        if (user.createPlotGui != null) {
            user.createPlotGui.delete();
        }

        if (user.createZoneGui != null) {
            user.createZoneGui.delete();
        }

        //Remove player from outlines.
        PlotSystem.getInstance().getOutlines().removePlayer(e.getPlayer());

        //Remove user from list
        instance.removeUser(user);

    }

}
