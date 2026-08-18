package net.bteuk.plotsystem.commands;

import net.bteuk.network.api.EventAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.papercore.WorldUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import org.btuk.minecraft.component.ComponentUtils;
import org.btuk.network.lib.utils.ChatUtils;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.bteuk.plotsystem.PlotSystem.SERVER_NAME;

public class ResetCommand {

    private static final Component USAGE = ComponentUtils.error("/plotsystem reset plot");

    private final Map<UUID, Integer> plotsToReset = new HashMap<>();

    private final Map<UUID, Integer> resetTimeouts = new HashMap<>();

    private final PlotSystem plotSystem;

    private final PlotAPI plotAPI;

    private final EventAPI eventAPI;

    public ResetCommand(PlotSystem plotSystem, PlotAPI plotAPI, EventAPI eventAPI) {
        this.plotSystem = plotSystem;
        this.plotAPI = plotAPI;
        this.eventAPI = eventAPI;
    }

    public void reset(CommandSender sender, String[] args) {

        if (args.length < 2) {
            sender.sendMessage(USAGE);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "plot" -> resetPlot(sender, args);
            case "confirm" -> confirmReset(sender);
            default -> sender.sendMessage(USAGE);
        }
    }

    private void resetPlot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ComponentUtils.error("This command can only be used by players!"));
            return;
        }

        if (!(player.hasPermission("uknet.plots.reset.plot"))) {
            player.sendMessage(ChatUtils.error("You do not have permission to use this command."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(USAGE);
            return;
        }

        int plotID;

        if (args.length >= 3) {
            try {
                plotID = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatUtils.error("/plotsystem reset plot <plotID>"));
                return;
            }
        } else {
            // Get plot that the player is standing in.
            User u = PlotSystem.getInstance().getUser(player);
            if (u.inPlot == 0) {
                player.sendMessage(ChatUtils.error("You are not standing in a plot."));
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

        // Check if the plot is claimed.
        if (!(plotAPI.isPlotClaimed(plotID))) {
            sender.sendMessage(ChatUtils.error("This plot is not claimed, you can only reset claimed plots."));
            return;
        }

        // Get world of plot.
        World world = WorldUtils.getWorld(plotAPI.getPlotLocation(plotID));

        // If world is null then the plot is not on this server.
        if (world == null) {
            sender.sendMessage(ChatUtils.error("The plot is not on this server."));
            return;
        }

        plotsToReset.put(player.getUniqueId(), plotID);
        sender.sendMessage(ComponentUtils.success("To confirm the reset, type %s within 30 seconds.", "/plotsystem reset confirm"));
        addTimeout(player.getUniqueId());
    }

    private void confirmReset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ComponentUtils.error("This command can only be used by players!"));
            return;
        }

        if (!(player.hasPermission("uknet.plots.reset.plot"))) {
            player.sendMessage(ChatUtils.error("You do not have permission to use this command."));
            return;
        }

        Integer plotID = plotsToReset.remove(player.getUniqueId());
        if (plotID == null) {
            player.sendMessage(ChatUtils.error("You have not requested a plot to be reset."));
            return;
        }
        Integer timeoutTask = resetTimeouts.remove(player.getUniqueId());
        cancelTimeout(timeoutTask);

        resetPlot(player, plotID);
    }

    private void resetPlot(Player player, int plotID) {
        eventAPI.createEvent(player.getUniqueId().toString(), SERVER_NAME, "delete plot " + plotID);
    }

    private void addTimeout(UUID playerUuid) {
        int taskId = plotSystem.getServer().getScheduler().scheduleSyncDelayedTask(plotSystem, () -> {
            plotsToReset.remove(playerUuid);
            resetTimeouts.remove(playerUuid);
        }, 600L /* 30 seconds */);
        Integer timeoutTask = resetTimeouts.put(playerUuid, taskId);
        cancelTimeout(timeoutTask);
    }

    private void cancelTimeout(Integer timeoutTask) {
        if (timeoutTask != null) {
            plotSystem.getServer().getScheduler().cancelTask(timeoutTask);
        }
    }
}
