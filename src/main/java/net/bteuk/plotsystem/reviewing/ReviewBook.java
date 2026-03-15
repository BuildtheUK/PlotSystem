package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.plotsystem.ReviewCategory;
import net.bteuk.network.api.plotsystem.ReviewCategoryFeedback;
import net.bteuk.network.api.plotsystem.ReviewSelection;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.inventory.meta.WritableBookMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReviewBook implements Listener {

    private static final Component REVIEW_BOOK_TITLE = ChatUtils.title("Review Book");

    private static final Component EDIT_FEEDBACK = Component.text("[+]", NamedTextColor.GRAY).hoverEvent(HoverEvent.showText(Component.text("Edit Category Feedback")));

    private static final ItemStack REVIEW_BOOK = createReviewBook();

    private final HashMap<ReviewCategory, ReviewSelection> reviewCategorySelection = new LinkedHashMap<>();

    private final HashMap<ReviewCategory, EditableBook> reviewCategoryFeedback = new HashMap<>();

    private final PlotSystem instance;

    private final Player player;

    private final ReviewHotbar reviewHotbar;

    private final PlotAPI plotAPI;

    private Book book;

    public ReviewBook(PlotSystem instance, Player player, ReviewHotbar reviewHotbar, PlotAPI plotAPI) {

        this.instance = instance;
        this.player = player;
        this.reviewHotbar = reviewHotbar;
        this.plotAPI = plotAPI;

        initReviewCategorySelection();
        updateReviewBook();

        reviewHotbar.setReviewBookSlot(REVIEW_BOOK);

        // Register listeners.
        Bukkit.getServer().getPluginManager().registerEvents(this, instance);
    }

    private static ItemStack createReviewBook() {
        ItemStack reviewBook = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta categoryFeedbackBookMeta = (BookMeta) reviewBook.getItemMeta();
        categoryFeedbackBookMeta.displayName(REVIEW_BOOK_TITLE);
        reviewBook.setItemMeta(categoryFeedbackBookMeta);
        return reviewBook;
    }

    @NotNull
    private static Component getReviewSelectionLine(Map.Entry<ReviewCategory, ReviewSelection> entry) {
        Component line = Component.empty();
        line = line.append(Component.text(entry.getKey().getDisplayName(), Style.style(TextDecoration.BOLD)));
        line = line.appendSpace();
        line = line.append(EDIT_FEEDBACK.clickEvent(getEditFeedbackClickEvent(entry.getKey())));
        line = line.appendNewline();
        for (ReviewSelection selection : ReviewSelection.values()) {
            Component option = selection.getAbbreviatedComponent();
            if (selection == entry.getValue()) {
                option = option.decorate(TextDecoration.BOLD);
            } else {
                option = option.clickEvent(getCategorySelectionClickEvent(entry.getKey(), selection));
            }
            line = line.append(option);
        }
        return line;
    }

    private static ClickEvent getCategorySelectionClickEvent(ReviewCategory category, ReviewSelection selection) {
        return ClickEvent.runCommand(String.format("/review %s %s", category, selection));
    }

    private static ClickEvent getEditFeedbackClickEvent(ReviewCategory category) {
        return ClickEvent.runCommand(String.format("/review feedback %s", category));
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

    public void initReviewBook(Collection<ReviewCategoryFeedback> initialReviewCategoryFeedback) {
        for (ReviewCategoryFeedback categoryFeedback : initialReviewCategoryFeedback) {
            reviewCategorySelection.put(categoryFeedback.category(), categoryFeedback.selection());
            if (categoryFeedback.bookId() != 0) {
                // Get the pages of the book.
                List<String> pages = plotAPI.getBookPages(categoryFeedback.bookId());

                reviewCategoryFeedback.put(categoryFeedback.category(), createCategoryFeedback(categoryFeedback.category(), pages.toArray(String[]::new)));
            }
        }
        updateReviewBook();
    }

    /**
     * Open the review book.
     */
    public void open() {
        player.openBook(book);
    }

    /**
     * Switch to a specific review category.
     *
     * @param category the category to switch to.
     */
    public void switchToCategory(ReviewCategory category) {

        // Check if the EditableBook for this feedback already exists.
        EditableBook categoryFeedback = reviewCategoryFeedback.get(category);

        if (categoryFeedback == null) {
            // Create a new editable book for this category.
            categoryFeedback = createCategoryFeedback(category);
            reviewCategoryFeedback.put(category, categoryFeedback);
        }

        // Set the book in the inventory of the player.
        reviewHotbar.setReviewBookSlot(categoryFeedback.getEditableBook());
    }

    /**
     * Update the selection of a review category.
     *
     * @param category  the category to change the selection of
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
            // Check if there is feedback to save for the category.
            EditableBook book = reviewCategoryFeedback.get(entry.getKey());
            saveCategoryFeedback(reviewId, entry.getKey(), entry.getValue(), book);
        }
    }

    public Map<ReviewCategory, ReviewCategoryFeedback> updateFeedback(int reviewId, Map<ReviewCategory, ReviewCategoryFeedback> previousReviewFeedback) {
        Map<ReviewCategory, ReviewCategoryFeedback> updatedReviewFeedback = new HashMap<>();
        for (ReviewCategoryFeedback previousCategoryFeedback : previousReviewFeedback.values()) {
            // Check if the feedback has changed.
            ReviewCategory category = previousCategoryFeedback.category();
            ReviewSelection newSelection = reviewCategorySelection.get(category);
            if (isEdited(previousCategoryFeedback, newSelection)) {
                // Update the feedback.
                int bookId = previousCategoryFeedback.bookId();
                EditableBook newBook = reviewCategoryFeedback.get(category);
                if (isEdited(newBook)) {
                    bookId = saveBook(newBook);
                }
                plotAPI.updatePlotCategoryFeedback(reviewId, category.name(), newSelection.name(), bookId);
                updatedReviewFeedback.put(category, new ReviewCategoryFeedback(category, newSelection, bookId));
            }
        }
        // Additionally, insert any new categories with feedback (this could be used to add general feedback).
        reviewCategorySelection.forEach((category, selection) -> {
            if (!previousReviewFeedback.containsKey(category)) {
                EditableBook newBook = reviewCategoryFeedback.get(category);
                if (isEdited(newBook)) {
                    int bookId = saveBook(newBook);
                    plotAPI.savePlotReviewCategoryFeedback(reviewId, category.name(), selection.name(), bookId);
                }
            }
        });
        return updatedReviewFeedback;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (cancelEvent(e.getWhoClicked(), e.getCurrentItem()) || cancelEvent(e.getWhoClicked(), e.getCursor())) {
            e.setCancelled(true);

            // If item is review gui then open the gui.
            if (REVIEW_BOOK.equals(e.getCurrentItem())) {
                Bukkit.getScheduler().runTaskLater(instance, this::open, 1);
            }
        }
    }

    @EventHandler
    public void interactEvent(PlayerInteractEvent e) {
        if (cancelEvent(e.getPlayer(), e.getItem())) {
            e.setCancelled(true);

            // If item is review gui then open the gui.
            if (REVIEW_BOOK.equals(e.getItem())) {
                Bukkit.getScheduler().runTaskLater(instance, this::open, 1);
            }
        }
    }

    public ReviewSelection getReviewSelectionForCategory(ReviewCategory category) {
        return reviewCategorySelection.get(category);
    }

    public List<String> getFeedbackForCategory(ReviewCategory category) {
        return reviewCategoryFeedback.get(category).getBookPages();
    }

    public boolean hasFeedback(ReviewCategory category) {
        EditableBook book = reviewCategoryFeedback.get(category);
        return book != null && book.hasContent();
    }

    public boolean isEdited(ReviewCategory category) {
        EditableBook book = reviewCategoryFeedback.get(category);
        return book != null && book.isEdited();
    }

    private boolean cancelEvent(HumanEntity humanEntity, ItemStack item) {
        // Check if player is the reviewer and that the item is the review book.
        return player.equals(humanEntity) && REVIEW_BOOK.equals(item);
    }

    private void initReviewCategorySelection() {
        for (ReviewCategory category : ReviewCategory.values()) {
            if (category.isRequired()) {
                reviewCategorySelection.put(category, ReviewSelection.NONE);
            }
        }
        // Also init the general category.
        reviewCategorySelection.put(ReviewCategory.GENERAL, ReviewSelection.NONE);
    }

    private boolean isEdited(ReviewCategoryFeedback previousCategoryFeedback, ReviewSelection currentSelection) {
        return !previousCategoryFeedback.selection().equals(currentSelection) ||
                isEdited(reviewCategoryFeedback.get(previousCategoryFeedback.category()));
    }

    private void updateReviewBook() {

        // Title
        Component page = Component.empty();
        page = page.append(Component.text("Feedback").decorate(TextDecoration.UNDERLINED).decorate(TextDecoration.BOLD));

        // Add the general feedback category.
        page = page.appendSpace();
        page = page.append(EDIT_FEEDBACK.clickEvent(getEditFeedbackClickEvent(ReviewCategory.GENERAL)));
        page = page.appendNewline();

        // Add a line for each category.
        for (Map.Entry<ReviewCategory, ReviewSelection> entry : reviewCategorySelection.entrySet()) {
            Component line = getReviewSelectionLine(entry);
            page = page.appendNewline();
            page = page.append(line);
        }

        this.book = Book.book(REVIEW_BOOK_TITLE, ChatUtils.line(player.getName()), page);
    }

    private EditableBook createCategoryFeedback(ReviewCategory category, String... pages) {
        ItemStack categoryFeedback = new ItemStack(Material.WRITABLE_BOOK);
        WritableBookMeta categoryFeedbackBookMeta = (WritableBookMeta) categoryFeedback.getItemMeta();
        categoryFeedbackBookMeta.displayName(ChatUtils.title(category.getDisplayName() + " Feedback"));
        categoryFeedbackBookMeta.setPages(pages);
        categoryFeedback.setItemMeta(categoryFeedbackBookMeta);

        return new EditableBook(instance, player, categoryFeedback, createCategoryFeedbackResetAction());
    }

    private EditableBook.BookResetAction createCategoryFeedbackResetAction() {
        return () -> {
            // Open the review book.
            open();

            // Reset the hotbar slot to the review book.
            Bukkit.getScheduler().runTaskLater(instance, () -> reviewHotbar.setReviewBookSlot(REVIEW_BOOK), 1L);
        };
    }

    private boolean isEdited(EditableBook book) {
        return book != null && book.isEdited();
    }

    private void saveCategoryFeedback(int reviewId, ReviewCategory category, ReviewSelection selection, EditableBook book) {
        int bookId = 0;
        if (isEdited(book)) {
            bookId = saveBook(book);
        }
        // Only save if there is at least a selection or feedback.
        // The General category can have no selection while having feedback.
        if (selection != ReviewSelection.NONE || bookId != 0) {
            plotAPI.savePlotReviewCategoryFeedback(reviewId, category.name(), selection.name(), bookId);
        }
    }

    private int saveBook(EditableBook book) {

        // Get the feedback written in the book.
        List<String> pages = book.getBookPages();

        // Create new book id.
        int bookId = 1 + plotAPI.getHighestBookId();

        // Iterate through all pages and store them in database.
        int i = 1;

        for (String page : pages) {
            if (!page.isBlank()) {
                plotAPI.saveBook(bookId, i, page);
                i++;
            }
        }

        return bookId;
    }
}
