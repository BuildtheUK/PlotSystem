package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.Utils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ReviewHotbar implements Listener {

    // PlotSystem instance.
    private final PlotSystem plotSystem;

    // User.
    private final User user;

    // Review gui item.
    private final ItemStack reviewGuiItem;

    private final List<ItemStack> requiredItems = new ArrayList<>();

    public ReviewHotbar(PlotSystem plotSystem, User user) {

        //Set plotsystem.
        this.plotSystem = plotSystem;

        //Set user.
        this.user = user;

        //Create review gui item.
        reviewGuiItem = Utils.createItem(Material.EMERALD, 1, ChatUtils.title("Review Menu"), ChatUtils.line("Click to open review menu."));
        initReviewItems();

        //Register listeners.
        Bukkit.getServer().getPluginManager().registerEvents(this, plotSystem);

    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        e.setCancelled(cancelEvent(e.getWhoClicked(), e.getCurrentItem()) || cancelEvent(e.getWhoClicked(), e.getCursor()));

        // If item is review gui then open the gui.
        if (reviewGuiItem.equals(e.getCurrentItem())) {
            Bukkit.getScheduler().runTaskLater(plotSystem, () -> user.getReview().openReviewGui(), 1);
        }
    }

    @EventHandler
    public void interactEvent(PlayerInteractEvent e) {
        e.setCancelled(cancelEvent(e.getPlayer(), e.getItem()));

        //If item is review gui then open the gui.
        if (reviewGuiItem.equals(e.getItem())) {
            Bukkit.getScheduler().runTaskLater(plotSystem, () -> user.getReview().openReviewGui(), 1);
        }
    }

    public void unregister() {
        //Unregister listeners.
        PlayerEditBookEvent.getHandlerList().unregister(this);
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryDragEvent.getHandlerList().unregister(this);
        InventoryMoveItemEvent.getHandlerList().unregister(this);
        PlayerInteractEvent.getHandlerList().unregister(this);
        PlayerDropItemEvent.getHandlerList().unregister(this);
        PlayerSwapHandItemsEvent.getHandlerList().unregister(this);

        //Send feedback in the console.
        PlotSystem.LOGGER.info("Reset reviewing hotbar and unregistered listeners");
    }

    @EventHandler
    public void swapHands(PlayerSwapHandItemsEvent e) {
        e.setCancelled(cancelEvent(e.getPlayer(), e.getOffHandItem()) || cancelEvent(e.getPlayer(), e.getMainHandItem()));
    }

    @EventHandler
    public void dropItem(PlayerDropItemEvent e) {
        e.setCancelled(cancelEvent(e.getPlayer(), e.getItemDrop().getItemStack()));
    }

    @EventHandler
    public void moveItem(InventoryMoveItemEvent e) {
        if (e.getInitiator().getHolder() instanceof Player player) {
            e.setCancelled(cancelEvent(player, e.getItem()));
        }
    }

    @EventHandler
    public void dragItem(InventoryDragEvent e) {
        e.setCancelled(cancelEvent(e.getWhoClicked(), e.getOldCursor()) || cancelEvent(e.getWhoClicked(), e.getCursor()));
    }

    public boolean cancelEvent(HumanEntity humanEntity, ItemStack item) {
        // Check if player is the reviewer and the item is one of the required items.
        return item != null && (user.player.equals(humanEntity) && requiredItems.stream().anyMatch(item::equals));
    }

    private void initReviewItems() {
        requiredItems.add(reviewGuiItem);
        requiredItems.add(user.getReview().getFeedbackBook().getEditableBook());

        // Set the hotbar items in the player's inventory.
        user.player.getInventory().setItem(0, reviewGuiItem);
        user.player.getInventory().setItem(1, user.getReview().getFeedbackBook().getEditableBook());
        user.player.getInventory().setItem(2, new ItemStack(Material.WOODEN_AXE));
        user.player.getInventory().setItem(3, new ItemStack(Material.ORANGE_CONCRETE));
    }
}
