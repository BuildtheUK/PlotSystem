package net.bteuk.plotsystem.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.bteuk.network.Network;
import net.bteuk.network.commands.AbstractCommand;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.NetworkUser;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.gui.ClaimGui;
import net.bteuk.plotsystem.utils.ParseUtils;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import static net.bteuk.network.utils.Constants.SERVER_NAME;
import static net.bteuk.network.utils.Constants.TUTORIALS;

public class ClaimCommand extends AbstractCommand {

    public static final Component TUTORIAL_REQUIRED_MESSAGE =
            ChatUtils.error("To claim a plot you first complete the starter tutorial.")
                    .append(ChatUtils.error(" Click here to open the tutorial menu!").clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/nav tutorials")));
    private final PlotSQL plotSQL;

    public ClaimCommand(PlotSQL plotSQL) {
        this.plotSQL = plotSQL;
    }

    public static boolean hasClaimPermission(User u, NetworkUser user, int plot) {

        // Make sure the player has permission to claim plots, else they must complete the tutorial first.
        // Only checked if tutorials are enabled.
        if (!(user.hasPermission("uknet.plots.claim.all") || user.hasPermission("uknet.plots.claim.easy")) && TUTORIALS) {
            user.player.sendMessage(TUTORIAL_REQUIRED_MESSAGE);
            return false;
        }

        // Check if the player has permission to claim a plot of this difficulty.
        if (!user.hasPermission("uknet.plots.claim.all")) {
            switch (u.plotSQL.getInt("SELECT difficulty FROM plot_data WHERE id=" + plot + ";")) {

                case 1 -> {
                    if (!user.hasPermission("uknet.plots.claim.easy")) {
                        user.sendMessage(ChatUtils.error("You do not have permission to claim an ")
                                .append(Component.text("Easy", NamedTextColor.DARK_RED))
                                .append(ChatUtils.error(" plot.")));
                        return false;
                    }
                }

                case 2 -> {
                    if (!user.hasPermission("uknet.plots.claim.normal")) {
                        user.sendMessage(ChatUtils.error("You do not have permission to claim a ")
                                .append(Component.text("Normal", NamedTextColor.DARK_RED))
                                .append(ChatUtils.error(" plot.")));
                        return false;
                    }
                }

                case 3 -> {
                    if (!user.hasPermission("uknet.plots.claim.hard")) {
                        user.sendMessage(ChatUtils.error("You do not have permission to claim a ")
                                .append(Component.text("Hard", NamedTextColor.DARK_RED))
                                .append(ChatUtils.error(" plot.")));
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {

        // Check if the sender is a player.
        Player player = getPlayer(stack);
        if (player == null) {
            return;
        }

        // Get the user.
        User u = PlotSystem.getInstance().getUser(player);

        int plot = 0;
        boolean inPlot = false;
        if (args.length > 0) {
            plot = ParseUtils.toInt(args[0]);
        }
        if (plot == 0) {
            plot = u.inPlot;
            inPlot = true;
        }

        // If the plot is valid open the claim plot gui.
        if (validPlot(u, plot, inPlot)) {

            NetworkUser user = Network.getInstance().getUser(u.player);

            if (!hasClaimPermission(u, user, plot)) {
                return;
            }

            // Open claim gui.
            u.claimGui = new ClaimGui(u, plot);
            u.claimGui.open(user);

        }
    }

    public boolean validPlot(User u, int plot, boolean inPlot) {

        // If the player is not in a plot tell them.
        if (plot == 0) {
            if (inPlot) {
                u.player.sendMessage(ChatUtils.error("You are not standing in a plot."));
            } else {
                u.player.sendMessage(ChatUtils.error("This is not a valid plot."));
            }
            return false;
        }

        // If the plot is already claimed tell them.
        // If they are the owner or a member tell them.
        if (plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + plot + " AND uuid='" + u.player.getUniqueId() + "' AND is_owner=1;")) {

            u.player.sendMessage(ChatUtils.error("You are already the owner of this plot!"));
            return false;

        } else if (plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + plot + " AND uuid='" + u.player.getUniqueId() + "' AND is_owner=0;")) {

            u.player.sendMessage(ChatUtils.error("You are already a member of this plot!"));
            return false;

        } else if (plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + plot + " AND status='claimed';")) {

            u.player.sendMessage(ChatUtils.error("This plot is already claimed!"));
            return false;

        }

        // Check if you do not already have the maximum number of plots.
        if (plotSQL.getInt("SELECT count(id) FROM plot_members WHERE uuid='" + u.uuid + "';") >= PlotSystem.getInstance().getConfig().getInt("plot_maximum")) {

            u.player.sendMessage(ChatUtils.error("You have reached the maximum number of plots."));
            return false;
        }

        // Check if the plot is on this server.
        if (!plotSQL.hasRow(
                "SELECT pd.id FROM plot_data AS pd INNER JOIN location_data AS ld ON ld.name=pd.location WHERE pd.id=" + plot + " AND ld.server='" + SERVER_NAME + "';")) {
            u.player.sendMessage(ChatUtils.error("This plot is on another server, unable to claim it from here."));
            return false;
        }

        // Checks passed, return true.
        return true;
    }
}
