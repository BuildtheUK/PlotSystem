package net.bteuk.plotsystem.utils;

import lombok.experimental.UtilityClass;

/**
 * Utility class for parsing.
 */
@UtilityClass
public class ParseUtils {

    /**
     * Parse String to int, else 0.
     *
     * @param text {@link String} to parse
     * @return int value, if not possible 0.
     */
    public int toInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
