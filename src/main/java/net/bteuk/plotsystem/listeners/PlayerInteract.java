package net.bteuk.plotsystem.listeners;

import net.bteuk.network.api.PlotAPI;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.exceptions.RegionManagerNotFoundException;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.plugins.WorldGuardFunctions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.btuk.network.lib.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

public class PlayerInteract implements Listener {

    private final PlotAPI plotAPI;

    public PlayerInteract(PlotSystem instance, PlotAPI plotAPI) {
        this.plotAPI = plotAPI;

        // Register the listener.
        Bukkit.getServer().getPluginManager().registerEvents(this, instance);
    }

    @EventHandler
    public void interactEvent(PlayerInteractEvent e) {

        User u = PlotSystem.getInstance().getUser(e.getPlayer());

        if (e.getPlayer().getOpenInventory().getType() != InventoryType.CRAFTING && e.getPlayer().getOpenInventory().getType() != InventoryType.CREATIVE) {
            return;
        }

        // Selection tool
        if (u.player.getInventory().getItemInMainHand().equals(PlotSystem.selectionTool)) {

            // You must have this permission to use the plot selection tool.
            // uknet = uknet plugins, plots = plotserver plugin, select = selection tool.
            if (!u.player.hasPermission("uknet.plots.select")) {

                e.setCancelled(true);
                u.player.sendMessage(ChatUtils.error("You do not have permission to use this tool!"));
                return;

            }

            // If the player left clicks this will (re)start the selection with the first point.
            if (e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {

                e.setCancelled(true);

                // Check if they are in a world where plots are allowed to be created.
                if (!plotAPI.hasLocation(Objects.requireNonNull(e.getClickedBlock()).getWorld().key().asMinimalString())) {
                    u.player.sendMessage(ChatUtils.error("You can't create plots in this world!"));
                    return;
                }

                // If the selected point is in an existing plot, cancel.
                try {
                    if (WorldGuardFunctions.inRegion(e.getClickedBlock())) {

                        u.player.sendMessage(ChatUtils.error("This point is in another plot!"));
                        return;

                    }
                } catch (RegionManagerNotFoundException ex) {
                    u.player.sendMessage(ChatUtils.error("An error occurred while processing the left click action, please contact an admin."));
                    ex.printStackTrace();
                    return;
                }

                // Passed the checks, start a new selection at the clicked block.
                u.selectionTool.startSelection(e.getClickedBlock(), e.getClickedBlock().getWorld().key().asMinimalString());
                u.player.sendMessage(ChatUtils.success("Started a new selection at ")
                        .append(Component.text(e.getClickedBlock().getX(), NamedTextColor.DARK_AQUA))
                        .append(ChatUtils.success(", "))
                        .append(Component.text(e.getClickedBlock().getZ(), NamedTextColor.DARK_AQUA)));

                // If the player right clicks then add a point to the existing selection.
            } else if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && Objects.equals(e.getHand(), EquipmentSlot.HAND)) {

                e.setCancelled(true);

                // If the player hasn't selected their first point cancel.
                if (u.selectionTool.size() == 0) {

                    u.player.sendMessage(ChatUtils.error("You must first start your selection by left-clicking."));
                    return;

                }

                // Check if they are making their plot in the same world as their first point.
                if (!Objects.requireNonNull(e.getClickedBlock()).getWorld().equals(u.selectionTool.world())) {

                    u.player.sendMessage(ChatUtils.error("You already started a selection in a different world, please create a new selection first."));
                    return;

                }

                // If the selected point is in an existing plot cancel.
                try {
                    if (WorldGuardFunctions.inRegion(e.getClickedBlock())) {

                        u.player.sendMessage(ChatUtils.error("This point is in another plot!"));
                        return;

                    }
                } catch (RegionManagerNotFoundException ex) {
                    u.player.sendMessage(ChatUtils.error("An error occurred while processing the right click action, please contact an admin."));
                    ex.printStackTrace();
                    return;
                }

                if (!u.selectionTool.addPoint(e.getClickedBlock())) {

                    return;

                }

                u.player.sendMessage(ChatUtils.success("Added point at ")
                        .append(Component.text(e.getClickedBlock().getX(), NamedTextColor.DARK_AQUA))
                        .append(ChatUtils.success(", "))
                        .append(Component.text(e.getClickedBlock().getZ(), NamedTextColor.DARK_AQUA)));

            }
        }
    }
}