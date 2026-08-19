package net.bteuk.plotsystem.events;

import com.sk89q.worldedit.math.BlockVector2;
import net.bteuk.network.api.ChatAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.entity.Event;
import net.bteuk.network.papercore.WorldUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.exceptions.RegionNotFoundException;
import net.bteuk.plotsystem.utils.plugins.WorldEditor;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import org.btuk.network.lib.dto.DirectMessage;
import org.btuk.network.lib.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class CloseEvent implements Event {

    private final PlotAPI plotAPI;

    private final ChatAPI chatAPI;

    public CloseEvent(PlotAPI plotAPI, ChatAPI chatAPI) {
        this.plotAPI = plotAPI;
        this.chatAPI = chatAPI;
    }

    public void event(String uuid, String[] event, String message) {

        // Event for save and closing.
        if (event[1].equals("zone")) {

            // PlotSQL
            FileConfiguration config = PlotSystem.getInstance().getConfig();

            // Convert the string id to int id.
            int zone = Integer.parseInt(event[2]);

            // Get zone location.
            String location = plotAPI.getZoneLocation(zone);

            // Check if the zone is on this server.
            String zoneServer = plotAPI.getLocationServer(location);
            if (PlotSystem.SERVER_NAME.equals(zoneServer)) {

                // Get worlds of plot and save location.
                String save_world = config.getString("save_world_dimension");
                if (save_world == null) {
                    LOGGER.warning("Save World is not defined in config, plot delete event has therefore failed!");
                    return;
                }

                World copyWorld = WorldUtils.getWorld(location);
                World pasteWorld = WorldUtils.getWorld(save_world);

                assert (copyWorld != null);

                int minusXTransform = -plotAPI.getXTransform(location);
                int minusZTransform = -plotAPI.getZTransform(location);

                // Get the zone bounds.
                List<BlockVector2> copyVector;
                try {
                    copyVector = WorldGuardFunctions.getPoints("z" + zone, copyWorld);
                } catch (RegionManagerNotFoundException | RegionNotFoundException e) {
                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.error("An error occurred while closing the zone, please contact an administrator."), false);
                    chatAPI.sendDirectMessage(directMessage);
                    return;
                }

                // Create the copyVector by transforming the points in the paste vector with the negative transform.
                // The negative transform is used because the coordinates by default are transformed from the save to the paste world, which in this case it reversed.
                List<BlockVector2> pasteVector = new ArrayList<>();
                for (BlockVector2 bv : copyVector) {
                    pasteVector.add(BlockVector2.at(bv.x() + minusXTransform, bv.z() + minusZTransform));
                }

                Bukkit.getScheduler().runTaskAsynchronously(PlotSystem.getInstance(), () -> {
                    // Save the zone by copying from the building world to the save world.
                    WorldEditor.updateWorld(copyVector, pasteVector, copyWorld, pasteWorld);

                    // Delete the worldguard region.
                    try {
                        WorldGuardFunctions.delete("z" + zone, copyWorld);
                    } catch (RegionManagerNotFoundException e) {
                        DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                                ChatUtils.error("An error occurred while closing the zone, please contact an administrator."), false);
                        chatAPI.sendDirectMessage(directMessage);
                        return;
                    }

                    // Remove all members of zone in database.
                    plotAPI.clearZoneMembers(zone);

                    // Set the zone status to closed.
                    plotAPI.setZoneStatus(zone, "closed");

                    // Add a message for the zone owner to the database to notify them that their zone was closed.
                    DirectMessage directMessage = new DirectMessage("global", uuid, "server",
                            ChatUtils.success("Closed zone %s, its content has been saved.", String.valueOf(zone)), true);
                    chatAPI.sendDirectMessage(directMessage);

                    // Log plot removal to console.
                    LOGGER.info("Zone " + zone + " has been closed.");
                });
            }
        }
    }
}
