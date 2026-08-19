package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import lombok.Setter;
import net.bteuk.network.api.NetworkAPI;
import net.bteuk.network.api.plotsystem.ReviewCategory;
import net.bteuk.network.api.plotsystem.ReviewCategoryFeedback;
import net.bteuk.network.api.plotsystem.ReviewSelection;
import net.bteuk.network.api.plotsystem.SubmittedStatus;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.btuk.minecraft.gui.GuiManager;
import org.btuk.network.lib.dto.PlotMessage;
import org.btuk.network.lib.utils.ChatUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class Verification extends ReviewAction {

    private final VerificationGui reviewActionGui;

    private final int reviewId;

    private final String reviewer;

    private final Map<ReviewCategory, ReviewCategoryFeedback> previousReviewFeedback = new HashMap<>();

    private boolean changedOutcome = false;

    private int changedSelection = 0;

    private int changedFeedback = 0;

    @Setter
    private boolean disableReputationChange = false;

    /**
     * Constructor to create a new review.
     *
     * @param instance instance of the plugin
     * @param plotID   the plot to review
     * @param user     the reviewer
     */
    public Verification(PlotSystem instance, int plotID, User user, NetworkAPI networkAPI, PlotHelper plotHelper, GuiManager guiManager) {
        super(instance, plotID, user, networkAPI, plotHelper, guiManager);

        this.reviewId = networkAPI.getPlotAPI().getActiveReviewId(plotID);
        this.reviewer = networkAPI.getPlotAPI().getPlotReviewer(reviewId);

        // Get the feedback from the reviewer.
        List<ReviewCategory> reviewCategories = networkAPI.getPlotAPI().getReviewCategories(reviewId);
        for (ReviewCategory category : reviewCategories) {
            previousReviewFeedback.put(category, new ReviewCategoryFeedback(category, networkAPI.getPlotAPI().getReviewSelection(reviewId, category),
                    networkAPI.getPlotAPI().getReviewBookId(reviewId, category)));
        }

        getReviewBook().initReviewBook(previousReviewFeedback.values());

        // Create the review gui.
        reviewActionGui = new VerificationGui(this, guiManager, networkAPI.getPlotAPI(), networkAPI.getGlobalSQL());
    }

    @Override
    public void save(boolean accept) {

        // Determine whether changes have been made in the verification.
        determineChanges(accept);

        int verificationId = plotAPI.createVerification(reviewId, user.uuid, changedOutcome != accept, accept);
        updateFeedback(verificationId, reviewId, previousReviewFeedback);
        plotAPI.completeReview(reviewId, accept);

        completeReview(accept);

        // Update the reviewer reputation.
        if (!disableReputationChange) {
            plotAPI.updateReviewerReputation(reviewer, getReputationChange());
        }

        // Close gui and clear review if exists.
        this.closeReviewAction();
    }

    @Override
    public void cancel() {
        // Set the plot back to 'awaiting verification'.
        plotHelper.updateSubmittedStatus(plotID, SubmittedStatus.AWAITING_VERIFICATION);

        // Send feedback.
        user.player.sendMessage(ChatUtils.success("Cancelled verification of plot ")
                .append(Component.text(plotID, NamedTextColor.DARK_AQUA)));

        super.cancel();
    }

    private void updateFeedback(int verificationId, int reviewId, Map<ReviewCategory, ReviewCategoryFeedback> previousReviewFeedback) {
        Map<ReviewCategory, ReviewCategoryFeedback> updatedReviewFeedback = getReviewBook().updateFeedback(reviewId, previousReviewFeedback);

        // Store the plot_verification_feedback for each category.
        for (ReviewCategoryFeedback categoryFeedback : previousReviewFeedback.values()) {

            ReviewCategoryFeedback updatedCategoryFeedback = updatedReviewFeedback.get(categoryFeedback.category());

            // Only store the category if there is updated feedback and the category is required.
            if (updatedCategoryFeedback != null && categoryFeedback.category().isRequired()) {
                plotAPI.savePlotVerificationCategory(verificationId, categoryFeedback.category().name(), categoryFeedback.selection().name(),
                        updatedCategoryFeedback.selection().name(), categoryFeedback.bookId(), updatedCategoryFeedback.bookId());
            }
        }
    }

    @Override
    protected void notifyReviewers() {
        // Send message to reviewers that a plot has been verified.
        PlotMessage plotMessage = new PlotMessage("A plot has been verified, there %s %s %s awaiting verification.", true);
        chatAPI.sendPlotMessage(plotMessage);
    }

    private void determineChanges(boolean accept) {
        // Determine what changes were made to the review.
        changedOutcome = accept != plotAPI.getReviewOutcome(reviewId);

        for (ReviewCategory category : ReviewCategory.values()) {
            if (category.isRequired()) {
                ReviewCategoryFeedback previousCategoryFeedback = previousReviewFeedback.get(category);
                if (previousCategoryFeedback == null || previousCategoryFeedback.selection() != getReviewBook().getReviewSelectionForCategory(category)) {
                    changedSelection++;
                }

                ReviewSelection selection = getReviewBook().getReviewSelectionForCategory(category);
                boolean thresholdReached = selection != null && plotHelper.reviewCategoryThresholdReached(plotDifficulty, category, selection);
                if (!accept && getReviewBook().isEdited(category) && !thresholdReached) {
                    changedFeedback++;
                }
            }
        }
    }

    private double getReputationChange() {
        double reputation = plotAPI.getReviewerReputation(reviewer);

        if (changedOutcome) {
            reputation = Math.min(reputation - 1, reputation * 0.75);
        } else if (changedSelection > 0) {
            reputation = Math.min(reputation - 0.1 * changedSelection, reputation * (1 - 0.1 * changedSelection));
        } else if (changedFeedback == 0) {
            reputation += 1;
        }

        // Return the new reputation, the minimum value is 0.
        return Math.max(reputation, 0);
    }
}
