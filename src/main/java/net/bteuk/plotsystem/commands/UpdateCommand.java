package net.bteuk.plotsystem.commands;

import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.lib.enums.PlotDifficulties;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class UpdateCommand {

    private static final Component GENERIC_ERROR_MESSAGE = ChatUtils.error("/plotsystem update [plot, location]");

    private static final Component PLOT_ERROR_MESSAGE = ChatUtils.error("/plotsystem update plot <plotID> set difficulty [easy|normal|hard]");

    private final PlotAPI plotAPI;

    private final LocationCommand locationCommand;

    public UpdateCommand(PlotAPI plotAPI, LocationCommand locationCommand) {
        this.plotAPI = plotAPI;
        this.locationCommand = locationCommand;
    }

    public void update(CommandSender sender, String[] args) {

        if (args.length < 2) {
            sender.sendMessage(GENERIC_ERROR_MESSAGE);
            return;
        }

        switch (args[1]) {
            case "plot" -> updatePlot(sender, args);
            case "location" -> locationCommand.updateLocation(sender, args);
            default -> sender.sendMessage(GENERIC_ERROR_MESSAGE);
        }
    }

    private void updatePlot(CommandSender sender, String[] args) {
        // Check if the sender is a player.
        // If so, check if they have permission.
        if (sender instanceof Player p) {
            if (!p.hasPermission("uknet.plots.update.plot")) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
                return;
            }
        }

        // Check if they have enough args and that the correct args have been given.
        if (args.length < 6 || !args[3].equalsIgnoreCase("set") || !args[4].equalsIgnoreCase("difficulty")) {
            sender.sendMessage(PLOT_ERROR_MESSAGE);
            return;
        }

        int plotID;
        try {
            plotID = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PLOT_ERROR_MESSAGE);
            return;
        }

        // Check if a valid difficulty was selected.
        PlotDifficulties plotDifficulty;
        try {
            plotDifficulty = PlotDifficulties.valueOf(args[5].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PLOT_ERROR_MESSAGE);
            return;
        }

        // Check if plot exists.
        if (!plotAPI.plotExists(plotID)) {
            sender.sendMessage(ChatUtils.error("Plot %s does not exist.", args[2]));
            return;
        }

        // Update the plot difficulty.
        plotAPI.setPlotDifficulty(plotID, plotDifficulty.getValue());
        sender.sendMessage(ChatUtils.success("Updated difficulty of plot %s to %s.", args[2], args[5]));

        // Update the plot outlines.
        PlotSystem.getInstance().getUsers().forEach(PlotSystem.getInstance().getOutlines()::addNearbyOutlines);
    }
}
