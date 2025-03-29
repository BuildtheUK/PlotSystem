package net.bteuk.plotsystem.utils;

import lombok.Setter;
import net.bteuk.network.lib.enums.PlotDifficulties;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.enums.PlotStatus;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.network.utils.plotsystem.ReviewCategory;
import net.bteuk.network.utils.plotsystem.ReviewSelection;
import net.bteuk.plotsystem.PlotSystem;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.bteuk.plotsystem.PlotSystem.LOGGER;
import static net.bteuk.plotsystem.utils.Config.CONFIG;

/**
 * Helper class for plot-related actions.
 */
public class PlotHelper {

    @Setter
    private static PlotSQL plotSQL;

    @Setter
    private static List<PlotHologram> holograms = new ArrayList<>();

    private static Map<PlotDifficulties, ReviewCategoryThreshold> REVIEW_CATEGORY_THRESHOLDS;

    /**
     * Initialise the plot helper by setting the relevant variables.
     * @param plotSQL   {@link PlotSQL}
     */
    public static void init(PlotSQL plotSQL) {
        setPlotSQL(plotSQL);
    }

    /**
     * Update the submitted status of a plot, will update any relevant holograms.
     * @param id                the plot id
     * @param submittedStatus   the submitted status
     */
    public static boolean updateSubmittedStatus(int id, SubmittedStatus submittedStatus) {
        return updatePlotStatus(id, PlotStatus.SUBMITTED, submittedStatus);
    }

    /**
     * Update the status of a plot, will update any relevant holograms.
     * @param id            the plot id
     * @param plotStatus    the plot status
     */
    public static boolean updatePlotStatus(int id, PlotStatus plotStatus) {
        SubmittedStatus submittedStatus = null;
        if (plotStatus == PlotStatus.SUBMITTED) {
            submittedStatus = SubmittedStatus.SUBMITTED;
        }
        return updatePlotStatus(id, plotStatus, submittedStatus);
    }

    /**
     * Update the status of a plot, will update any relevant holograms.
     * @param id                the plot id
     * @param status            the plot status
     * @param submittedStatus   the submitted status of the plot, if status is submitted
     */
    private static boolean updatePlotStatus(int id, PlotStatus status, SubmittedStatus submittedStatus) {
        boolean hasChanged = false;
        if (!plotSQL.hasRow("SELECT 1 FROM plot_data WHERE id=" + id + " AND status='" + status.database_value + "'")) {
            plotSQL.update("UPDATE plot_data SET status='" + status.database_value + "' WHERE id=" + id + ";");
            hasChanged = true;
        }
        if (submittedStatus != null && !plotSQL.hasRow("SELECT 1 FROM plot_submission WHERE plot_id=" + id + " AND status='" + submittedStatus.database_value + "'")) {
            plotSQL.update("UPDATE plot_submission SET status='" + submittedStatus.database_value + "' WHERE plot_id=" + id + ";");
            hasChanged = true;
        }
        // Delay the hologram update until the plot has been completely updated.
        if (hasChanged && !PlotSystem.getInstance().isClosing()) {
            Bukkit.getScheduler().runTask(PlotSystem.getInstance(), () -> {

                // Update the hologram status.
                List<PlotHologram> hologramsToRemove = new ArrayList<>();
                holograms.stream().filter(hologram -> hologram.getPlot() == id).forEach(hologram -> {
                    hologram.updatePlotStatus(status, submittedStatus);
                    // If the hologram is empty, add it to the list of holograms to remove.
                    if (hologram.isEmpty()) {
                        hologramsToRemove.add(hologram);
                    }
                });
                // Remove any empty holograms.
                holograms.removeAll(hologramsToRemove);
            });
        }
        return hasChanged;
    }

    public static void addPlotHologram(PlotHologram plotHologram) {
        holograms.add(plotHologram);
    }

    public static void updatePlotHologram(int plot) {
        holograms.stream().filter(plotHologram -> plotHologram.getPlot() == plot).forEach(PlotHologram::updateLocation);
    }

    public static void addPlayer(Player player) {
        holograms.forEach(hologram -> hologram.setHologramVisibilityForPlayer(player));
    }

    public static ReviewSelection getReviewCategoryThreshold(PlotDifficulties difficulty, ReviewCategory category) {
        return REVIEW_CATEGORY_THRESHOLDS.get(difficulty).getThreshold(category);
    }

    public static boolean reviewCategoryReachedThreshold(PlotDifficulties difficulty, ReviewCategory category, ReviewSelection selection) {
        if (REVIEW_CATEGORY_THRESHOLDS == null) {
            loadReviewCategoryThresholds();
        }
        ReviewSelection threshold =  getReviewCategoryThreshold(difficulty, category);
        return switch (selection) {
            case GOOD -> threshold == ReviewSelection.GOOD || threshold == ReviewSelection.OK || threshold == ReviewSelection.POOR;
            case OK -> threshold == ReviewSelection.OK || threshold == ReviewSelection.POOR;
            case POOR -> threshold == ReviewSelection.POOR;
            default -> false;
        };
    }

    private static void loadReviewCategoryThresholds() {
        ConfigurationSection categories = CONFIG.getConfigurationSection("categories");
        REVIEW_CATEGORY_THRESHOLDS = new HashMap<>();
        for (PlotDifficulties difficulty : PlotDifficulties.values()) {
            REVIEW_CATEGORY_THRESHOLDS.put(difficulty, new ReviewCategoryThreshold());
        }
        if (categories != null) {
            Set<String> categoryKeys = categories.getKeys(false);
            categoryKeys.forEach(category -> {
                ReviewCategory categoryEnum;
                try {
                    categoryEnum = ReviewCategory.valueOf(category);
                } catch (IllegalArgumentException e) {
                    return;
                }
                // Get the threshold for the category from the config.
                List<?> thresholds = categories.getList(category);
                if (thresholds == null || thresholds.isEmpty() || thresholds.size() < 3 || !(thresholds.getFirst() instanceof String)) {
                    return;
                }
                for (PlotDifficulties difficulty : PlotDifficulties.values()) {
                    try {
                        ReviewSelection selection = ReviewSelection.valueOf((String) thresholds.get(difficulty.ordinal()));
                        REVIEW_CATEGORY_THRESHOLDS.get(difficulty).addThreshold(categoryEnum, selection);
                    } catch (IllegalArgumentException e) {
                            // Continue, just ignore this one.
                    }
                }
            });
        } else {
            LOGGER.warning("There is no configuration for the reviewing categories!");
        }

    }

    private static class ReviewCategoryThreshold {

        private final Map<ReviewCategory, ReviewSelection> categoryThresholds = new HashMap<>();

        private void addThreshold(ReviewCategory category, ReviewSelection threshold) {
            categoryThresholds.put(category, threshold);
        }

        private ReviewSelection getThreshold(ReviewCategory category) {
            return categoryThresholds.get(category);
        }
    }
}
