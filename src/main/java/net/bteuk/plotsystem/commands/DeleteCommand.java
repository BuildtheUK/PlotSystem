package net.bteuk.plotsystem.commands;

import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class DeleteCommand {

    private final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    private final LocationCommand locationCommand;

    public DeleteCommand(PlotAPI plotAPI, PlotHelper plotHelper, LocationCommand locationCommand) {
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;
        this.locationCommand = locationCommand;
    }

    public void delete(CommandSender sender, String[] args) {

        if (args.length < 2) {

            sender.sendMessage(ChatUtils.error("/plotsystem delete [plot, location, zone]"));
            return;

        }

        switch (args[1]) {

            case "plot":

                deletePlot(sender, args);
                break;

            case "location":

                locationCommand.deleteLocation(sender, args);
                break;

            case "zone":

                break;

            default:

                sender.sendMessage(ChatUtils.error("/plotsystem delete [plot, location, zone]"));

        }
    }

    private void deletePlot(CommandSender sender, String[] args) {

        // Check if the sender has permission.
        // If there are no additional args then the sender must be a player.
        if (sender instanceof Player p) {

            if (!(p.hasPermission("uknet.plots.delete.plot"))) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command."));
                return;
            }

        } else if (args.length < 3) {

            sender.sendMessage(ChatUtils.error("/plotsystem delete plot <plotID>"));
            return;

        }

        int plotID;

        if (args.length >= 3) {

            try {

                plotID = Integer.parseInt(args[2]);

            } catch (NumberFormatException e) {

                sender.sendMessage(ChatUtils.error("/plotsystem delete plot <plotID>"));
                return;

            }

        } else {

            Player p = (Player) sender;

            // Get plot that the player is standing in.
            User u = PlotSystem.getInstance().getUser(p);

            if (u.inPlot == 0) {

                p.sendMessage(ChatUtils.error("You are not standing in a plot."));
                return;

            }

            plotID = u.inPlot;

        }

        if (plotID == 0) {
            return;
        }

        // Check if plot exists.
        if (!plotAPI.plotExists(plotID)) {
            sender.sendMessage(ChatUtils.error("This plot does not exist."));
        }

        // Check if the plot is unclaimed
        if (!(plotAPI.isPlotUnclaimed(plotID))) {
            sender.sendMessage(ChatUtils.error("This plot is claimed, you can only delete unclaimed plots."));
            return;
        }

        // Get world of plot.
        World world = Bukkit.getWorld(plotAPI.getPlotLocation(plotID));

        // If world is null then the plot is not on this server.
        if (world == null) {
            sender.sendMessage(ChatUtils.error("The plot is not on this server."));
            return;
        }

        // Delete plot.
        try {
            if (WorldGuardFunctions.delete(String.valueOf(plotID), world)) {

                // Set plot to deleted.
                plotHelper.updatePlotStatus(plotID, PlotStatus.DELETED);
                sender.sendMessage(ChatUtils.success("Plot ")
                        .append(Component.text(plotID, NamedTextColor.DARK_AQUA))
                        .append(ChatUtils.success(" deleted.")));

            } else {

                sender.sendMessage(ChatUtils.error("An error occured while deleting the plot."));
                LOGGER.warning("An error occurred while deleting plot " + plotID + " from WorldGuard.");

            }
        } catch (RegionManagerNotFoundException e) {
            sender.sendMessage(ChatUtils.error("An error occurred while deleting the plot, please contact an admin."));
            e.printStackTrace();
        }
    }
}
