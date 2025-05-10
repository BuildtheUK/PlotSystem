package net.bteuk.plotsystem.commands;

import net.bteuk.network.Network;
import net.bteuk.network.lib.enums.PlotDifficulties;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class UpdateCommand {

    private static final Component GENERIC_ERROR_MESSAGE = ChatUtils.error("/plotsystem update [plot, location]");

    private static final Component PLOT_ERROR_MESSAGE = ChatUtils.error("/plotsystem update plot <plotID> set difficulty [easy|normal|hard]");

    private UpdateCommand() {
        // Do nothing
    }

    public static void update(CommandSender sender, String[] args) {

        if (args.length < 2) {
            sender.sendMessage(GENERIC_ERROR_MESSAGE);
            return;
        }

        switch (args[1]) {
            case "plot" -> updatePlot(sender, args);
            case "location" -> LocationCommand.updateLocation(sender, args);
            default -> sender.sendMessage(GENERIC_ERROR_MESSAGE);
        }
    }

    private static void updatePlot(CommandSender sender, String[] args) {
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

        PlotSQL plotSQL = Network.getInstance().getPlotSQL();

        // Check if plot exists.
        if (!plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + plotID + " AND status IN ('unclaimed','claimed','submitted');")) {
            sender.sendMessage(ChatUtils.error("Plot %s does not exist.", args[2]));
            return;
        }

        // Update the plot difficulty.
        plotSQL.update("UPDATE plot_data SET difficulty=" + plotDifficulty.getValue() + " WHERE id=" + plotID + ";");
        sender.sendMessage(ChatUtils.success("Updated difficulty of plot %s to %s.", args[2], args[5]));
    }
}
