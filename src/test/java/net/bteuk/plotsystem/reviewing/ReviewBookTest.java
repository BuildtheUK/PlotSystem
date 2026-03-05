package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.plotsystem.ReviewCategory;
import net.bteuk.network.api.plotsystem.ReviewCategoryFeedback;
import net.bteuk.network.api.plotsystem.ReviewSelection;
import net.bteuk.plotsystem.PlotSystem;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewBookTest {

    private static final String PLAYER_NAME = "testPlayer";

    @Mock
    private PlotSystem plotSystem;

    @Mock
    private Player player;

    @Mock
    private ReviewHotbar reviewHotbar;

    @Mock
    private PlotAPI plotAPI;

    @Mock
    private ItemStack reviewBookItem;

    @Mock
    private BookMeta reviewBookMeta;

    @Mock
    private Server server;

    @Mock
    private PluginManager pluginManager;

    private ReviewBook reviewBook;

    @BeforeEach
    void setUp() {
        try (
                MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class);
                MockedStatic<ItemStack> itemStackMockedStatic = mockStatic(ItemStack.class);
        ) {
            bukkitMockedStatic.when(Bukkit::getServer).thenReturn(server);
            itemStackMockedStatic.when(() -> ItemStack.of(any(), anyInt())).thenReturn(reviewBookItem);

            when(reviewBookItem.getItemMeta()).thenReturn(reviewBookMeta);
            when(player.getName()).thenReturn(PLAYER_NAME);
            when(server.getPluginManager()).thenReturn(pluginManager);

            reviewBook = new ReviewBook(plotSystem, player, reviewHotbar, plotAPI);
        }
    }

    @Test
    void testUpdateFeedback_addGeneralCategory() {
        try (MockedStatic<PlotSystem> plotSystemMockedStatic = mockStatic(PlotSystem.class)) {
            plotSystemMockedStatic.when(PlotSystem::getInstance).thenReturn(plotSystem);
            try (
                    MockedConstruction<ItemStack> itemStackConstruction = mockConstruction(ItemStack.class, (mock, context) -> {
                        when(mock.getItemMeta()).thenReturn(reviewBookMeta);
                    });
                    MockedConstruction<NamespacedKey> namespacedKeyConstruction = mockConstruction(NamespacedKey.class);
                    MockedConstruction<EditableBook> editableBookConstruction = mockConstruction(EditableBook.class, (mock, context) -> {
                        when(mock.isEdited()).thenReturn(true);
                    });
            ) {
                AtomicInteger bookId = new AtomicInteger(1);
                Map<ReviewCategory, ReviewCategoryFeedback> previousReviewFeedback = new HashMap<>();
                Arrays.stream(ReviewCategory.values()).forEach(category -> {
                    if (category.isRequired()) {
                        previousReviewFeedback.put(category, new ReviewCategoryFeedback(category, ReviewSelection.OK,
                                bookId.getAndIncrement()));
                    }
                });

                reviewBook.initReviewBook(previousReviewFeedback.values());
                reviewBook.switchToCategory(ReviewCategory.GENERAL);

                Map<ReviewCategory, ReviewCategoryFeedback> updatedFeedback = reviewBook.updateFeedback(1, previousReviewFeedback);

                // We added the general category to the feedback, this will be saved but is not included in the updatedFeedback map as we do not want it to affect the reputation.
                assertFalse(updatedFeedback.containsKey(ReviewCategory.GENERAL));
                verify(plotAPI).savePlotReviewCategoryFeedback(1, ReviewCategory.GENERAL.name(), ReviewSelection.NONE.name(), 1);
            }
        }
    }
}