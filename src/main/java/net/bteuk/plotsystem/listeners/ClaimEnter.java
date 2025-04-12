package net.bteuk.plotsystem.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.network.sql.GlobalSQL;
import net.bteuk.network.sql.PlotSQL;
import net.bteuk.network.utils.Time;
import net.bteuk.plotsystem.PlotSystem;
import net.bteuk.plotsystem.utils.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class ClaimEnter implements Listener {

    final PlotSQL plotSQL;
    final GlobalSQL globalSQL;

    public ClaimEnter(PlotSystem plugin, PlotSQL plotSQL, GlobalSQL globalSQl) {

        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        this.plotSQL = plotSQL;
        this.globalSQL = globalSQl;

    }

    @EventHandler
    public void joinEvent(PlayerJoinEvent e) {

        Bukkit.getScheduler().scheduleSyncDelayedTask(PlotSystem.getInstance(), () -> {

            User u = PlotSystem.getInstance().getUser(e.getPlayer());
            checkRegion(u);
        }, 20L);
    }

    @EventHandler
    public void moveEvent(PlayerMoveEvent e) {

        User u = PlotSystem.getInstance().getUser(e.getPlayer());

        // Delay this so the movement has taken place.
        Bukkit.getScheduler().runTask(PlotSystem.getInstance(), () -> checkRegion(u));
    }

    @EventHandler
    public void teleportEvent(PlayerTeleportEvent e) {

        User u = PlotSystem.getInstance().getUser(e.getPlayer());

        // Delay this so the teleport has taken place.
        Bukkit.getScheduler().runTask(PlotSystem.getInstance(), () -> checkRegion(u));
    }

    public void checkRegion(User u) {

        // Get the location of the user.
        Location l = u.player.getLocation();

        // Create a query for all regions that the player is standing in, this should always contain 1 or less regions.
        // If more than 1 region is queried then something has gone wrong with creating regions.
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        ApplicableRegionSet applicableRegionSet = query.getApplicableRegions(BukkitAdapter.adapt(l));

        // Iterate through the regions, which should be at most 1.
        // If there is more than 1 region, throw an error.
        if (applicableRegionSet.size() > 1) {
            PlotSystem.LOGGER.severe("The player " + u.player.getName() + " is standing in more than 1 region, this should not be possible!");
            return;
        }

        // If there is a region, then check if it's a plot or zone.
        // If this is not the same as the player was previously standing, notify the user and update the information in the user instance.
        if (applicableRegionSet.size() == 1) {

            for (ProtectedRegion region : applicableRegionSet.getRegions()) {

                // Get region name.
                String regionName = region.getId();

                // If the regionName starts with a z, then it's a zone.
                // Else it's a region.
                // The try catch will try to prevent any format exceptions.
                try {

                    if (regionName.startsWith("z")) {

                        checkZone(u, Integer.parseInt(regionName.replace("z", "")));

                    } else {

                        checkPlot(u, Integer.parseInt(regionName));

                    }
                } catch (NumberFormatException e) {

                    PlotSystem.LOGGER.warning("ApplicableRegionSet found a region that is not a plot or zone, the region name is " + regionName);

                }
            }
        }

        // If you're currently in a plot or zone, but you're not in a region, show the player that they've left it.
        if (applicableRegionSet.size() == 0 && (u.inPlot + u.inZone) > 0) {

            // If the plot is claimed, send the relevant message.
            if (u.inPlot != 0) {
                if (!plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + u.inPlot + ";")) {

                    u.player.sendActionBar(
                            ChatUtils.success("You have left plot ")
                                    .append(Component.text(u.inPlot, NamedTextColor.DARK_AQUA)));

                } else {

                    // If you are the owner of the plot send the relevant message.
                    if (plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + u.inPlot + " AND uuid='" + u.uuid + "' AND is_owner=1;")) {

                        u.player.sendActionBar(
                                ChatUtils.success("You have left your plot."));

                    } else {

                        // If you are not an owner or member send the relevant message.
                        u.player.sendActionBar(
                                ChatUtils.success("You have left ")
                                        .append(Component.text(globalSQL.getString("SELECT name FROM player_data WHERE uuid = '" +
                                                        plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + u.inPlot + " AND is_owner=1;") + "';") + "'s",
                                                NamedTextColor.DARK_AQUA))
                                        .append(ChatUtils.success(" plot.")));

                    }
                }

            } else if (u.inZone != 0) {

                // Show zone leave message.
                u.player.sendActionBar(
                        ChatUtils.success("You have left zone ")
                                .append(Component.text(u.inZone, NamedTextColor.DARK_AQUA)));

            }

            // Set all variables to default values after the logic has been run.
            u.inPlot = 0;
            u.inZone = 0;

        }
    }

    private void checkPlot(User u, int plot) {

        // If the plot is not equal to the current plot, then notify the player and update the user instance.
        if (u.inPlot != plot) {

            // Set the zone value to zero, since you can never be in both at the same time.
            u.inZone = 0;

            // Set plot to the current plot.
            u.inPlot = plot;

            // If the plot is claimed, send the relevant message.
            if (!plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + plot + ";")) {

                u.player.sendActionBar(
                        ChatUtils.success("You have entered plot ")
                                .append(Component.text(u.inPlot, NamedTextColor.DARK_AQUA))
                                .append(ChatUtils.success(", it is unclaimed.")));

            } else {

                // If you are the owner of the plot send the relevant message.
                if (plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + plot + " AND uuid='" + u.uuid + "' AND is_owner=1;")) {

                    plotSQL.update("UPDATE plot_members SET last_enter=" + Time.currentTime() + ",inactivity_notice=0 WHERE id=" + plot + " AND uuid='" + u.uuid + "';");
                    u.player.sendActionBar(
                            ChatUtils.success("You have entered plot ")
                                    .append(Component.text(u.inPlot, NamedTextColor.DARK_AQUA))
                                    .append(ChatUtils.success(", you are the owner of this plot.")));

                    // If you are a member of the plot send the relevant message.
                } else if (plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + plot + " AND uuid='" + u.uuid + "' AND is_owner=0;")) {

                    plotSQL.update("UPDATE plot_members SET last_enter=" + Time.currentTime() + " WHERE id=" + plot + " AND uuid='" + u.uuid + "';");
                    u.player.sendActionBar(
                            ChatUtils.success("You have entered plot ")
                                    .append(Component.text(u.inPlot, NamedTextColor.DARK_AQUA))
                                    .append(ChatUtils.success(", you are a member of this plot.")));

                } else {

                    // If you are not an owner or member send the relevant message.
                    u.player.sendActionBar(
                            ChatUtils.success("You have entered ")
                                    .append(Component.text(globalSQL.getString("SELECT name FROM player_data WHERE uuid = '" +
                                            plotSQL.getString("SELECT uuid FROM plot_members WHERE id=" + plot + " AND is_owner=1;") + "';") + "'s", NamedTextColor.DARK_AQUA))
                                    .append(ChatUtils.success(" plot.")));

                }
            }

        } else {

            // If you are the owner or member of this plot update your last enter time.
            if (plotSQL.hasRow("SELECT id FROM plot_members WHERE id=" + u.inPlot + " AND uuid='" + u.uuid + "';")) {

                plotSQL.update("UPDATE plot_members SET last_enter=" + Time.currentTime() + " WHERE id=" + u.inPlot + " AND uuid='" + u.uuid + "';");

            }
        }
    }

    private void checkZone(User u, int zone) {

        if (u.inZone != zone) {

            // Set the plot value to zero, since you can never be in both at the same time.
            u.inPlot = 0;

            // Set zone to current zone.
            u.inZone = zone;

            // Check if the zone is public.
            if (plotSQL.hasRow("SELECT id FROM zones WHERE id=" + zone + " AND is_public=1;")) {

                u.player.sendActionBar(
                        ChatUtils.success("You have entered zone ")
                                .append(Component.text(zone, NamedTextColor.DARK_AQUA))
                                .append(ChatUtils.success(", it is public.")));

            } else {

                u.player.sendActionBar(
                        ChatUtils.success("You have entered zone ")
                                .append(Component.text(zone, NamedTextColor.DARK_AQUA)));

            }
        }
    }
}