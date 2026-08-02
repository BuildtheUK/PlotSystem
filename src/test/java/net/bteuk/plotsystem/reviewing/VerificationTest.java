package net.bteuk.plotsystem.reviewing;

import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.plotsystem.ReviewCategory;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.btuk.minecraft.gui.GuiManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VerificationTest {

    @Mock
    private PlotSystem plotSystem;

    @Mock
    private NetworkAPI networkAPI;

    @Mock
    private PlotAPI plotAPI;

    @Mock
    private PlotHelper plotHelper;

    @Mock
    private GuiManager guiManager;

    @Mock
    private User user;

    @Mock
    private Player player;

    @Mock
    private PlayerInventory inventory;

    @Mock
    private World world;

    @Mock
    private ItemStack reviewBookItem;

    @Mock
    private BookMeta reviewBookMeta;

    @Mock
    private Book book;

    @Mock
    private Logger logger;

    private Verification verification;

    private final int plotID = 1;

    private final int reviewId = 10;

    private final String reviewerUuid = UUID.randomUUID().toString();

    private final String playerUuid = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() throws Exception {
        PlotSystem.LOGGER = logger;
        try (
                MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class);
                MockedStatic<ItemStack> itemStackMockedStatic = mockStatic(ItemStack.class);
                MockedStatic<Book> bookMockedStatic = mockStatic(Book.class);
        ) {

            bukkitMockedStatic.when(() -> Bukkit.getWorld(anyString())).thenReturn(world);
            itemStackMockedStatic.when(() -> ItemStack.of(any(), anyInt())).thenReturn(reviewBookItem);
            bookMockedStatic.when(() -> Book.book(any(), any(), any(Component.class))).thenReturn(book);

            // Must be lenient since this method is only called on class init, if other tests init the class before this, the stub is not needed.
            lenient().when(reviewBookItem.getItemMeta()).thenReturn(reviewBookMeta);
            when(player.getInventory()).thenReturn(inventory);

            try (
                    MockedConstruction<ReviewHotbar> reviewHotbarConstruction = mockConstruction(ReviewHotbar.class);
                    MockedConstruction<ReviewBook> reviewBookConstruction = mockConstruction(ReviewBook.class);
                    MockedConstruction<VerificationGui> verificationGuiConstruction = mockConstruction(VerificationGui.class)
            ) {

                // Handle final fields in User using reflection
                Field playerField = User.class.getDeclaredField("player");
                playerField.setAccessible(true);
                playerField.set(user, player);

                Field uuidField = User.class.getDeclaredField("uuid");
                uuidField.setAccessible(true);
                uuidField.set(user, playerUuid);

                when(networkAPI.getPlotAPI()).thenReturn(plotAPI);
                when(plotAPI.getActiveReviewId(plotID)).thenReturn(reviewId);
                when(plotAPI.getPlotReviewer(reviewId)).thenReturn(reviewerUuid);
                when(plotAPI.getReviewCategories(reviewId)).thenReturn(List.of(ReviewCategory.values()));

                verification = new Verification(plotSystem, plotID, user, networkAPI, plotHelper, guiManager);
            }
        }
    }

    @Test
    void testSaveWithNoChanges() {
        try (
                MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class);
        ) {
            bukkitMockedStatic.when(() -> Bukkit.getWorld(anyString())).thenReturn(world);

            when(plotAPI.getReviewOutcome(reviewId)).thenReturn(true);

            verification.save(true);

            // The verification was created.
            verify(plotAPI).createVerification(reviewId, playerUuid, true, true);
            // No verification category feedback was saved since no changes were made.
            verify(plotAPI, never()).savePlotVerificationCategory(anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt());
            verify(plotAPI).completeReview(reviewId, true);

            // Reviewer reputation should be increased by 1, assuming the initial is 0 as it is a mocked return value.
            verify(plotAPI).updateReviewerReputation(reviewerUuid, 1);
        }
    }
}