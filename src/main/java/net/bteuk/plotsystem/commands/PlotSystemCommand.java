package net.bteuk.plotsystem.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.bteuk.network.Network;
import net.bteuk.network.commands.AbstractCommand;
import net.bteuk.network.commands.tabcompleters.FixedArgSelector;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.ParseUtils;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.PlotHologram;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class PlotSystemCommand extends AbstractCommand {

    private final PlotSQL plotSQL;
    private final GlobalSQL globalSQL;

    public PlotSystemCommand(GlobalSQL globalSQL, PlotSQL plotSQL) {
        this.plotSQL = plotSQL;
        this.globalSQL = globalSQL;

        setTabCompleter(new FixedArgSelector(Arrays.asList("create", "selectiontool", "delete", "help", "setalias", "movemarker"), 0));
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {

        CommandSender sender = stack.getSender();

        // If there are no arguments return.
        if (args.length == 0) {
            sender.sendMessage(ChatUtils.error("/plotsystem help"));
            return;
        }

        switch (args[0]) {

            case "selectiontool" -> selectionTool(sender);
            case "create" -> {
                CreateCommand createCommand = new CreateCommand(globalSQL, plotSQL);
                createCommand.create(sender, args);
            }
            case "delete" -> {
                DeleteCommand deleteCommand = new DeleteCommand(globalSQL, plotSQL);
                deleteCommand.delete(sender, args);
            }
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

        if (plotSQL.hasRow("SELECT name FROM location_data WHERE name='" + location + "';")) {

            plotSQL.update("UPDATE location_data SET alias='" + alias.replace("'", "\\'") + "' WHERE name='" + location + "';");
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
            int coordinate_id = Network.getInstance().getPlotSQL().getInt("SELECT coordinate_id FROM plot_data WHERE id=" + plot + ";");
            Location l = p.getLocation().clone();
            l.setY(l.getY() + 2);

            if (coordinate_id == 0) {
                // Create a new coordinate id and add it to the plot data.
                coordinate_id = Network.getInstance().getGlobalSQL().addCoordinate(l);
                Network.getInstance().getPlotSQL().update("UPDATE plot_data SET coordinate_id=" + coordinate_id + " WHERE id=" + plot + ";");
                // Add the hologram.
                PlotHelper.addPlotHologram(new PlotHologram(plot));
                p.sendMessage(ChatUtils.success("Added marker to plot " + plot));
            } else {
                // Update the existing coordinate location.
                Network.getInstance().getGlobalSQL().updateCoordinate(coordinate_id, l);
                // Update the hologram.
                PlotHelper.updatePlotHologram(plot);
                p.sendMessage(ChatUtils.success("Moved marker of plot " + plot));
            }

        } else {
            sender.sendMessage(ChatUtils.error("You must be a player to use this command."));
        }
    }
}
