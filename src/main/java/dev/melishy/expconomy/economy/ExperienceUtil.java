package dev.melishy.expconomy.economy;

import org.bukkit.entity.Player;

/**
 * Conversions between Minecraft levels and total experience points.
 * <p>
 * Vanilla splits the XP curve into three bands. The per level cost is:
 * <ul>
 *     <li>levels 0-15 : {@code 2 * level + 7}</li>
 *     <li>levels 16-30: {@code 5 * level - 38}</li>
 *     <li>levels 31+  : {@code 9 * level - 158}</li>
 * </ul>
 * The total to reach a level is the closed form of those sums.
 */
public final class ExperienceUtil {

    private ExperienceUtil() {
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public static int xpForLevelUp(int level) {
        if (level >= 31) {
            return 9 * level - 158;
        }
        if (level >= 16) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }

    /** Total XP accumulated to reach the start of {@code level}. Uses long so level 21,000+ can't overflow. */
    public static long xpAtLevel(int level) {
        long l = level;
        // Both products below are always even, so the division is exact.
        if (level >= 32) {
            return l * (9 * l - 325) / 2 + 2220;
        }
        if (level >= 17) {
            return l * (5 * l - 81) / 2 + 360;
        }
        return l * l + 6 * l;
    }

    /** Total XP for a level plus bar progress (0.0 - 1.0), capped at {@link Integer#MAX_VALUE}. */
    public static int totalFromLevel(int level, float progress) {
        long total = xpAtLevel(Math.max(0, level)) + Math.round(progress * xpForLevelUp(level));
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, total));
    }

    /** The level a player with {@code totalXp} total experience sits at. */
    public static int levelFromTotal(int totalXp) {
        int level = 0;
        int remaining = Math.max(0, totalXp);
        while (remaining >= xpForLevelUp(level)) {
            remaining -= xpForLevelUp(level);
            level++;
        }
        return level;
    }

    /** The progress bar fraction {@code (0.0 - 1.0)} for a player with {@code totalXp}. */
    public static float progressFromTotal(int totalXp) {
        int level = 0;
        int remaining = Math.max(0, totalXp);
        while (remaining >= xpForLevelUp(level)) {
            remaining -= xpForLevelUp(level);
            level++;
        }
        int cost = xpForLevelUp(level);
        return cost == 0 ? 0f : (float) remaining / cost;
    }

    /**
     * Total experience of an online player.
     * 
     * {@link Player#getTotalExperience()} only counts XP gained, not XP spent on
     * enchanting, so it drifts from the visible bar. Recomputing from the level and
     * the bar progress is the accurate value.
     */
    public static int getTotalExperience(Player player) {
        return totalFromLevel(player.getLevel(), player.getExp());
    }

    /** Overwrite an online player's experience to exactly {@code totalXp}. */
    public static void setTotalExperience(Player player, int totalXp) {
        int clamped = Math.max(0, totalXp);
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);

        int level = levelFromTotal(clamped);
        player.setLevel(level);
        player.setExp(progressFromTotal(clamped));
        // Keep the "total experience" counter in sync for plugins that read it.
        player.setTotalExperience(clamped);
    }
}
