package dev.melishy.expconomy.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import dev.melishy.expconomy.Expconomy;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion exposing balances and levels.
 * <p>
 * Placeholders:
 * <ul>
 *     <li>{@code %expconomy_balance%} raw XP balance</li>
 *     <li>{@code %expconomy_balance_formatted%} balance with the configured number format</li>
 *     <li>{@code %expconomy_currency%} currency name</li>
 * </ul>
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private final Expconomy plugin;

    public PlaceholderHook(Expconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "expconomy";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Melishy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        int balance = plugin.economy().getBalance(player);
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "balance" -> Integer.toString(balance);
            case "balance_formatted" -> plugin.messages().formatNumber(balance);
            case "currency" -> plugin.messages().currency();
            default -> null;
        };
    }
}
