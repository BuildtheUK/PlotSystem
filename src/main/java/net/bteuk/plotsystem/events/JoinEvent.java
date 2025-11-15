package net.bteuk.plotsystem.events;

import net.bteuk.network.api.ChatAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.SQLAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class JoinEvent implements Event {

    private final PlotAPI plotAPI;

    private final PlotHelper plotHelper;

    private final ChatAPI chatAPI;

    private final SQLAPI globalAPI;

    public JoinEvent(PlotAPI plotAPI, PlotHelper plotHelper, ChatAPI chatAPI, SQLAPI globalAPI) {
        this.plotAPI = plotAPI;
        this.plotHelper = plotHelper;
        this.chatAPI = chatAPI;
        this.globalAPI = globalAPI;
    }

    public void event(String uuid, String[] event, String sMessage) {

        // Events for retracting
        if (event[1].equals("plot")) {

            // Get the user.
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            Component message = ChatUtils.success("You have joined Plot %s", String.valueOf(id));

            // Check if you have not already reached the maximum number of plots.
            if (plotAPI.getNumberOfPlots(uuid) >= PlotSystem.getInstance().getConfig().getInt("plot_maximum")) {

                message = ChatUtils.error("You have reached the maximum number of plots.");

            } else {

                // Add the player to the database.
                plotAPI.createPlotMember(id, uuid);

                // Add the player to the worldguard region.
                try {
                    WorldGuardFunctions.addMember(String.valueOf(id), uuid, Bukkit.getWorld(plotAPI.getPlotLocation(id)));
                } catch (RegionManagerNotFoundException | RegionNotFoundException e) {

                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.error("An error occurred while adding you to the plot, please contact an administrator."), false);
                    chatAPI.sendDirectMessage(directMessage);
                    return;

                }

                // Send a message to the plot owner.
                DirectMessage directMessage = new DirectMessage("global", plotAPI.getPlotOwner(id), "server",
                        ChatUtils.success("%s has joined your plot %s", globalAPI.getString("SELECT name FROM player_data WHERE uuid='" + uuid + "';"),
                                String.valueOf(id)), false);
                chatAPI.sendDirectMessage(directMessage);

                // If the player is on the server, update the hologram.
                if (p != null) {
                    plotHelper.updatePlotHologram(id);
                }

            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, false);
            chatAPI.sendDirectMessage(directMessage);

        } else if (event[1].equals("zone")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            Component message = ChatUtils.success("You have joined Zone %s", String.valueOf(id));

            // Add the player to the database.
            plotAPI.createZoneMember(id, uuid);

            // Add the player to the worldguard region.
            try {
                WorldGuardFunctions.addMember("z" + id, uuid, Bukkit.getWorld(plotAPI.getZoneLocation(id)));
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("An error occurred while adding you to the zone, please contact an administrator."), false);
                chatAPI.sendDirectMessage(directMessage);
                return;
            }

            // Send a message to the zone owner.
            DirectMessage ownerMessage = new DirectMessage("global", plotAPI.getZoneOwner(id), "server",
                    ChatUtils.success("%s has joined your Zone %s", globalAPI.getString("SELECT name FROM player_data WHERE uuid='" + uuid + "';"),
                            String.valueOf(id)), true);
            chatAPI.sendDirectMessage(ownerMessage);

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, false);
            chatAPI.sendDirectMessage(directMessage);

        }
    }
}
