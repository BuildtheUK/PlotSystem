package net.bteuk.plotsystem.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.bteuk.network.api.plotsystem.ReviewCategory;
import net.bteuk.network.api.plotsystem.ReviewSelection;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command for editing selections to reviewing categories during the reviewing process.
 */
public class ReviewCommand implements BasicCommand {

    private final PlotSystem instance;

    public ReviewCommand(PlotSystem instance) {
        this.instance = instance;
    }

    @Override
    public void execute(CommandSourceStack stack, String @NotNull [] args) {

        // Check if the player is actually reviewing, else ignore the command.
        if (!(stack.getSender() instanceof Player player)) {
            return;
        }

        User user = instance.getUser(player);

        if (user == null) {
            player.sendMessage(ChatUtils.error("An error has occurred, please rejoin and contact your server admin."));
            return;
        }

        if (user.getReview() == null) {
            player.sendMessage(ChatUtils.error("This command can only be used during the reviewing process."));
        }

        // Get the review category and selection.
        if (args.length < 2) {
            return;
        }

        if (args[0].equals("feedback")) {
            try {
                ReviewCategory reviewCategory = ReviewCategory.valueOf(args[1]);
                user.getReview().getReviewBook().switchToCategory(reviewCategory);
            } catch (IllegalArgumentException e) {
                // Do nothing, this command should never be used directly by a player.
            }
        } else {
            try {
                ReviewCategory reviewCategory = ReviewCategory.valueOf(args[0]);
                ReviewSelection reviewSelection = ReviewSelection.valueOf(args[1]);
                user.getReview().getReviewBook().updateReviewSelection(reviewCategory, reviewSelection);
            } catch (IllegalArgumentException e) {
                // Do nothing, this command should never be used directly by a player.
            }
        }
    }
}
