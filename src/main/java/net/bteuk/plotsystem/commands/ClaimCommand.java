package net.bteuk.plotsystem.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.bteuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.gui.ClaimGui;
import net.bteuk.plotsystem.utils.ParseUtils;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static net.bteuk.plotsystem.PlotSystem.SERVER_NAME;

public class ClaimCommand implements BasicCommand {

    public static final Component TUTORIAL_REQUIRED_MESSAGE =
            ChatUtils.error("To claim a plot you first complete the starter tutorial.")
                    .append(ChatUtils.error(" Click here to open the tutorial menu!").clickEvent(ClickEvent.runCommand("/nav tutorials")));

    private final NetworkAPI networkAPI;

    private final PlotAPI plotAPI;

    private final GuiManager guiManager;

    private final PlotHelper plotHelper;

    public ClaimCommand(NetworkAPI networkAPI, GuiManager guiManager, PlotHelper plotHelper) {
        this.networkAPI = networkAPI;
        this.plotAPI = networkAPI.getPlotAPI();
        this.guiManager = guiManager;
        this.plotHelper = plotHelper;
    }

    public static boolean hasClaimPermission(NetworkAPI networkAPI, Player player, int plot) {

        // Make sure the player has permission to claim plots, else they must complete the tutorial first.
        // Only checked if tutorials are enabled.
        if (!(player.hasPermission("uknet.plots.claim.all") || player.hasPermission("uknet.plots.claim.easy")) && networkAPI.isTutorialsEnabled()) {
            player.sendMessage(TUTORIAL_REQUIRED_MESSAGE);
            return false;
        }

        // Check if the player has permission to claim a plot of this difficulty.
        if (!player.hasPermission("uknet.plots.claim.all")) {
            switch (networkAPI.getPlotAPI().getPlotDifficulty(plot)) {

                case 1 -> {
                    if (!player.hasPermission("uknet.plots.claim.easy")) {
                        player.sendMessage(ChatUtils.error("You do not have permission to claim an ")
                                .append(Component.text("Easy", NamedTextColor.DARK_RED))
                                .append(ChatUtils.error(" plot.")));
                        return false;
                    }
                }

                case 2 -> {
                    if (!player.hasPermission("uknet.plots.claim.normal")) {
                        player.sendMessage(ChatUtils.error("You do not have permission to claim a ")
                                .append(Component.text("Normal", NamedTextColor.DARK_RED))
                                .append(ChatUtils.error(" plot.")));
                        return false;
                    }
                }

                case 3 -> {
                    if (!player.hasPermission("uknet.plots.claim.hard")) {
                        player.sendMessage(ChatUtils.error("You do not have permission to claim a ")
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
    public void execute(CommandSourceStack stack, String @NotNull [] args) {

        // Check if the sender is a player.
        if (!(stack.getSender() instanceof Player player)) {
            return;
        }

        // Get the user.
        User user = PlotSystem.getInstance().getUser(player);

        int plot = 0;
        boolean inPlot = false;
        if (args.length > 0) {
            plot = ParseUtils.toInt(args[0]);
        }
        if (plot == 0) {
            plot = user.inPlot;
            inPlot = true;
        }

        // If the plot is valid open the claim plot gui.
        if (validPlot(user, plot, inPlot)) {
            if (!hasClaimPermission(networkAPI, player, plot)) {
                return;
            }

            // Open claim gui.
            user.claimGui = new ClaimGui(user, plot, guiManager, plotAPI, plotHelper);
            user.claimGui.open(player);
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
        if (plotAPI.isPlotOwner(u.inPlot, u.uuid)) {

            u.player.performCommand("plot info " + u.inPlot);
            return false;

        } else if (plotAPI.isPlotMember(u.inPlot, u.uuid)) {

            u.player.performCommand("plot info " + u.inPlot);
            return false;

        } else if (plotAPI.isPlotClaimed(u.inPlot)) {

            u.player.sendMessage(ChatUtils.error("This plot is already claimed!"));
            return false;

        }

        // Check if you do not already have the maximum number of plots.
        if (plotAPI.getNumberOfPlots(u.uuid) >= PlotSystem.getInstance().getConfig().getInt("plot_maximum")) {

            u.player.sendMessage(ChatUtils.error("You have reached the maximum number of plots."));
            return false;
        }

        // Check if the plot is on this server.
        if (!SERVER_NAME.equals(plotAPI.getLocationServer(plotAPI.getPlotLocation(plot)))) {
            u.player.sendMessage(ChatUtils.error("This plot is on another server, unable to claim it from here."));
            return false;
        }

        // Checks passed, return true.
        return true;
    }
}
