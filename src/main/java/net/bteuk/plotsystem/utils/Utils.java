package net.bteuk.plotsystem.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

public class Utils {
    public static ItemStack createItem(Material material, int amount, Component displayName, Component... loreString) {

        ItemStack item = ItemStack.of(material.isItem() ? material : Material.STRUCTURE_VOID);
        item.setAmount(amount);

        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        List<Component> lore = new ArrayList<>(Arrays.asList(loreString));
        meta.lore(lore);
        item.setItemMeta(meta);

        return item;
    }

    // Converts milliseconds to date.
    public static String getDate(long time) {

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        formatter.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        Date date = new Date(time);
        return formatter.format(date);
    }

    // Converts milliseconds to datetime.
    public static String getDateTime(long time) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm Z");
        formatter.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        Date date = new Date(time);
        return formatter.format(date);
    }
}
