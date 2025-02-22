package net.bteuk.plotsystem.reviewing;

import lombok.Getter;

@Getter
public enum ReviewCategory {
    OUTLINES("Outlines"),
    FEATURES("Features"),
    ROOF("Roof"),
    GARDEN("Garden"),
    TEXTURES("Textures"),
    DETAILS("Details");

    private final String displayName;

    ReviewCategory(String displayName) {
        this.displayName = displayName;
    }
}
