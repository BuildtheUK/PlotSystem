package net.bteuk.plotsystem.events;

import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Time;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class JoinEvent {

    public static void event(String uuid, String[] event) {

        // Events for retracting
        if (event[1].equals("plot")) {

            // Get the user.
            Player p = Bukkit.getPlayer(UUID.fromString(uuid));

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            Component message = ChatUtils.success("You have joined Plot %s", String.valueOf(id));

            PlotSQL plotSQL = Network.getInstance().getPlotSQL();

            // Check if you have not already reached the maximum number of plots.
            if (plotSQL.getInt("SELECT count(id) FROM plot_members WHERE uuid='" + uuid + "';") >= PlotSystem.getInstance().getConfig().getInt("plot_maximum")) {

                message = ChatUtils.error("You have reached the maximum number of plots.");

            } else {

                // Add the player to the database.
                plotSQL.update("INSERT INTO plot_members(id,uuid,is_owner,last_enter) VALUES(" + id + ",'" + uuid + "',0," + Time.currentTime() + ");");

                // Add the player to the worldguard region.
                try {
                    WorldGuardFunctions.addMember(String.valueOf(id), uuid, Bukkit.getWorld(plotSQL.getString("SELECT location FROM plot_data WHERE id=" + id + ";")));
                } catch (RegionManagerNotFoundException | RegionNotFoundException e) {

                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.error("An error occurred while adding you to the plot, please contact an administrator."), false);
                    Network.getInstance().getChat().sendSocketMesage(directMessage);
                    return;

                }

                // Send a message to the plot owner.
                DirectMessage directMessage = new DirectMessage("global", plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + id + " AND is_owner=1;"), "server",
                        ChatUtils.success("%s has joined your plot %s", Network.getInstance().getGlobalSQL().getString("SELECT name FROM player_data WHERE uuid='" + uuid + "';"),
                                String.valueOf(id)), false);
                Network.getInstance().getChat().sendSocketMesage(directMessage);

                // If the player is on the server, update the hologram.
                if (p != null) {
                    PlotHelper.updatePlotHologram(id);
                }

            }

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, false);
            Network.getInstance().getChat().sendSocketMesage(directMessage);

        } else if (event[1].equals("zone")) {

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            Component message = ChatUtils.success("You have joined Zone %s", String.valueOf(id));

            PlotSQL plotSQL = Network.getInstance().getPlotSQL();

            // Add the player to the database.
            plotSQL.update("INSERT INTO zone_members(id,uuid,is_owner) VALUES(" + id + ",'" + uuid + "',0);");

            // Add the player to the worldguard region.
            try {
                WorldGuardFunctions.addMember("z" + id, uuid, Bukkit.getWorld(plotSQL.getString("SELECT location FROM zones WHERE id=" + id + ";")));
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("An error occurred while adding you to the zone, please contact an administrator."), false);
                Network.getInstance().getChat().sendSocketMesage(directMessage);
                return;
            }

            // Send a message to the zone owner.
            DirectMessage ownerMessage = new DirectMessage("global", plotSQL.getString("SELECT uuid FROM zone_members WHERE id=" + id + " AND is_owner=1;"), "server",
                    ChatUtils.success("%s has joined your Zone %s", Network.getInstance().getGlobalSQL().getString("SELECT name FROM player_data WHERE uuid='" + uuid + "';"),
                            String.valueOf(id)), true);
            Network.getInstance().getChat().sendSocketMesage(ownerMessage);

            DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                    message, false);
            Network.getInstance().getChat().sendSocketMesage(directMessage);

        }
    }
}
