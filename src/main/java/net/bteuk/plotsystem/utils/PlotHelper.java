package net.bteuk.plotsystem.utils;

import lombok.Setter;
import net.bteuk.network.api.PlotAPI;
import net.bteuk.network.api.plotsystem.PlotStatus;
import net.bteuk.network.api.plotsystem.ReviewCategory;
import net.bteuk.network.api.plotsystem.ReviewSelection;
import net.bteuk.network.api.plotsystem.SubmittedStatus;
import net.bteuk.network.lib.enums.PlotDifficulties;
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
    private List<PlotHologram> holograms = new ArrayList<>();

    private Map<PlotDifficulties, ReviewCategoryThreshold> REVIEW_CATEGORY_THRESHOLDS;

    private final PlotAPI plotAPI;

    public PlotHelper(PlotAPI plotAPI) {
        this.plotAPI = plotAPI;
    }

    /**
     * Update the submitted status of a plot, will update any relevant holograms.
     *
     * @param id              the plot id
     * @param submittedStatus the submitted status
     */
    public void updateSubmittedStatus(int id, SubmittedStatus submittedStatus) {
        updatePlotStatus(id, PlotStatus.SUBMITTED, submittedStatus);
    }

    /**
     * Update the status of a plot, will update any relevant holograms.
     *
     * @param id         the plot id
     * @param plotStatus the plot status
     */
    public boolean updatePlotStatus(int id, PlotStatus plotStatus) {
        SubmittedStatus submittedStatus = null;
        if (plotStatus == PlotStatus.SUBMITTED) {
            submittedStatus = SubmittedStatus.SUBMITTED;
        }
        return updatePlotStatus(id, plotStatus, submittedStatus);
    }

    /**
     * Update the status of a plot, will update any relevant holograms.
     *
     * @param id              the plot id
     * @param status          the plot status
     * @param submittedStatus the submitted status of the plot, if status is submitted
     */
    private boolean updatePlotStatus(int id, PlotStatus status, SubmittedStatus submittedStatus) {
        boolean hasChanged = false;
        if (plotAPI.getPlotStatus(id) != status) {
            plotAPI.setPlotStatus(id, status.database_value);
            hasChanged = true;
        }
        if (submittedStatus != null && plotAPI.getPlotSubmissionStatus(id) != submittedStatus) {
            plotAPI.setPlotSubmissionStatus(id, submittedStatus.getDatabaseValue());
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

    public void addPlotHologram(PlotHologram plotHologram) {
        holograms.add(plotHologram);
    }

    public void updatePlotHologram(int plot) {
        holograms.stream().filter(plotHologram -> plotHologram.getPlot() == plot).forEach(PlotHologram::updateLocation);
    }

    public void addPlayer(Player player) {
        holograms.forEach(hologram -> hologram.setHologramVisibilityForPlayer(player));
    }

    public ReviewSelection getReviewCategoryThreshold(PlotDifficulties difficulty, ReviewCategory category) {
        return REVIEW_CATEGORY_THRESHOLDS.get(difficulty).getThreshold(category);
    }

    public boolean reviewCategoryThresholdReached(PlotDifficulties difficulty, ReviewCategory category, ReviewSelection selection) {
        if (REVIEW_CATEGORY_THRESHOLDS == null) {
            loadReviewCategoryThresholds();
        }
        ReviewSelection threshold = getReviewCategoryThreshold(difficulty, category);
        return switch (selection) {
            case GOOD -> threshold == ReviewSelection.GOOD || threshold == ReviewSelection.OK || threshold == ReviewSelection.POOR;
            case OK -> threshold == ReviewSelection.OK || threshold == ReviewSelection.POOR;
            case POOR -> threshold == ReviewSelection.POOR;
            default -> false;
        };
    }

    private void loadReviewCategoryThresholds() {
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
