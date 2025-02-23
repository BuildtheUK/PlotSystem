package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.plotsystem.PlotSystem;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReviewBook implements Listener {

    private static final Component REVIEW_BOOK_TITLE = ChatUtils.title("Review Book");

    private static final Component EDIT_FEEDBACK = Component.text("[+]", NamedTextColor.GRAY).hoverEvent(HoverEvent.showText(Component.text("Edit Category Feedback")));

    private static final ItemStack REVIEW_BOOK = createReviewBook();

    private final PlotSQL plotSQL = PlotSystem.getInstance().plotSQL;

    private final HashMap<ReviewCategory, ReviewSelection> reviewCategorySelection = new LinkedHashMap<>();

    private final HashMap<ReviewCategory, EditableBook> reviewCategoryFeedback = new HashMap<>();

    private final PlotSystem instance;

    private final Player player;

    private final ReviewHotbar reviewHotbar;

    private Book book;

    public ReviewBook(PlotSystem instance, Player player, ReviewHotbar reviewHotbar) {

        this.instance = instance;
        this.player = player;
        this.reviewHotbar = reviewHotbar;

        initReviewCategorySelection();
        updateReviewBook();

        // Register listeners.
        Bukkit.getServer().getPluginManager().registerEvents(this, instance);
    }

    public void unregister() {
        // Unregister all category feedback books.
        for (EditableBook categoryFeedbackBook : reviewCategoryFeedback.values()) {
            categoryFeedbackBook.unregister();
        }
        // Unregister the listeners for the review book.
        InventoryClickEvent.getHandlerList().unregister(this);
        PlayerInteractEvent.getHandlerList().unregister(this);
    }

    /**
     * Open the review book.
     */
    public void open() {
        player.openBook(book);
    }

    /**
     * Open the feedback book for a specific review category.
     *
     * @param category the category to open the feedback for.
     */
    public void openFeedback(ReviewCategory category) {

        // Check if the EditableBook for this feedback already exists.
        EditableBook categoryFeedback = reviewCategoryFeedback.get(category);

        if (categoryFeedback == null) {
            // Create a new editable book for this category.
            categoryFeedback = createCategoryFeedback(category);
            reviewCategoryFeedback.put(category, categoryFeedback);
        }

        // Set the book in the inventory of the player.
        reviewHotbar.setReviewBookSlot(categoryFeedback.getEditableBook());

        // Open the book.
        categoryFeedback.open();
    }

    /**
     * Update the selection of a review category.
     *
     * @param category the category to change the selection of
     * @param selection the selection to change it to
     */
    public void updateReviewSelection(ReviewCategory category, ReviewSelection selection) {
        reviewCategorySelection.put(category, selection);
        updateReviewBook();

        // Open the book again with the updated contents.
        player.openBook(book);
    }

    public void saveFeedback(int reviewId) {
        for (Map.Entry<ReviewCategory, ReviewSelection> entry : reviewCategorySelection.entrySet()) {
            // Check if there is a feedback to save for the category.
            EditableBook book = reviewCategoryFeedback.get(entry.getKey());
            saveCategoryFeedback(reviewId, entry.getKey(), entry.getValue(), book);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        e.setCancelled(cancelEvent(e.getWhoClicked(), e.getCurrentItem()) || cancelEvent(e.getWhoClicked(), e.getCursor()));

        // If item is review gui then open the gui.
        if (REVIEW_BOOK.equals(e.getCurrentItem())) {
            Bukkit.getScheduler().runTaskLater(instance, this::open, 1);
        }
    }

    @EventHandler
    public void interactEvent(PlayerInteractEvent e) {
        e.setCancelled(cancelEvent(e.getPlayer(), e.getItem()));

        // If item is review gui then open the gui.
        if (REVIEW_BOOK.equals(e.getItem())) {
            Bukkit.getScheduler().runTaskLater(instance, this::open, 1);
        }
    }

    private static ItemStack createReviewBook() {
        ItemStack reviewBook = new ItemStack(Material.BOOK);
        BookMeta categoryFeedbackBookMeta = (BookMeta) reviewBook.getItemMeta();
        categoryFeedbackBookMeta.displayName(REVIEW_BOOK_TITLE);
        reviewBook.setItemMeta(categoryFeedbackBookMeta);
        return reviewBook;
    }

    private boolean cancelEvent(HumanEntity humanEntity, ItemStack item) {
        // Check if player is the reviewer and that the item is the review book.
        return item != null && (player.equals(humanEntity) && REVIEW_BOOK.equals(item));
    }

    private void initReviewCategorySelection() {
        reviewCategorySelection.put(ReviewCategory.OUTLINES, ReviewSelection.NONE);
        reviewCategorySelection.put(ReviewCategory.FEATURES, ReviewSelection.NONE);
        reviewCategorySelection.put(ReviewCategory.ROOF, ReviewSelection.NONE);
        reviewCategorySelection.put(ReviewCategory.GARDEN, ReviewSelection.NONE);
        reviewCategorySelection.put(ReviewCategory.TEXTURES, ReviewSelection.NONE);
        reviewCategorySelection.put(ReviewCategory.DETAILS, ReviewSelection.NONE);
    }

    private void updateReviewBook() {

        // Title
        Component page = Component.text("Feedback").decorate(TextDecoration.UNDERLINED).decorate(TextDecoration.BOLD);
        page = page.appendNewline();

        // Add a line for each category.
        for (Map.Entry<ReviewCategory, ReviewSelection> entry : reviewCategorySelection.entrySet()) {
            Component line = getReviewSelectionLine(entry);
            page = page.append(line);
        }

        this.book = Book.book(REVIEW_BOOK_TITLE, ChatUtils.line(player.getName()), page);
    }

    private EditableBook createCategoryFeedback(ReviewCategory category) {
        ItemStack categoryFeedback = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta categoryFeedbackBookMeta = (BookMeta) categoryFeedback.getItemMeta();
        categoryFeedbackBookMeta.displayName(ChatUtils.title(category.getDisplayName() + " Feedback"));
        categoryFeedback.setItemMeta(categoryFeedbackBookMeta);

        return  new EditableBook(instance, player, categoryFeedback, createCategoryFeedbackSignAction());
    }

    private EditableBook.BookSignAction createCategoryFeedbackSignAction() {
        return () -> {
            // Reset the hotbar slot to the review book.
            reviewHotbar.setReviewBookSlot(REVIEW_BOOK);

            // Open the review book.
            open();
        };
    }

    private void saveCategoryFeedback(int reviewId, ReviewCategory category, ReviewSelection selection, EditableBook book) {
        int bookId = 0;
        if (book != null && book.isEdited()) {
            bookId = saveBook(book);
        }
        plotSQL.savePlotReviewCategoryFeedback(reviewId, category.name(), selection.name(), bookId);
    }

    private int saveBook(EditableBook book) {

        // Get the feedback written in the book.
        List<Component> pages = book.getBookPages();

        // TODO: Don't save the book if there is no content.

        // Create new book id.
        int bookId = 1 + plotSQL.getInt("SELECT id FROM book_data ORDER BY id DESC;");

        // Iterate through all pages and store them in database.
        int i = 1;

        for (Component page : pages) {
            String stringPage = PlainTextComponentSerializer.plainText().serialize(page);
            if (!stringPage.isBlank()) {
                plotSQL.update("INSERT INTO book_data(id,page,contents) VALUES(" + bookId + "," + i + ",'" + stringPage.replace("'", "\\'") + "');");
                i++;
            }
        }

        return bookId;
    }


    @NotNull
    private static Component getReviewSelectionLine(Map.Entry<ReviewCategory, ReviewSelection> entry) {
        Component line = Component.text(entry.getKey().getDisplayName());
        line = line.appendSpace();
        for (ReviewSelection selection : ReviewSelection.values()) {
            Component option = selection.getDisplayComponent();
            if (selection == entry.getValue()) {
                option = option.decorate(TextDecoration.BOLD);
            } else {
                option = option.clickEvent(getCategorySelectionClickEvent(entry.getKey(), selection));
            }
            line = line.append(option);
        }
        line = line.appendSpace();
        line = line.append(EDIT_FEEDBACK.clickEvent(getEditFeedbackClickEvent(entry.getKey())));
        return line;
    }

    private static ClickEvent getCategorySelectionClickEvent(ReviewCategory category, ReviewSelection selection) {
        return ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, String.format("/review %s %s", category, selection));
    }

    private static ClickEvent getEditFeedbackClickEvent(ReviewCategory category) {
        return ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, String.format("/review feedback %s", category));
    }
}
