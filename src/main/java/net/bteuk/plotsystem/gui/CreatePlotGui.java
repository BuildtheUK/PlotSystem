package net.bteuk.plotsystem.gui;

import org.btuk.minecraft.gui.Gui;
import org.btuk.minecraft.gui.GuiManager;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotValues;
import net.bteuk.plotsystem.utils.User;
import net.bteuk.plotsystem.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Objects;

public class CreatePlotGui extends Gui {

    private final User user;

    // This gui handles the plot creation process, and will allow the user to set the parameters of the plot.
    public CreatePlotGui(GuiManager manager, User user) {

        super(manager, 27, Component.text("Create Plot Menu", NamedTextColor.AQUA, TextDecoration.BOLD));

        this.user = user;

        createGui();
    }

    private void createGui() {

        // Choose plot size.
        setItem(11, Utils.createItem(Objects.requireNonNull(PlotValues.sizeMaterial(user.selectionTool.size)), 1,
                        ChatUtils.title(Objects.requireNonNull(PlotValues.sizeName(user.selectionTool.size))),
                        ChatUtils.line("Click to cycle through sizes.")),
                event -> {
                    Player player = (Player) event.getWhoClicked();

                    // Get an instance of the plotsystem user.
                    User eUser = PlotSystem.getInstance().getUser(player);

                    // Change the size by 1.
                    // If less than 3 (large) increase by 1, else return to 1.
                    if (eUser.selectionTool.size == 3) {
                        eUser.selectionTool.size = 1;
                    } else {
                        eUser.selectionTool.size++;
                    }

                    // Update the gui.
                    refresh();
                    updatePlayerInventory(player);
                });

        // Choose plot difficulty.
        setItem(15, Utils.createItem(Objects.requireNonNull(PlotValues.difficultyMaterial(user.selectionTool.difficulty)), 1,
                        ChatUtils.title(Objects.requireNonNull(PlotValues.difficultyName(user.selectionTool.difficulty))),
                        ChatUtils.line("Click to cycle through different difficulties.")),
                event -> {
                    Player player = (Player) event.getWhoClicked();

                    User eUser = PlotSystem.getInstance().getUser(player);

                    // Change the difficulty by 1.
                    // If less than 3 (hard) increase by 1, else return to 1.
                    if (eUser.selectionTool.difficulty == 3) {

                        eUser.selectionTool.difficulty = 1;

                    } else {

                        eUser.selectionTool.difficulty++;

                    }

                    // Update the gui.
                    refresh();
                    updatePlayerInventory(player);
                });

        // Create plot.
        setItem(13, Utils.createItem(Material.DIAMOND, 1,
                        ChatUtils.title("Create Plot"),
                        ChatUtils.line("Click create a new plot with the settings selected.")),
                event -> {
                    Player player = (Player) event.getWhoClicked();

                    User eUser = PlotSystem.getInstance().getUser(player);

                    // Close the inventory.
                    player.closeInventory();

                    // Create plot with the selection created by the user.
                    eUser.selectionTool.createPlot();

                });

        // Fill the border of the gui with grey stained glass pane.
        for (int i = 0; i <= 26; i++) {

            // Skip the centre.
            if (i == 10) {
                i = 17;
            }

            setItem(i, Utils.createItem(Material.GRAY_STAINED_GLASS_PANE, 1, Component.empty()));
        }
    }

    public void refresh() {
        this.clear();
        createGui();
    }
}
