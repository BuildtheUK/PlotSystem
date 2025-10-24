package net.bteuk.plotsystem.events;

import net.bteuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.commands.ClaimCommand;
import net.bteuk.plotsystem.gui.ClaimGui;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;
import static net.bteuk.plotsystem.commands.ClaimCommand.TUTORIAL_REQUIRED_MESSAGE;

public class ClaimEvent implements Event {

    private final NetworkAPI networkAPI;

    private final PlotAPI plotAPI;

    private final GuiManager guiManager;

    public ClaimEvent(NetworkAPI networkAPI, GuiManager guiManager) {
        this.networkAPI = networkAPI;
        this.plotAPI = networkAPI.getPlotAPI();
        this.guiManager = guiManager;
    }

    public void event(String uuid, String[] event, String sMessage) {

        // Events for claiming
        if (event[1].equals("plot")) {

            // Get the user.
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));

            if (p == null) {

                LOGGER.warning("Player " + uuid + " is not on the server the event was sent to!");
                return;

            }

            // Make sure the player has permission to claim plots, else they must complete the tutorial first.
            // Only checked if tutorials are enabled.
            if (!(p.hasPermission("uknet.plots.claim.all") || p.hasPermission("uknet.plots.claim.easy")) && networkAPI.isTutorialsEnabled()) {

                p.sendMessage(TUTORIAL_REQUIRED_MESSAGE);
                return;

            }

            User u = PlotSystem.getInstance().getUser(p);

            // If the player is not in a plot tell them.
            if (u.inPlot == 0) {

                p.sendMessage(ChatUtils.error("You are not in a plot!"));
                return;

            }

            // If the plot is already claimed tell them.
            // If they are the owner or a member tell them.
            if (plotAPI.isPlotOwner(u.inPlot, uuid)) {

                p.sendMessage(ChatUtils.error("You are already the owner of this plot!"));
                return;

            } else if (plotAPI.isPlotMember(u.inPlot, uuid)) {

                p.sendMessage(ChatUtils.error("You are already a member of this plot!"));
                return;

            } else if (plotAPI.isPlotClaimed(u.inPlot)) {

                p.sendMessage(ChatUtils.error("This plot is already claimed!"));
                return;

            }

            // Check if you do not already have the maximum number of plots.
            if (plotAPI.getNumberOfPlots(uuid) >= PlotSystem.getInstance().getConfig().getInt("plot_maximum")) {

                p.sendMessage(ChatUtils.error("You have reached the maximum number of plots."));
                return;

            }

            // Check if the player has permission to claim a plot of this difficulty.
            if (!ClaimCommand.hasClaimPermission(networkAPI, p, u.inPlot)) {
                return;
            }

            u.player.closeInventory();
            u.claimGui = new ClaimGui(u, u.inPlot, guiManager);
            u.claimGui.open(p);

        }
    }
}
