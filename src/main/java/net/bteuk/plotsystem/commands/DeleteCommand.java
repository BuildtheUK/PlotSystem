package net.bteuk.plotsystem.commands;

import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.Multiverse;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class DeleteCommand {

    private final GlobalSQL globalSQL;
    private final PlotSQL plotSQL;

    public DeleteCommand(GlobalSQL globalSQL, PlotSQL plotSQL) {

        this.globalSQL = globalSQL;
        this.plotSQL = plotSQL;

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

                deleteLocation(sender, args);
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
        if (!plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + plotID + ";")) {
            sender.sendMessage(ChatUtils.error("This plot does not exist."));
        }

        // Check if plot is unclaimed
        if (!(plotSQL.hasRow("SELECT id FROM plot_data WHERE id=" + plotID + " AND status='unclaimed'"))) {
            sender.sendMessage(ChatUtils.error("This plot is claimed, you can only delete unclaimed plots."));
            return;
        }

        // Get world of plot.
        World world = Bukkit.getWorld(plotSQL.getString("SELECT location FROM plot_data WHERE id=" + plotID + ";"));

        // If world is null then the plot is not on this server.
        if (world == null) {
            sender.sendMessage(ChatUtils.error("The plot is not on this server."));
            return;
        }

        // Delete plot.
        try {
            if (WorldGuardFunctions.delete(String.valueOf(plotID), world)) {

                // Set plot to deleted.
                PlotHelper.updatePlotStatus(plotID, PlotStatus.DELETED);
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

    private void deleteLocation(CommandSender sender, String[] args) {

        // If sender is a player, check for permission.
        if (sender instanceof Player p) {

            if (!(p.hasPermission("uknet.plots.delete.location"))) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command."));
                return;
            }

        }

        // Check arg count.
        if (args.length < 3) {

            sender.sendMessage(ChatUtils.error("/plotsystem delete location [name]"));
            return;

        }

        // Check if location exists.
        if (!(plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + args[2] + "';"))) {

            sender.sendMessage(ChatUtils.error("The location ")
                    .append(Component.text(args[2], NamedTextColor.DARK_RED))
                    .append(ChatUtils.error(" does not exist.")));
            return;

        }

        // Check if the location is on this server.
        if (!(plotSQL.getString("SELECT server FROM location_data WHERE name='" + args[2] + "';").equals(PlotSystem.SERVER_NAME))) {

            sender.sendMessage(ChatUtils.error("This location is not on this server."));
            return;

        }

        // If location has plots, cancel.
        if (plotSQL.hasRow("SELECT id FROM plot_data WHERE location='" + args[2] + "' AND status<>'completed' AND status<>'deleted';")) {

            sender.sendMessage(ChatUtils.error("This location active has plots, all plots must be deleted or completed to remove the location."));
            return;

        }

        // Delete location.
        if (Multiverse.deleteWorld(args[2])) {

            // Delete location from database.
            plotSQL.update("DELETE FROM location_data WHERE name='" + args[2] + "';");
            sender.sendMessage(ChatUtils.success("Deleted location ")
                    .append(Component.text(args[2], NamedTextColor.DARK_AQUA)));
            LOGGER.info("Deleted location " + args[2] + ".");

            // Get regions from database.
            ArrayList<String> regions = plotSQL.getStringList("SELECT region FROM regions WHERE location='" + args[2] + "';");

            // Delete regions from database.
            plotSQL.update("DELETE FROM regions WHERE location='" + args[2] + "';");

            // Iterate through regions to unlock them on Earth.
            for (String region : regions) {
                globalSQL.update("INSERT INTO server_events(uuid,type,server,event) VALUES(NULL,'network','"
                        + globalSQL.getString("SELECT name FROM server_data WHERE type='earth';") + "'," +
                        "'region set default " + region + "');");
            }

        } else {

            sender.sendMessage(ChatUtils.error("An error occurred while deleting the world."));
            LOGGER.warning("An error occurred while deleting world " + args[2] + ".");

        }
    }
}
