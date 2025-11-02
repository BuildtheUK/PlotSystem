package net.bteuk.plotsystem.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.bteuk.minecraft.gui.GuiManager;
import net.bteuk.network.api.CoordinateAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.papercore.LocationAdapter;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.ParseUtils;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.PlotHologram;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PlotSystemCommand implements BasicCommand, TabCompleter {

    private static final List<String> options = List.of("create", "selectiontool", "delete", "help", "setalias", "movemarker", "update");

    private final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    private final CoordinateAPI coordinateAPI;

    private final CreateCommand createCommand;

    private final DeleteCommand deleteCommand;

    private final UpdateCommand updateCommand;

    public PlotSystemCommand(PlotAPI plotAPI, PlotHelper plotHelper, CoordinateAPI coordinateAPI, GuiManager guiManager, LocationCommand locationCommand) {
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;
        this.coordinateAPI = coordinateAPI;

        this.createCommand = new CreateCommand(guiManager, plotAPI, locationCommand);
        this.deleteCommand = new DeleteCommand(plotAPI, plotHelper, locationCommand);
        this.updateCommand = new UpdateCommand(plotAPI, locationCommand);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {

        CommandSender sender = stack.getSender();

        // If there are no arguments, return.
        if (args.length == 0) {
            sender.sendMessage(ChatUtils.error("/plotsystem help"));
            return;
        }

        switch (args[0]) {

            case "selectiontool" -> selectionTool(sender);
            case "create" -> createCommand.create(sender, args);
            case "delete" -> deleteCommand.delete(sender, args);
            case "update" -> updateCommand.update(sender, args);
            case "help" -> help(sender);
            case "setalias" -> {

                if (args.length == 3) {
                    setAlias(sender, args[1], args[2]);
                } else {
                    sender.sendMessage(ChatUtils.error("/plotsystem setalias [location] [alias]"));
                }
            }
            case "movemarker" -> moveHologram(sender, args);
            default -> sender.sendMessage(ChatUtils.error("/plotsystem help"));
        }
    }

    private void help(CommandSender sender) {

        sender.sendMessage(Component.text("/plotsystem setalias [location] [alias]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/plotsystem selectiontool ", NamedTextColor.GRAY)
                .append(ChatUtils.line("- Get the selection tool to create plots.")));
        sender.sendMessage(Component.text("/plotsystem create plot ", NamedTextColor.GRAY)
                .append(ChatUtils.line("- Create a plot for your current selection.")));
        sender.sendMessage(Component.text("/plotsystem delete plot <plotID> ", NamedTextColor.GRAY)
                .append(ChatUtils.line("- Delete an unclaimed plot.")));
        sender.sendMessage(Component.text("/plotsystem create location [name] <Xmin> <Zmin> <Xmax> <Zmax>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/plotsystem delete location [name]", NamedTextColor.GRAY));

    }

    private void selectionTool(CommandSender sender) {

        // Check if the sender is a player.
        if (!(sender instanceof Player)) {

            sender.sendMessage(ChatUtils.error("You must be a player to use this command."));
            return;

        }

        // Get the user.
        User u = PlotSystem.getInstance().getUser((Player) sender);

        // Check if the user has permission.
        if (!u.player.hasPermission("uknet.plots.select")) {

            u.player.sendMessage(ChatUtils.error("You do not have permission to do this."));
            return;

        }

        // Give the player a selection tool.
        u.selectionTool.giveSelectionTool();

    }

    private void setAlias(CommandSender sender, String location, String alias) {

        if (sender instanceof Player p) {
            if (!p.hasPermission("uknet.plots.setalias")) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command."));
                return;
            }
        }

        if (plotAPI.hasLocation(location)) {

            plotAPI.setLocationAlias(location, alias);
            sender.sendMessage(ChatUtils.success("Set alias of location ")
                    .append(Component.text(location, NamedTextColor.DARK_AQUA))
                    .append(ChatUtils.success(" to "))
                    .append(Component.text(alias, NamedTextColor.DARK_AQUA)));

        } else {
            sender.sendMessage(ChatUtils.error("The location ")
                    .append(Component.text(location, NamedTextColor.DARK_RED))
                    .append(ChatUtils.error(" does not exist.")));
        }
    }

    private void moveHologram(CommandSender sender, String[] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("uknet.plots.movemarker")) {
                p.sendMessage(ChatUtils.error("You do not have permission to use this command."));
                return;
            }

            if (args.length < 2) {
                p.sendMessage(ChatUtils.error("/plotsystem movemarker <plotID>"));
                return;
            }

            int plot = ParseUtils.toInt(args[1]);

            if (plot == 0) {
                p.sendMessage(ChatUtils.error("/plotsystem movemarker <plotID>"));
                return;
            }

            // Get the user.
            User u = PlotSystem.getInstance().getUser(p);

            if (u == null) {
                p.sendMessage(ChatUtils.error("An error occurred, please rejoin."));
                return;
            }

            // Check if the player is in the same world as the plot.
            if (u.inPlot != plot) {
                p.sendMessage(ChatUtils.error("You must be standing in the plot to move the marker."));
                return;
            }

            // Get the coordinate of the marker.
            int coordinate_id = plotAPI.getPlotCoordinate(plot);
            Location l = p.getLocation().clone();
            l.setY(l.getY() + 2);

            if (coordinate_id == 0) {
                // Create a new coordinate id and add it to the plot data.
                coordinate_id = coordinateAPI.addCoordinate(LocationAdapter.adapt(l));
                plotAPI.updatePlotCoordinate(plot, coordinate_id);
                // Add the hologram.
                plotHelper.addPlotHologram(new PlotHologram(plot, plotAPI, coordinateAPI));
                p.sendMessage(ChatUtils.success("Added marker to plot " + plot));
            } else {
                // Update the existing coordinate location.
                coordinateAPI.updateCoordinate(coordinate_id, LocationAdapter.adapt(l));
                // Update the hologram.
                plotHelper.updatePlotHologram(plot);
                p.sendMessage(ChatUtils.success("Moved marker of plot " + plot));
            }

        } else {
            sender.sendMessage(ChatUtils.error("You must be a player to use this command."));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        // Return list.
        List<String> returns = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], options, returns);
        } else if (args.length == 0) {
            return options;
        }
        return returns;
    }
}
