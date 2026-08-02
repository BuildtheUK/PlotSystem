package net.bteuk.plotsystem.commands;

import net.bteuk.network.api.PlotAPI;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.gui.CreatePlotGui;
import net.bteuk.plotsystem.gui.CreateZoneGui;
import net.bteuk.plotsystem.utils.User;
import org.btuk.minecraft.gui.GuiManager;
import org.btuk.network.lib.utils.ChatUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateCommand {

    private final GuiManager guiManager;

    private final PlotAPI plotAPI;

    private final LocationCommand locationCommand;

    public CreateCommand(GuiManager guiManager, PlotAPI plotAPI, LocationCommand locationCommand) {
        this.guiManager = guiManager;
        this.plotAPI = plotAPI;
        this.locationCommand = locationCommand;
    }

    public void create(CommandSender sender, String[] args) {

        if (args.length < 2) {

            sender.sendMessage(ChatUtils.error("/plotsystem create [plot, location, zone]"));
            return;

        }

        switch (args[1]) {
            case "plot" -> createPlot(sender);
            case "location" -> locationCommand.createLocation(sender, args);
            case "zone" -> createZone(sender);
            default -> sender.sendMessage(ChatUtils.error("/plotsystem create [plot, location, zone]"));
        }

    }

    private void createPlot(CommandSender sender) {

        // Check if the sender is a player
        if (!(sender instanceof Player)) {

            sender.sendMessage(ChatUtils.error("This command can only be used by players!"));
            return;

        }

        // Get the user
        User user = PlotSystem.getInstance().getUser((Player) sender);

        // Check if the user has permission to use this command
        if (!user.player.hasPermission("uknet.plots.create.plot")) {

            user.player.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
            return;

        }

        // Check if the plot is valid, meaning that at least 3 points are selected with the selection tool.
        if (user.selectionTool.size() < 3) {

            user.player.sendMessage(ChatUtils.error("You must select at least 3 points for a valid plot!"));
            return;

        }

        // Open the plot creation menu
        // Calculate the area of the plot and set a default size estimate.
        user.selectionTool.area();
        user.selectionTool.setDefaultSize();

        // Open the create gui.
        user.createPlotGui = new CreatePlotGui(guiManager, user);
        user.createPlotGui.open(user.player);
    }

    public void createZone(CommandSender sender) {

        // Check if the sender is a player
        if (!(sender instanceof Player)) {

            sender.sendMessage(ChatUtils.error("This command can only be used by players!"));
            return;

        }

        // Get the user
        User user = PlotSystem.getInstance().getUser((Player) sender);

        // Check if the user has permission to use this command
        if (!user.player.hasPermission("uknet.plots.create.zone")) {

            user.player.sendMessage(ChatUtils.error("You do not have permission to use this command!"));
            return;

        }

        // Check if the selection is valid, meaning that at least 3 points are selected with the selection tool.
        if (user.selectionTool.size() < 3) {

            user.player.sendMessage(ChatUtils.error("You must select at least 3 points for a valid zone!"));
            return;

        }

        // If the player already has a zones, cancel, as this is the maximum.
        // Lastly there is a limit of 21 total zones at a time.
        if (plotAPI.isZoneOwner(user.uuid)) {

            user.player.sendMessage(ChatUtils.error("You already have a zone, close this before creating a new one."));
            return;

        } else if (plotAPI.getNumberOfZones() >= 21) {

            user.player.sendMessage(ChatUtils.error("There are currently 21 zones, this is the maximum."));
            return;

        }

        // Open the create zone gui.
        user.createZoneGui = new CreateZoneGui(guiManager, user);
        user.createZoneGui.open(user.player);
    }
}
