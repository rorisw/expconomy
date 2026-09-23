package dev.melishy.expconomy;

import dev.melishy.expconomy.command.ExpCommand;
import dev.melishy.expconomy.economy.XpEconomy;
import dev.melishy.expconomy.hook.PlaceholderHook;
import dev.melishy.expconomy.hook.VaultConnector;
import dev.melishy.expconomy.listener.PlayerListener;
import dev.melishy.expconomy.message.Messages;
import dev.melishy.expconomy.storage.BalanceStore;
import dev.melishy.expconomy.storage.BankStore;
import dev.melishy.expconomy.storage.FakeAccountStore;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Expconomy extends JavaPlugin {

    private Messages messages;
    private BalanceStore balanceStore;
    private BankStore bankStore;
    private FakeAccountStore fakeAccountStore;
    private XpEconomy economy;
    private VaultConnector vaultConnector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        this.messages = new Messages(this);
        this.balanceStore = new BalanceStore(this);
        this.bankStore = new BankStore(this);
        this.fakeAccountStore = new FakeAccountStore(this);
        this.economy = new XpEconomy(this, balanceStore);

        registerCommands();
        registerListeners();
        setupVault();
        // load: STARTUP runs before PlaceholderAPI enables. Wait until every plugin is up.
        Bukkit.getScheduler().runTask(this, this::setupPlaceholders);
        startAutosave();

        getLogger().info("Expconomy enabled.");
    }

    @Override
    public void onDisable() {
        // onEnable may have failed before creating these.
        if (economy != null) {
            Bukkit.getOnlinePlayers().forEach(economy::onQuit);
        }
        saveAll();
        if (vaultConnector != null) {
            Bukkit.getServicesManager().unregister(vaultConnector);
        }
    }

    public void reload() {
        // Save first. load() replaces memory with the file, so unsaved offline changes would vanish.
        economy.refreshOnline();
        saveAll();
        reloadConfig();
        migrateConfig();
        messages.reload();
        balanceStore.load();
        bankStore.load();
        fakeAccountStore.load();
        setupVault();
    }

    private void saveAll() {
        if (balanceStore != null) {
            balanceStore.save();
        }
        if (bankStore != null) {
            bankStore.save();
        }
        if (fakeAccountStore != null) {
            fakeAccountStore.save();
        }
    }

    /** Saves every 5 minutes so a crash loses at most 5 minutes of offline changes. */
    private void startAutosave() {
        long period = 20L * 60 * 5;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            economy.refreshOnline();
            saveAll();
        }, period, period);
    }

    private void migrateConfig() {
        if (!getConfig().isSet("config-version")) {
            getConfig().set("config-version", 1);
            saveConfig();
        }
    }

    private void registerCommands() {
        bindCommand("xpc", new ExpCommand(this));
    }

    private void bindCommand(String name, Object handler) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "command " + name + " missing from plugin.yml");
        command.setExecutor((org.bukkit.command.CommandExecutor) handler);
        if (handler instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    private void setupVault() {
        if (!getConfig().getBoolean("integrations.vault", true) || Bukkit.getPluginManager().getPlugin("Vault") == null) {
            if (vaultConnector != null) {
                Bukkit.getServicesManager().unregister(vaultConnector);
                vaultConnector = null;
            }
            return;
        }
        if (vaultConnector == null) {
            vaultConnector = new VaultConnector(this);
        }
        // Re-register at highest priority so we win as the active economy.
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> existing =
                Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (existing == null || existing.getProvider() != vaultConnector) {
            Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class,
                    vaultConnector, this, ServicePriority.Highest);
            getLogger().info("Registered Expconomy as the Vault economy provider.");
        }
    }

    private void setupPlaceholders() {
        if (getConfig().getBoolean("integrations.placeholderapi", true)
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }
    }

    /** Whether an optional feature is enabled in config (features.&lt;name&gt;, default true). */
    public boolean feature(String name) {
        return getConfig().getBoolean("features." + name, true);
    }

    public Messages messages() {
        return messages;
    }

    public XpEconomy economy() {
        return economy;
    }

    public BankStore bank() {
        return bankStore;
    }

    public FakeAccountStore fakeAccounts() {
        return fakeAccountStore;
    }
}
