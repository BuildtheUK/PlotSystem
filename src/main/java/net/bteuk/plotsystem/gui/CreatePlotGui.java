package net.bteuk.plotsystem.gui;

import net.bteuk.minecraft.gui.Gui;
import net.bteuk.minecraft.gui.GuiManager;
import net.bteuk.plotsystem.utils.PlotValues;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

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
        setItem(11, Utils.createItem(PlotValues.sizeMaterial(user.selectionTool.size), 1,
                        ChatUtils.title(PlotValues.sizeName(user.selectionTool.size)),
                        ChatUtils.line("Click to cycle through sizes.")),
                u ->

                {

                    // Get an instance of the plotsystem user.
                    User eUser = PlotSystem.getInstance().getUser(u.player);

                    // Change the size by 1.
                    // If less than 3 (large) increase by 1, else return to 1.
                    if (eUser.selectionTool.size == 3) {

                        eUser.selectionTool.size = 1;

                    } else {

                        eUser.selectionTool.size++;

                    }

                    // Update the gui.
                    refresh();
                    u.player.getOpenInventory().getTopInventory().setContents(getInventory().getContents());

                });

        // Choose plot difficulty.
        setItem(15, Utils.createItem(PlotValues.difficultyMaterial(user.selectionTool.difficulty), 1,
                        ChatUtils.title(PlotValues.difficultyName(user.selectionTool.difficulty)),
                        ChatUtils.line("Click to cycle through different difficulties.")),
                u ->

                {

                    User eUser = PlotSystem.getInstance().getUser(u.player);

                    // Change the difficulty by 1.
                    // If less than 3 (hard) increase by 1, else return to 1.
                    if (eUser.selectionTool.difficulty == 3) {

                        eUser.selectionTool.difficulty = 1;

                    } else {

                        eUser.selectionTool.difficulty++;

                    }

                    // Update the gui.
                    refresh();
                    u.player.getOpenInventory().getTopInventory().setContents(getInventory().getContents());

                });

        // Create plot.
        setItem(13, Utils.createItem(Material.DIAMOND, 1,
                        ChatUtils.title("Create Plot"),
                        ChatUtils.line("Click create a new plot with the settings selected.")),
                u ->

                {

                    User eUser = PlotSystem.getInstance().getUser(u.player);

                    // Close the inventory.
                    u.player.closeInventory();

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
