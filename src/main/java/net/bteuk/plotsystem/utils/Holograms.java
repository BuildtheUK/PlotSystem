package net.bteuk.plotsystem.utils;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.List;

public class Holograms {
    public static Hologram createHologram(String name, Location location, List<String> text) {
        name = name.replace(" ", "_");
        if (DHAPI.getHologram(name) == null) {
            return DHAPI.createHologram(name, location, text);
        }
        return null;
    }
}
