package net.bteuk.plotsystem.events;

import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

public class KickEvent {

    public static void event(String uuid, String[] event) {

        // Events for retracting
        if (event[1].equals("plot")) {

            GlobalSQL globalSQL = Network.getInstance().getGlobalSQL();
            PlotSQL plotSQL = Network.getInstance().getPlotSQL();

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            String ownerId = plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + id + " AND is_owner=1");

            Component messageOwner = ChatUtils.success("You have kicked %s from plot %s", globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + uuid + "';"),
                    String.valueOf(id));
            Component messageMember = ChatUtils.error("You have been kicked from Plot %s", String.valueOf(id));

            // Remove the player to the database.
            plotSQL.update("DELETE FROM plot_members WHERE id=" + id + " AND uuid='" + uuid + "';");

            // Remove the player to the worldguard region.
            try {
                WorldGuardFunctions.removeMember(String.valueOf(id), uuid, Bukkit.getWorld(plotSQL.getString("SELECT location FROM plot_data WHERE id=" + id + ";")));
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", ownerId, "server",
                        ChatUtils.error("An error occurred while trying to kick the user from the plot, please contact an administrator."), false);
                Network.getInstance().getChat().sendSocketMesage(directMessage);
                return;
            }

            // Send message to plot owner.
            DirectMessage ownerMessage = new DirectMessage("global", ownerId, "server",
                    messageOwner, true);
            Network.getInstance().getChat().sendSocketMesage(ownerMessage);

            // Send message to plot member.
            DirectMessage memberMessage = new DirectMessage("global", uuid, "server",
                    messageMember, true);
            Network.getInstance().getChat().sendSocketMesage(memberMessage);

        } else if (event[1].equals("zone")) {

            GlobalSQL globalSQL = Network.getInstance().getGlobalSQL();
            PlotSQL plotSQL = Network.getInstance().getPlotSQL();

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            String ownerId = plotSQL.getString("SELECT uuid FROM zone_members WHERE id=" + id + " AND is_owner=1");

            Component messageOwner = ChatUtils.success("You have kicked %s from Zone %s", globalSQL.getString("SELECT name FROM player_data WHERE uuid='" + uuid + "';"),
                    String.valueOf(id));
            Component messageMember = ChatUtils.error("You have been kicked from Zone %s", String.valueOf(id));

            // Remove the player to the database.
            plotSQL.update("DELETE FROM zone_members WHERE id=" + id + " AND uuid='" + uuid + "';");

            // Remove the player to the worldguard region.
            try {
                WorldGuardFunctions.removeMember("z" + event[2], uuid, Bukkit.getWorld(plotSQL.getString("SELECT location FROM zones WHERE id=" + id + ";")));
            } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", ownerId, "server",
                        ChatUtils.error("An error occurred while trying to kick the user from the zone, please contact an administrator."), false);
                Network.getInstance().getChat().sendSocketMesage(directMessage);
                return;
            }

            // Send message to zone owner.
            DirectMessage ownerMessage = new DirectMessage("global", ownerId, "server",
                    messageOwner, true);
            Network.getInstance().getChat().sendSocketMesage(ownerMessage);

            // Send message to zone member.
            DirectMessage memberMessage = new DirectMessage("global", uuid, "server",
                    messageMember, true);
            Network.getInstance().getChat().sendSocketMesage(memberMessage);
        }
    }

}
