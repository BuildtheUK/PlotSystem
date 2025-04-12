package net.bteuk.plotsystem.gui;

import net.bteuk.network.gui.Gui;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.Utils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;

public class CreateZoneGui extends Gui {

    private final User user;

    // This gui handles the plot creation process, and will allow the user to set the parameters of the plot.
    public CreateZoneGui(User user) {

        super(27, Component.text("Create Plot Menu", NamedTextColor.AQUA, TextDecoration.BOLD));

        this.user = user;

        createGui();

    }

    private void createGui() {

        // Create zone.
        setItem(13, Utils.createItem(Material.DIAMOND, 1,
                        ChatUtils.title("Create Zone"),
                        ChatUtils.line("Click create a new zone with the settings selected.")),
                u ->

                {

                    User eUser = PlotSystem.getInstance().getUser(u.player);

                    // Close the inventory.
                    u.player.closeInventory();

                    // Create plot with the selection created by the user.
                    eUser.selectionTool.createZone();

                });

        // Set public/private.
        if (user.selectionTool.is_public) {

            setItem(11, Utils.createItem(Material.OAK_DOOR, 1,
                            ChatUtils.title("Set the zone to private."),
                            ChatUtils.line("Click to make the zone private."),
                            ChatUtils.line("A private zone means the owner has"),
                            ChatUtils.line("to invite members for them to build.")),
                    u ->

                    {

                        // Set private.
                        user.selectionTool.is_public = false;

                        // Refresh the gui.
                        refresh();
                        user.player.getOpenInventory().getTopInventory().setContents(getInventory().getContents());

                    });

        } else {

            setItem(11, Utils.createItem(Material.IRON_DOOR, 1,
                            ChatUtils.title("Set the zone to public."),
                            ChatUtils.line("Click to make the zone public."),
                            ChatUtils.line("A public zone allows JrBuilder+"),
                            ChatUtils.line("to join without having to request.")),
                    u ->

                    {

                        // Set private.
                        user.selectionTool.is_public = true;

                        // Refresh the gui.
                        refresh();
                        user.player.getOpenInventory().getTopInventory().setContents(getInventory().getContents());

                    });

        }

        // Set expiration time.
        setItem(15, Utils.createItem(Material.CLOCK, user.selectionTool.hours,
                        ChatUtils.title("Set the zone expiration time."),
                        ChatUtils.line("Click to cycle through expiration times."),
                        ChatUtils.line("The current time is " + user.selectionTool.hours + " hours."),
                        ChatUtils.line("The expiration time can be extended later.")),
                u ->

                {

                    // Increase expiration time.
                    switch (user.selectionTool.hours) {
                        case 2 -> user.selectionTool.hours = 6;
                        case 6 -> user.selectionTool.hours = 24;
                        case 24 -> user.selectionTool.hours = 48;
                        case 48 -> user.selectionTool.hours = 2;
                    }

                    // Refresh the gui.
                    refresh();
                    user.player.getOpenInventory().getTopInventory().setContents(getInventory().getContents());

                });

        for (int i = 0; i <= 26; i++) {

            // Skip the centre.
            if (i == 10) {
                i = 17;
            }

            setItem(i, Utils.createItem(Material.GRAY_STAINED_GLASS_PANE, 1, Component.empty()));
        }
    }

    public void refresh() {

        clearGui();
        createGui();

    }
}
