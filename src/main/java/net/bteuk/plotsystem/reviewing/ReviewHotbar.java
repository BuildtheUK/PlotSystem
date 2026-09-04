package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.api.TimerAPI;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.Utils;
import org.btuk.network.lib.utils.ChatUtils;
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

import java.util.HashMap;
import java.util.Map;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;

public class ReviewHotbar implements Listener {

    // PlotSystem instance.
    private final PlotSystem instance;

    // User.
    private final User user;

    // Review gui item.
    private final ItemStack reviewGui;

    private final Map<Integer, ItemStack> requiredItems = new HashMap<>();

    private final TimerAPI timerApi;
    private final int timerId;

    public ReviewHotbar(PlotSystem instance, User user, TimerAPI timerApi) {

        // Set plotsystem.
        this.instance = instance;

        // Set user.
        this.user = user;

        // Set timer api.
        this.timerApi = timerApi;

        // Create the review gui item.
        reviewGui = Utils.createItem(Material.EMERALD, 1, ChatUtils.title("Review Menu"), ChatUtils.line("Click to open review menu."));

        initReviewItems();

        // Register listeners.
        Bukkit.getServer().getPluginManager().registerEvents(this, instance);

        // Register a timer to check if the required items are still in the inventory.
        timerId = timerApi.registerTimer(() -> {
            for (Map.Entry<Integer, ItemStack> entry : requiredItems.entrySet()) {
                if (!entry.getValue().equals(user.player.getInventory().getItem(entry.getKey()))) {
                    user.player.getInventory().setItem(entry.getKey(), entry.getValue());
                }
            }
        }, 1000L);

    }

    public void setReviewBookSlot(ItemStack itemStack) {
        requiredItems.put(1, itemStack);
        user.player.getInventory().setItem(1, itemStack);
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

        // Unregister timer.
        timerApi.cancelTimer(timerId);

        //Send feedback in the console.
        LOGGER.info("Reset reviewing hotbar and unregistered listeners");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (cancelEvent(e.getWhoClicked(), e.getCurrentItem()) || cancelEvent(e.getWhoClicked(), e.getCursor())) {
            e.setCancelled(true);

            // If item is review gui then open the gui.
            if (reviewGui.equals(e.getCurrentItem())) {
                Bukkit.getScheduler().runTaskLater(instance, () -> user.getReview().openReviewActionGui(), 1);
            }
        }
    }

    @EventHandler
    public void interactEvent(PlayerInteractEvent e) {
        if (cancelEvent(e.getPlayer(), e.getItem())) {
            e.setCancelled(true);

            // If item is review gui then open the gui.
            if (reviewGui.equals(e.getItem())) {
                Bukkit.getScheduler().runTaskLater(instance, () -> user.getReview().openReviewActionGui(), 1);
            }
        }
    }

    @EventHandler
    public void swapHands(PlayerSwapHandItemsEvent e) {
        if (cancelEvent(e.getPlayer(), e.getOffHandItem()) || cancelEvent(e.getPlayer(), e.getMainHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void dropItem(PlayerDropItemEvent e) {
        if (cancelEvent(e.getPlayer(), e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void moveItem(InventoryMoveItemEvent e) {
        if (e.getInitiator().getHolder() instanceof Player player && cancelEvent(player, e.getItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void dragItem(InventoryDragEvent e) {
        if (cancelEvent(e.getWhoClicked(), e.getOldCursor()) || cancelEvent(e.getWhoClicked(), e.getCursor())) {
            e.setCancelled(true);
        }
    }

    private boolean cancelEvent(HumanEntity humanEntity, ItemStack item) {
        // Check if player is the reviewer and the item is one of the required items.
        return item != null && (user.player.equals(humanEntity) && requiredItems.containsValue(item));
    }

    private void initReviewItems() {
        requiredItems.put(0, reviewGui);

        // Set the hotbar items in the player's inventory.
        user.player.getInventory().setItem(0, reviewGui);

        user.player.getInventory().setItem(2, new ItemStack(Material.ORANGE_CONCRETE));
        user.player.getInventory().setItem(3, new ItemStack(Material.SPRUCE_SIGN));
    }
}
