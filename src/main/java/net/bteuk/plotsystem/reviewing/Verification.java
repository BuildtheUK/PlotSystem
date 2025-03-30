package net.bteuk.plotsystem.reviewing;

import lombok.Getter;
import net.bteuk.network.Network;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.utils.enums.SubmittedStatus;
import net.bteuk.network.utils.plotsystem.ReviewCategory;
import net.bteuk.network.utils.plotsystem.ReviewCategoryFeedback;
import net.bteuk.network.utils.plotsystem.ReviewSelection;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.PlotHelper;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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

    /**
     * Constructor to create a new review.
     *
     * @param instance instance of the plugin
     * @param plotID   the plot to review
     * @param user     the reviewer
     */
    public Verification(PlotSystem instance, int plotID, User user) {
        super(instance, plotID, user);

        this.reviewId = plotSQL.getInt("SELECT id FROM plot_review WHERE plot_id=" + plotID + " AND completed=0;");
        this.reviewer = plotSQL.getString("SELECT reviewer FROM plot_review WHERE id=" + this.reviewId + ";");

        // Get the feedback from the reviewer.
        List<String> reviewCategories = plotSQL.getStringList("SELECT category FROM plot_category_feedback WHERE review_id=" + reviewId + ";");
        for (String category : reviewCategories) {
            previousReviewFeedback.put(ReviewCategory.valueOf(category), new ReviewCategoryFeedback(
                    ReviewCategory.valueOf(category),
                    ReviewSelection.valueOf(plotSQL.getString("SELECT selection FROM plot_category_feedback WHERE review_id=" + reviewId + " AND category='" + category + "';")),
                    plotSQL.getInt("SELECT book_id FROM plot_category_feedback WHERE review_id=" + reviewId + " AND category='" + category + "';")
            ));
        }

        getReviewBook().initReviewBook(previousReviewFeedback.values());

        // Create the review gui.
        reviewActionGui = new VerificationGui(this);

    }

    @Override
    public void save(boolean accept) {

        // Determine whether changes have been made in the verification.
        determineChanges(accept);

        int verificationId = plotSQL.createVerification(reviewId, user.uuid, changedOutcome != accept, accept);
        updateFeedback(verificationId, reviewId, previousReviewFeedback);
        plotSQL.update("UPDATE plot_review SET accepted=" + accept + ", completed=1 WHERE id=" + reviewId + ";");

        completeReview(accept);

        // Update the reviewer reputation.
        plotSQL.update("UPDATE reviewers SET reputation=" + getReputationChange() + " WHERE uuid='" + reviewer + "';");

        // Close gui and clear review if exists.
        this.closeReviewAction();
    }

    @Override
    public void cancel() {
        // Set the plot back to 'awaiting verification'.
        PlotHelper.updateSubmittedStatus(plotID, SubmittedStatus.AWAITING_VERIFICATION);

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

            // Only store the category if there is updated feedback.
            if (updatedCategoryFeedback != null) {
                plotSQL.savePlotVerificationCategory(verificationId, categoryFeedback.category().name(),
                        categoryFeedback.selection().name(), updatedCategoryFeedback.selection().name(),
                        categoryFeedback.bookId(), updatedCategoryFeedback.bookId());
            }
        }
    }

    @Override
    protected void notifyReviewers() {
        // Send message to reviewers that a plot has been verified.
        PlotMessage plotMessage = new PlotMessage("A plot has been verified, there %s %s %s awaiting verification.", true);
        Network.getInstance().getChat().sendSocketMesage(plotMessage);
    }

    private void determineChanges(boolean accept) {
        // Determine what changes were made to the review.
        changedOutcome = accept != plotSQL.getBoolean("SELECT accepted FROM plot_review WHERE id=" + reviewId + ";");

        for (ReviewCategory category : ReviewCategory.values()) {
            if (category.isRequired()) {
                ReviewCategoryFeedback previousCategoryFeedback = previousReviewFeedback.get(category);
                if (previousCategoryFeedback == null || previousCategoryFeedback.selection() != getReviewBook().getReviewSelectionForCategory(category)) {
                    changedSelection++;
                }

                ReviewSelection selection = getReviewBook().getReviewSelectionForCategory(category);
                boolean thresholdReached = selection != null && PlotHelper.reviewCategoryThresholdReached(plotDifficulty, category, selection);
                if (!accept && getReviewBook().isEdited(category) && !thresholdReached) {
                    changedFeedback++;
                }
            }
        }
    }

    private double getReputationChange() {
        double reputation = plotSQL.getReviewerReputation(reviewer);

        if (changedOutcome) {
            reputation = Math.min(reputation - 1, reputation * 0.75);
        } else if (changedSelection > 0) {
            reputation = Math.min(reputation - 0.1 * changedSelection, reputation * (1 - 0.1 * changedSelection));
        } else if (changedFeedback == 0) {
            reputation += 1;
        }

        return reputation;
    }
}
