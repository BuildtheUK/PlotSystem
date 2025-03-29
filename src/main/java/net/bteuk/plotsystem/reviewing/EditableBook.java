package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.plotsystem.PlotSystem;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.WritableBookMeta;

import java.util.List;

public class EditableBook implements Listener {

	private final Player bookEditor;

	@Getter
	private final ItemStack editableBook;

	private final BookSignAction bookSignAction;

	private final WritableBookMeta editableBookData;

	private BookMeta previousBookMeta;

	@Getter
	private boolean edited;
	
	public EditableBook(PlotSystem instance, Player bookEditor, ItemStack editableBook, BookSignAction bookSignAction) {

		if (editableBook.getType() != Material.WRITABLE_BOOK) {
			throw new IllegalArgumentException("ItemStack must be a WRITABLE_BOOK");
		}

		// Register listener.
		Bukkit.getServer().getPluginManager().registerEvents(this, instance);

		this.bookEditor = bookEditor;
		this.editableBook = editableBook;
		this.bookSignAction = bookSignAction;
		editableBookData = (WritableBookMeta) editableBook.getItemMeta();
		previousBookMeta = (BookMeta) editableBook.getItemMeta();
	}

	public void unregister() {
		PlayerEditBookEvent.getHandlerList().unregister(this);
	}

	public List<String> getBookPages() {
		return editableBookData.getPages();
	}

	public boolean hasContent() {
		boolean hasContent = false;
		for (String page : getBookPages()) {
			hasContent = hasContent || StringUtils.isNotBlank(page);
		}
		return hasContent;
	}

	public boolean hasChanged(List<String> newPages) {
		if (newPages.size() != getBookPages().size()) {
			return true;
		}

		boolean hasChanged = false;
		for (int i = 0; i < newPages.size(); i++) {
			if (getBookPages().get(i).trim().equals(newPages.get(i).trim())) {
				hasChanged = true;
			}
		}

		return hasChanged;
	}

	@EventHandler
	public void onBookEdit(PlayerEditBookEvent e) {

		// Check if the player is the editor and that this book was edited.
		if (!bookEditor.equals(e.getPlayer()) || !e.getPreviousBookMeta().equals(previousBookMeta)) {
			return;
		}

		// Check if the player has edited this book.
		List<String> pages = e.getNewBookMeta().pages().stream().map(page -> PlainTextComponentSerializer.plainText().serialize(page)).toList();
		if (hasChanged(pages)) {
			// Add the pages of the book to the book meta.
			editableBookData.setPages(pages);
			editableBook.setItemMeta(editableBookData);
			previousBookMeta = (BookMeta) editableBookData;
			edited = true;
		}

		// Perform the book sign action on signing the book.
		if (e.isSigning()) {
			e.setCancelled(true);
			bookSignAction.onBookSign();
		}
	}

	@FunctionalInterface
	public interface BookSignAction {
		void onBookSign();
	}
}
