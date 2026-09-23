package dev.melishy.expconomy.economy;

import dev.melishy.expconomy.Expconomy;
import dev.melishy.expconomy.storage.BalanceStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player XP balances. Alive online players read and write their XP bar.
 * Offline and dead players read and write {@link BalanceStore}.
 * <p>
 * Dead players: Paper only resets the XP bar when the player clicks Respawn.
 * Until then the bar still shows the XP from before death, and a modded client
 * can keep sending commands. If we read the bar, /xpc pay on the death screen
 * would spend XP that the respawn then gives back. So the death listener writes
 * the respawn balance to the store, and {@link #onRespawn(Player)} applies it.
 * <p>
 * Threads: Vault calls can arrive off the main thread, and Bukkit only lets us
 * touch XP on the main thread. We lock every read-modify-write. Off-thread
 * changes to online players go into {@link #pendingDeltas} and get applied next
 * tick. Reads add the pending delta.
 * <p>
 * Offline: we can't edit a player file, so offline changes mark the player as
 * pending in the store and {@link #onJoin(Player)} applies the stored value.
 */
public final class XpEconomy {

    private final Expconomy plugin;
    private final BalanceStore store;
    private final Object lock = new Object();
    /** Guarded by lock. XP not yet applied to an online player's bar (can be negative). */
    private final Map<UUID, Long> pendingDeltas = new HashMap<>();

    public XpEconomy(Expconomy plugin, BalanceStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public BalanceStore store() {
        return store;
    }

    /** Maximum balance from config, or {@link Integer#MAX_VALUE} when unlimited. */
    public int maxBalance() {
        int max = plugin.getConfig().getInt("balance.maximum", 0);
        return max <= 0 ? Integer.MAX_VALUE : max;
    }

    public int getBalance(OfflinePlayer player) {
        synchronized (lock) {
            return currentBalance(player);
        }
    }

    public boolean has(OfflinePlayer player, int amount) {
        return getBalance(player) >= amount;
    }

    /**
     * Set a player's balance to an absolute value, clamped to {@code [0, maxBalance]}.
     *
     * @return the value actually stored after clamping
     */
    public int setBalance(OfflinePlayer player, int amount) {
        synchronized (lock) {
            return writeBalance(player, amount);
        }
    }

    /**
     * Apply a signed change to a player's balance.
     *
     * @return {@code true} if the change was applied; {@code false} if it would
     * have dropped the balance below zero (a withdrawal larger than the balance).
     */
    public boolean change(OfflinePlayer player, int delta) {
        synchronized (lock) {
            long target = (long) currentBalance(player) + delta;
            if (target < 0) {
                return false;
            }
            writeBalance(player, clampToInt(target));
            return true;
        }
    }

    /** Add XP; returns the amount actually added after clamping to the max. */
    public int add(OfflinePlayer player, int amount) {
        synchronized (lock) {
            int before = currentBalance(player);
            int after = writeBalance(player, clampToInt((long) before + amount));
            return after - before;
        }
    }

    /** Remove XP; returns the amount actually removed (never more than the balance). */
    public int remove(OfflinePlayer player, int amount) {
        synchronized (lock) {
            int before = currentBalance(player);
            int after = writeBalance(player, clampToInt((long) before - amount));
            return before - after;
        }
    }

    /**
     * Moves XP between two players in one locked step, so nothing can change
     * either balance halfway through.
     *
     * @return the amount moved: 0 if the sender can't afford it, less than
     * {@code amount} if the receiver hit the max balance
     */
    public int transfer(OfflinePlayer from, OfflinePlayer to, int amount) {
        synchronized (lock) {
            int fromBalance = currentBalance(from);
            if (amount <= 0 || fromBalance < amount) {
                return 0;
            }
            int toBalance = currentBalance(to);
            int moved = (int) Math.min(amount, Math.max(0, (long) maxBalance() - toBalance));
            if (moved <= 0) {
                return 0;
            }
            writeBalance(from, fromBalance - moved);
            writeBalance(to, toBalance + moved);
            return moved;
        }
    }

    /** Main thread, on join. Applies offline changes, or copies live XP into the store. */
    public void onJoin(Player player) {
        synchronized (lock) {
            UUID uuid = player.getUniqueId();
            if (player.isDead()) {
                // They logged out on the death screen. onRespawn applies the store.
                store.markPending(uuid);
                return;
            }
            // Keep pendingDeltas: an async change can land between login and join.
            // It's relative to the bar, so it still applies on top of the store value.
            if (store.isPending(uuid)) {
                ExperienceUtil.setTotalExperience(player, store.get(uuid));
                store.clearPending(uuid);
            } else {
                store.put(uuid, player.getName(), ExperienceUtil.getTotalExperience(player));
            }
        }
    }

    /** Main thread, on quit and shutdown. Stores live XP plus any pending delta. */
    public void onQuit(Player player) {
        synchronized (lock) {
            UUID uuid = player.getUniqueId();
            Long pending = pendingDeltas.remove(uuid);
            if (player.isDead()) {
                // The store already holds the respawn balance. The bar is stale.
                store.markPending(uuid);
                return;
            }
            long live = ExperienceUtil.getTotalExperience(player);
            store.put(uuid, player.getName(), clampToInt(live + (pending == null ? 0 : pending)));
            if (pending != null && pending != 0) {
                // The player file saves the old XP, so apply the stored value on next join.
                store.markPending(uuid);
            }
        }
    }

    /** Main thread, during the death event. The bar plus pending delta, before any death changes. */
    public int balanceAtDeath(Player player) {
        synchronized (lock) {
            return liveBalance(player);
        }
    }

    /**
     * Main thread, at the end of the death event. Stores the XP the player will
     * respawn with. Until they respawn, their balance comes from the store.
     */
    public void recordDeath(Player player, int respawnXp) {
        synchronized (lock) {
            UUID uuid = player.getUniqueId();
            pendingDeltas.remove(uuid);
            store.put(uuid, player.getName(), respawnXp);
            store.clearPending(uuid);
        }
    }

    /** Main thread, after respawn. Applies anything paid to or spent by the player while dead. */
    public void onRespawn(Player player) {
        synchronized (lock) {
            UUID uuid = player.getUniqueId();
            if (store.isPending(uuid)) {
                ExperienceUtil.setTotalExperience(player, store.get(uuid));
                store.clearPending(uuid);
            }
            store.put(uuid, player.getName(), liveBalance(player));
        }
    }

    /** Copies online balances into the store for the leaderboard and autosave. */
    public void refreshOnline() {
        synchronized (lock) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (usesBar(player)) {
                    store.put(player.getUniqueId(), player.getName(), liveBalance(player));
                }
            }
        }
    }

    // --- internals, callers must hold the lock ---

    /** True if this player's balance is their XP bar. False for offline and dead players. */
    private static boolean usesBar(@Nullable Player player) {
        return player != null && !player.isDead();
    }

    private int liveBalance(Player player) {
        long live = ExperienceUtil.getTotalExperience(player);
        return clampToInt(live + pendingDeltas.getOrDefault(player.getUniqueId(), 0L));
    }

    private int currentBalance(OfflinePlayer player) {
        Player online = player.getPlayer();
        return usesBar(online) ? liveBalance(online) : store.get(player.getUniqueId());
    }

    private int writeBalance(OfflinePlayer player, int amount) {
        int clamped = Math.max(0, Math.min(amount, maxBalance()));
        UUID uuid = player.getUniqueId();
        Player online = player.getPlayer();
        if (!usesBar(online)) {
            store.put(uuid, player.getName(), clamped);
            store.markPending(uuid);
            return clamped;
        }
        // Don't write the store here. Before PlayerJoinEvent the store may still
        // hold unapplied offline changes. refreshOnline keeps the leaderboard fresh.
        if (Bukkit.isPrimaryThread()) {
            // clamped already includes the pending delta.
            pendingDeltas.remove(uuid);
            ExperienceUtil.setTotalExperience(online, clamped);
        } else {
            long delta = (long) clamped - liveBalance(online);
            if (delta != 0) {
                pendingDeltas.merge(uuid, delta, Long::sum);
                scheduleApply(uuid);
            }
        }
        return clamped;
    }

    private void scheduleApply(UUID uuid) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyPending(uuid));
    }

    /** Main thread. Adds the pending delta to the XP bar. */
    private void applyPending(UUID uuid) {
        synchronized (lock) {
            Long delta = pendingDeltas.remove(uuid);
            if (delta == null || delta == 0) {
                return;
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                // They quit first. onQuit stored the balance with this delta.
                return;
            }
            if (online.isDead()) {
                // Died first. recordDeath cleared deltas, but be safe and route it to the store.
                store.put(uuid, online.getName(), clampToInt((long) store.get(uuid) + delta));
                store.markPending(uuid);
                return;
            }
            long live = ExperienceUtil.getTotalExperience(online);
            ExperienceUtil.setTotalExperience(online, clampToInt(live + delta));
        }
    }

    private static int clampToInt(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }
}
