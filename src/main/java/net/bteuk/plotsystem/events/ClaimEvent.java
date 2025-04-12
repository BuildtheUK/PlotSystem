package net.bteuk.plotsystem.events;

import net.bteuk.network.Network;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.NetworkUser;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.commands.ClaimCommand;
import net.bteuk.plotsystem.gui.ClaimGui;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

import static net.bteuk.network.utils.Constants.TUTORIALS;
import static net.bteuk.plotsystem.PlotSystem.LOGGER;
import static net.bteuk.plotsystem.commands.ClaimCommand.TUTORIAL_REQUIRED_MESSAGE;

public class ClaimEvent {

    public static void event(String uuid, String[] event) {

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
            if (!(p.hasPermission("uknet.plots.claim.all") || p.hasPermission("uknet.plots.claim.easy")) && TUTORIALS) {

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
            if (u.plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + u.inPlot + " AND uuid='" + u.player.getUniqueId() + "' AND is_owner=1;")) {

                p.sendMessage(ChatUtils.error("You are already the owner of this plot!"));
                return;

            } else if (u.plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + u.inPlot + " AND uuid='" + u.player.getUniqueId() + "' AND is_owner=0;")) {

                p.sendMessage(ChatUtils.error("You are already a member of this plot!"));
                return;

            } else if (u.plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + u.inPlot + " AND status='claimed';")) {

                p.sendMessage(ChatUtils.error("This plot is already claimed!"));
                return;

            }

            // Check if you do not already have the maximum number of plots.
            if (u.plotSQL.getInt("SELECT count(id) FROM plot_members WHERE uuid='" + uuid + "';") >= PlotSystem.getInstance().getConfig().getInt("plot_maximum")) {

                p.sendMessage(ChatUtils.error("You have reached the maximum number of plots."));
                return;

            }

            // Open the claim gui.
            NetworkUser user = Network.getInstance().getUser(u.player);

            // Check if the player has permission to claim a plot of this difficulty.
            if (!ClaimCommand.hasClaimPermission(u, user, u.inPlot)) {
                return;
            }

            u.player.closeInventory();
            u.claimGui = new ClaimGui(u, u.inPlot);
            u.claimGui.open(user);

        }
    }
}
