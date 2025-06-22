package net.bteuk.plotsystem.commands;

import net.bteuk.network.Network;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.NetworkUser;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.gui.CreatePlotGui;
import net.bteuk.plotsystem.gui.CreateZoneGui;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CreateCommand {

    private CreateCommand() {
        // Do nothing
    }

    public static void create(CommandSender sender, String[] args) {

        if (args.length < 2) {

            sender.sendMessage(ChatUtils.error("/plotsystem create [plot, location, zone]"));
            return;

        }

        switch (args[1]) {
            case "plot" -> createPlot(sender);
            case "location" -> LocationCommand.createLocation(sender, args);
            case "zone" -> createZone(sender);
            default -> sender.sendMessage(ChatUtils.error("/plotsystem create [plot, location, zone]"));
        }

    }

    private static void createPlot(CommandSender sender) {

        // Check if the sender is a player
        if (!(sender instanceof Player)) {

            sender.sendMessage(ChatUtils.error("This command can only be used by players!"));
            return;

        }

        // Get the user
        User u = PlotSystem.getInstance().getUser((Player) sender);

        // Check if the user has permission to use this command
        if (!u.player.hasPermission("uknet.plots.create.plot")) {

            u.player.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
            return;

        }

        // Check if the plot is valid, meaning that at least 3 points are selected with the selection tool.
        if (u.selectionTool.size() < 3) {

            u.player.sendMessage(ChatUtils.error("You must select at least 3 points for a valid plot!"));
            return;

        }

        // Open the plot creation menu
        // Calculate the area of the plot and set a default size estimate.
        u.selectionTool.area();
        u.selectionTool.setDefaultSize();

        // Get the user from the network plugin, this plugin handles all guis.
        NetworkUser user = Network.getInstance().getUser(u.player);

        // Open the create gui.
        u.createPlotGui = new CreatePlotGui(u);
        u.createPlotGui.open(user);

    }

    public static void createZone(CommandSender sender) {

        // Check if the sender is a player
        if (!(sender instanceof Player)) {

            sender.sendMessage(ChatUtils.error("This command can only be used by players!"));
            return;

        }

        // Get the user
        User u = PlotSystem.getInstance().getUser((Player) sender);

        // Check if the user has permission to use this command
        if (!u.player.hasPermission("uknet.plots.create.zone")) {

            u.player.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
            return;

        }

        // Check if the selection is valid, meaning that at least 3 points are selected with the selection tool.
        if (u.selectionTool.size() < 3) {

            u.player.sendMessage(ChatUtils.error("You must select at least 3 points for a valid zone!"));
            return;

        }

        PlotSQL plotSQL = Network.getInstance().getPlotSQL();

        // If the player already has a zones, cancel, as this is the maximum.
        // Lastly there is a limit of 21 total zones at a time.
        if (plotSQL.hasRow("SELECT id FROM zone_members WHERE uuid='" + u.player.getUniqueId() + "' AND is_owner=1;")) {

            u.player.sendMessage(ChatUtils.error("You already have a zone, close this before creating a new one."));
            return;

        } else if (plotSQL.getInt("SELECT count(id) FROM zones WHERE status='open';") >= 21) {

            u.player.sendMessage(ChatUtils.error("There are currently 21 zones, this is the maximum."));
            return;

        }

        // Get the user from the network plugin, this plugin handles all guis.
        NetworkUser user = Network.getInstance().getUser(u.player);

        // Open the create zone gui.
        u.createZoneGui = new CreateZoneGui(u);
        u.createZoneGui.open(user);
    }
}
