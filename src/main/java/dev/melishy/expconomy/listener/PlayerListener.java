package dev.melishy.expconomy.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import dev.melishy.expconomy.Expconomy;
import dev.melishy.expconomy.economy.ExperienceUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Syncs balances on join, quit, death and respawn, and sets the XP drop on death. */
public final class PlayerListener implements Listener {

    private final Expconomy plugin;

    public PlayerListener(Expconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.economy().onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.economy().onQuit(event.getPlayer());
        plugin.economy().store().save();
    }

    // HIGH so we run after most plugins, but leave MONITOR for loggers.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("death.modify-drops", true)) {
            return;
        }
        // keepInventory, or another plugin, wants the player to keep their XP.
        if (event.getKeepLevel()) {
            return;
        }
        Player player = event.getEntity();
        int total = plugin.economy().balanceAtDeath(player);
        int percent = Math.max(0, Math.min(100, plugin.getConfig().getInt("death.drop-percentage", 10)));
        int dropped = (int) ((long) total * percent / 100);
        int kept = total - dropped;

        // Paper's ServerPlayer.reset() sets the level to newLevel, then calls
        // giveExperiencePoints(newExp), which also adds newExp to the total counter.
        // Give newExp only the leftover points so they can't push past the next level.
        int level = ExperienceUtil.levelFromTotal(kept);
        int leftover = (int) (kept - ExperienceUtil.xpAtLevel(level));
        event.setDroppedExp(dropped);
        event.setNewLevel(level);
        event.setNewExp(leftover);
        event.setNewTotalExp(kept - leftover);
    }

    /**
     * Runs after every plugin has changed the event, including us, and records
     * what the player will respawn with. Runs even with modify-drops off, so
     * death-screen payments still go to the store.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeathFinal(PlayerDeathEvent event) {
        Player player = event.getEntity();
        long respawnXp = event.getKeepLevel()
                ? plugin.economy().balanceAtDeath(player)
                : ExperienceUtil.xpAtLevel(Math.max(0, event.getNewLevel())) + Math.max(0, event.getNewExp());
        plugin.economy().recordDeath(player, (int) Math.min(Integer.MAX_VALUE, respawnXp));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRespawn(PlayerPostRespawnEvent event) {
        plugin.economy().onRespawn(event.getPlayer());
    }
}
