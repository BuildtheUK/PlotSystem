package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@Getter
public enum ReviewSelection {
    GOOD(Component.text("[Good]", NamedTextColor.GREEN)),
    OK(Component.text("[Ok]", NamedTextColor.YELLOW)),
    POOR(Component.text("[Poor]", NamedTextColor.RED)),
    NONE(Component.empty());

    private final Component displayComponent;

    ReviewSelection(Component displayComponent) {
        this.displayComponent = displayComponent;
    }
}
