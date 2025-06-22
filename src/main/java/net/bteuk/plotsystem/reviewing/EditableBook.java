package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.plotsystem.PlotSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.WritableBookMeta;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class EditableBook implements Listener {

    private final Player bookEditor;

    @Getter
    private final ItemStack editableBook;

    private final BookResetAction bookResetAction;

    private final WritableBookMeta editableBookData;

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
        editableBookData = (WritableBookMeta) editableBook.getItemMeta();

        // Add the random uuid as obfuscated lore text as a unique identifier.
        editableBookData.lore(Collections.singletonList(Component.text(UUID.randomUUID().toString()).decorate(TextDecoration.OBFUSCATED)));
        editableBook.setItemMeta(editableBookData);
    }

    public void unregister() {
        PlayerEditBookEvent.getHandlerList().unregister(this);
    }

    public List<String> getBookPages() {
        return editableBookData.getPages().stream().map(String::trim).collect(Collectors.toList());
    }

    public boolean hasContent() {
        return !StringUtils.isEmpty(String.join("", getBookPages()));
    }

    public boolean hasChanged(List<String> newPages) {
        // Compare the total content of the book.
        // Trim all the leading and trailing whitespace first.
        String previousBookContent = String.join("", getBookPages());
        String currentBookContent = String.join("", newPages);

        return !Objects.equals(previousBookContent, currentBookContent);
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent e) {
        // Check if the player is the editor and that this book was edited.
        if (!bookEditor.equals(e.getPlayer()) || !e.getPreviousBookMeta().equals(editableBookData)) {
            return;
        }

        List<String> pages = e.getNewBookMeta().pages().stream().map(page -> PlainTextComponentSerializer.plainText().serialize(page)).toList();

        // Check if the player has edited this book.
        if (hasChanged(pages)) {
            edited = true;
        }

        // Add the pages of the book to the book meta.
        editableBookData.setPages(pages);
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
        // Check if the item is a writable book, that the player equals the book editor and that the book meta equals the item meta.
        if (e.getItemDrop().getItemStack().getType() == Material.WRITABLE_BOOK && bookEditor.equals(e.getPlayer()) && editableBookData.equals(e.getItemDrop().getItemStack().getItemMeta())) {
            e.setCancelled(true);
            if (bookResetAction != null) {
                bookResetAction.onBookReset();
            }
        }
    }

    @FunctionalInterface
    public interface BookResetAction {
        void onBookReset();
    }
}
