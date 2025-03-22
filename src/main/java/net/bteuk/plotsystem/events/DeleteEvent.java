package net.bteuk.plotsystem.events;

import com.sk89q.worldedit.math.BlockVector2;
import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.plugins.WorldEditor;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class DeleteEvent {

    public static void event(String uuid, String[] event) {

        //Events for deleting
        if (event[1].equals("plot")) {

            //PlotSQL
            PlotSQL plotSQL = Network.getInstance().getPlotSQL();

            //Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            //Get location which is the world.
            String location = plotSQL.getString("SELECT location FROM plot_data WHERE id=" + id + ";");

            //Get worlds of plot and save location.
            String save_world = PlotSystem.getInstance().getConfig().getString("save_world");
            if (save_world == null) {
                LOGGER.warning("Save World is not defined in config, plot delete event has therefore failed!");
                return;
            }

            World copyWorld = Bukkit.getWorld(save_world);
            //Location name is the same as the world name.
            World pasteWorld = Bukkit.getWorld(location);

            if (copyWorld == null || pasteWorld == null) {

                //Send error to console.
                LOGGER.severe("Plot delete event failed!");
                LOGGER.severe("Event details:" + Arrays.toString(event));
                return;

            }

            int minusXTransform = -plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");
            int minusZTransform = -plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");

            //Get the plot bounds.
            List<BlockVector2> pasteVector;
            try {
                pasteVector = WorldGuardFunctions.getPoints(String.valueOf(id), pasteWorld);
            } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("An error occurred while deleting the plot, please contact an administrator."), false);
                Network.getInstance().getChat().sendSocketMesage(directMessage);
                return;
            }

            //Create the copyVector by transforming the points in the paste vector with the negative transform.
            //The negative transform is used because the coordinates by default are transformed from the save to the paste world, which in this case it reversed.
            List<BlockVector2> copyVector = new ArrayList<>();
            for (BlockVector2 bv : pasteVector) {
                copyVector.add(BlockVector2.at(bv.getX() + minusXTransform, bv.getZ() + minusZTransform));
            }

            //Revert plot to original state.
            Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
                WorldEditor.updateWorld(copyVector, pasteVector, copyWorld, pasteWorld);

                //Remove all members from the worldguard plot.
                try {
                    WorldGuardFunctions.clearMembers(event[2], pasteWorld);
                } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.error("An error occurred while deleting the plot, please contact an administrator."), false);
                    Network.getInstance().getChat().sendSocketMesage(directMessage);
                    return;
                }

                // Remove all members of plot in database.
                plotSQL.update("DELETE FROM plot_members WHERE id=" + id + ";");

                // Remove the submitted plot if it is currently submitted.
                plotSQL.update("DELETE FROM plot_submission WHERE plot_id=" + id + ";");

                //Set plot status to unclaimed.
                PlotStatus currentStatus = PlotStatus.fromDatabaseValue(plotSQL.getString("SELECT status FROM plot_data WHERE id=" + id + ";"));
                PlotHelper.updatePlotStatus(id, PlotStatus.UNCLAIMED);

                //Send message to plot owner.
                Player p = Bukkit.getPlayer(UUID.fromString(uuid));

                //If the player is on this server send them a message.
                if (p != null) {

                    p.sendMessage(ChatUtils.success("Plot ")
                            .append(Component.text(id, NamedTextColor.DARK_AQUA))
                            .append(ChatUtils.success(" deleted")));

                } else {

                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.error("Plot %s deleted", String.valueOf(id)), true);
                    Network.getInstance().getChat().sendSocketMesage(directMessage);
                }

                // If the plot was submitted, before deleting, send a message to reviewers letting them know it's no longer submitted.
                if (currentStatus == PlotStatus.SUBMITTED) {
                    //Send message to reviewers that a plot submission has been deleted.
                    PlotMessage plotMessage = new PlotMessage("A submitted plot has been deleted, there %s %s submitted %s.", false);
                    Network.getInstance().getChat().sendSocketMesage(plotMessage);
                }
            });
        } else if (event[1].equals("zone")) {

            // PlotSQL
            PlotSQL plotSQL = Network.getInstance().getPlotSQL();

            // Convert the string id to int id.
            int id = Integer.parseInt(event[2]);

            // Get location which is the world.
            String location = plotSQL.getString("SELECT location FROM zones WHERE id=" + id + ";");

            // Get worlds of plot and save location.
            String save_world = PlotSystem.getInstance().getConfig().getString("save_world");
            if (save_world == null) {
                LOGGER.warning("Save World is not defined in config, plot delete event has therefore failed!");
                return;
            }

            World copyWorld = Bukkit.getWorld(save_world);
            // Location name is the same as the world name.
            World pasteWorld = Bukkit.getWorld(location);

            if (copyWorld == null || pasteWorld == null) {

                //Send error to console.
                LOGGER.severe("Zone delete event failed due to the copy or paste-world being null!");
                return;

            }

            int minusXTransform = -plotSQL.getInt("SELECT xTransform FROM location_data WHERE name='" + location + "';");
            int minusZTransform = -plotSQL.getInt("SELECT zTransform FROM location_data WHERE name='" + location + "';");

            // Get the zone bounds.
            List<BlockVector2> pasteVector;
            try {
                pasteVector = WorldGuardFunctions.getPoints("z" + event[2], pasteWorld);
            } catch (RegionNotFoundException | RegionManagerNotFoundException e) {
                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("An error occurred while deleting the zone, please contact an administrator."), false);
                Network.getInstance().getChat().sendSocketMesage(directMessage);
                return;
            }

            if (pasteVector == null) {
                return;
            }

            // Create the copyVector by transforming the points in the paste vector with the negative transform.
            // The negative transform is used because the coordinates by default are transformed from the save to the paste world, which in this case it reversed.
            List<BlockVector2> copyVector = new ArrayList<>();
            for (BlockVector2 bv : pasteVector) {
                copyVector.add(BlockVector2.at(bv.getX() + minusXTransform, bv.getZ() + minusZTransform));
            }

            // Revert zone to original state.
            Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
                WorldEditor.updateWorld(copyVector, pasteVector, copyWorld, pasteWorld);

                //Remove the zone from worldguard.
                try {
                    WorldGuardFunctions.delete("z" + event[2], pasteWorld);
                } catch (RegionManagerNotFoundException e) {
                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.error("An error occurred while deleting the zone, please contact an administrator."), false);
                    Network.getInstance().getChat().sendSocketMesage(directMessage);
                    return;
                }

                //Remove all members of zone in database.
                plotSQL.update("DELETE FROM zone_members WHERE id=" + id + ";");

                //Set zone status to closed.
                plotSQL.update("UPDATE zones SET status='closed' WHERE id=" + id + ";");

                DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                        ChatUtils.error("Zone %s deleted", String.valueOf(id)), true);
                Network.getInstance().getChat().sendSocketMesage(directMessage);
            });
        }
    }
}
