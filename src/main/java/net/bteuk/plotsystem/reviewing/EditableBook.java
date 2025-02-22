package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.plotsystem.PlotSystem;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

public class EditableBook implements Listener {

	private final Player bookEditor;

	@Getter
	private final ItemStack editableBook;

	private final BookSignAction bookSignAction;


	private BookMeta editableBookData;
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
		editableBookData = (BookMeta) editableBook.getItemMeta();
	}

	public void unregister() {
		PlayerEditBookEvent.getHandlerList().unregister(this);
	}

	public void open() {
		bookEditor.openBook(editableBookData);
	}

	public List<Component> getBookPages() {
		return editableBookData.pages();
	}

	@EventHandler
	public void onBookEdit(PlayerEditBookEvent e) {

		// Check if the player is the editor.
		if (!bookEditor.equals(e.getPlayer())) {
			return;
		}

		// Check if the player has edited this book.
		if (editableBookData.equals(e.getPreviousBookMeta())) {

			// Save editing of book.
			editableBookData = e.getNewBookMeta();
			editableBook.setItemMeta(editableBookData);

			edited = true;
		}

		// Perform the book sign action on signing the book.
		if (e.isSigning()) {
			bookSignAction.onBookSign();
		}
	}

	@FunctionalInterface
	public interface BookSignAction {
		void onBookSign();
	}
}
