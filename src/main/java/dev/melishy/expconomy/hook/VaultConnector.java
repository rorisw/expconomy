package dev.melishy.expconomy.hook;

import dev.melishy.expconomy.Expconomy;
import dev.melishy.expconomy.economy.XpEconomy;
import dev.melishy.expconomy.storage.FakeAccountStore;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Vault economy provider backed by player XP.
 * <p>
 * Names that match a player use their XP through {@link XpEconomy}. Other names
 * (towns, shops) get a separate account in {@link FakeAccountStore}.
 * <p>
 * We extend {@link AbstractEconomy} so new Vault methods get default
 * implementations and don't break the build.
 */
// Vault's name-based methods (getBalance(String) and friends) are deprecated,
// but they're part of the Economy interface and some plugins still call them.
@SuppressWarnings("deprecation")
public final class VaultConnector extends AbstractEconomy {

    private final Expconomy plugin;

    public VaultConnector(Expconomy plugin) {
        this.plugin = plugin;
    }

    private XpEconomy econ() {
        return plugin.economy();
    }

    private FakeAccountStore fake() {
        return plugin.fakeAccounts();
    }

    /** Players the server has seen, or null. Every other name is a fake account. */
    @Nullable
    private OfflinePlayer resolve(@Nullable String name) {
        return name == null ? null : Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "Expconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return plugin.messages().formatNumber(amount) + " " + plugin.messages().currency();
    }

    @Override
    public String currencyNamePlural() {
        return plugin.messages().currencyPlural();
    }

    @Override
    public String currencyNameSingular() {
        return plugin.messages().currency();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = resolve(playerName);
        return player != null ? econ().getBalance(player) : fake().get(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return player == null ? 0 : econ().getBalance(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String world, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = resolve(playerName);
        if (player != null) {
            return withdrawPlayer(player, amount);
        }
        // Fake accounts keep decimals.
        String invalid = checkAmount(amount);
        if (invalid != null) {
            return fail(fake().get(playerName), invalid);
        }
        synchronized (fake()) {
            double balance = fake().get(playerName);
            if (balance < amount) {
                return fail(balance, "Insufficient funds.");
            }
            double now = balance - amount;
            fake().set(playerName, now);
            return new EconomyResponse(amount, now, EconomyResponse.ResponseType.SUCCESS, null);
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return fail(0, "Unknown player.");
        }
        String invalid = checkAmount(amount);
        if (invalid != null) {
            return fail(econ().getBalance(player), invalid);
        }
        // XP has no decimals. Round up so a 0.5 price still costs 1 XP.
        double rounded = Math.ceil(amount);
        if (rounded > Integer.MAX_VALUE) {
            return fail(econ().getBalance(player), "Insufficient XP.");
        }
        int change = (int) rounded;
        if (!econ().change(player, -change)) {
            return fail(econ().getBalance(player), "Insufficient XP.");
        }
        return new EconomyResponse(change, econ().getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String world, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = resolve(playerName);
        if (player != null) {
            return depositPlayer(player, amount);
        }
        String invalid = checkAmount(amount);
        if (invalid != null) {
            return fail(fake().get(playerName), invalid);
        }
        synchronized (fake()) {
            double now = fake().get(playerName) + amount;
            fake().set(playerName, now);
            return new EconomyResponse(amount, now, EconomyResponse.ResponseType.SUCCESS, null);
        }
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return fail(0, "Unknown player.");
        }
        String invalid = checkAmount(amount);
        if (invalid != null) {
            return fail(econ().getBalance(player), invalid);
        }
        // Round down so deposits never create XP out of fractions.
        int whole = (int) Math.min(Integer.MAX_VALUE, Math.floor(amount));
        int added = econ().add(player, whole);
        return new EconomyResponse(added, econ().getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String world, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse fail(double balance, String message) {
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, message);
    }

    /** Returns an error message, or null if the amount is a usable number. */
    @Nullable
    private static String checkAmount(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return "Invalid amount.";
        }
        if (amount < 0) {
            return "Amount can't be negative.";
        }
        return null;
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String world) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return true;
    }

    // --- Banks: unsupported ---

    @Override
    public EconomyResponse createBank(String name, String player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    private EconomyResponse unsupportedBank() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Expconomy does not support banks.");
    }
}
