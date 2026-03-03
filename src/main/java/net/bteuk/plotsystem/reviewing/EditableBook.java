package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.plotsystem.PlotSystem;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.WritableBookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class EditableBook implements Listener {

    private static final NamespacedKey EDITABLE_BOOK_ID_KEY =
            new NamespacedKey(PlotSystem.getInstance(), "editable_book_id");

    private final Player bookEditor;

    @Getter
    private final ItemStack editableBook;

    private final BookResetAction bookResetAction;

    private final WritableBookMeta editableBookData;

    private final UUID editableBookId;

    @Getter
    private boolean edited;

    public EditableBook(PlotSystem instance, Player bookEditor, ItemStack editableBook, BookResetAction bookResetAction) {

        if (editableBook.getType() != Material.WRITABLE_BOOK) {
            throw new IllegalArgumentException("ItemStack must be a WRITABLE_BOOK");
        }

        // Register listener.
        Bukkit.getServer().getPluginManager().registerEvents(this, instance);

        this.bookEditor = bookEditor;
        this.editableBook = editableBook;
        this.bookResetAction = bookResetAction;
        this.editableBookData = (WritableBookMeta) editableBook.getItemMeta();

        // Add the random uuid as obfuscated lore text as a unique identifier.
        this.editableBookId = UUID.randomUUID();
        this.editableBookData.getPersistentDataContainer().set(EDITABLE_BOOK_ID_KEY, PersistentDataType.STRING, editableBookId.toString());

        editableBook.setItemMeta(editableBookData);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    public List<String> getBookPages() {
        return editableBookData.getPages().stream().map(String::trim).collect(Collectors.toList());
    }

    public boolean hasContent() {
        return !StringUtils.isEmpty(String.join("", getBookPages()));
    }

    public boolean hasChanged(List<String> newPages) {
        String previousBookContent = String.join("", getBookPages());
        String currentBookContent = String.join("", newPages);
        return !Objects.equals(previousBookContent, currentBookContent);
    }

    private boolean bookNotEquals(WritableBookMeta meta) {
        if (meta == null) return true;
        String id = meta.getPersistentDataContainer().get(EDITABLE_BOOK_ID_KEY, PersistentDataType.STRING);
        return id == null || !id.equals(editableBookId.toString());
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent e) {
        if (!bookEditor.equals(e.getPlayer())) {
            return;
        }

        if (bookNotEquals(e.getPreviousBookMeta())) {
            return;
        }

        List<String> pages = e.getNewBookMeta().pages().stream().map(page -> PlainTextComponentSerializer.plainText().serialize(page)).toList();

        // Check if the player has edited this book.
        if (hasChanged(pages)) {
            edited = true;
        }

        // Add the pages of the book to the book meta.
        editableBookData.setPages(pages);
        // Ensure the ID stays on the item meta
        editableBookData.getPersistentDataContainer().set(EDITABLE_BOOK_ID_KEY, PersistentDataType.STRING, editableBookId.toString());
        editableBook.setItemMeta(editableBookData);


        // Perform the book sign action on signing the book.
        if (e.isSigning()) {
            e.setCancelled(true);
            if (bookResetAction != null) {
                bookResetAction.onBookReset();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void dropItem(PlayerDropItemEvent e) {
        if (!bookEditor.equals(e.getPlayer())) {
            return;
        }

        ItemStack dropped = e.getItemDrop().getItemStack();
        if (dropped.getType() != Material.WRITABLE_BOOK) {
            return;
        }

        if (!(dropped.getItemMeta() instanceof WritableBookMeta droppedMeta)) {
            return;
        }

        if (bookNotEquals(droppedMeta)) {
            return;
        }

        e.setCancelled(true);
        if (bookResetAction != null) {
            bookResetAction.onBookReset();
        }
    }

    @FunctionalInterface
    public interface BookResetAction {
        void onBookReset();
    }
}
