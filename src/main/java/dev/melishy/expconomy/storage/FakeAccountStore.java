package dev.melishy.expconomy.storage;

import dev.melishy.expconomy.Expconomy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Balances for Vault accounts that aren't players, like towns and shops.
 * <p>
 * Saved as YAML. XPConomy used Java serialization to a .dat file, which breaks
 * easily and can run untrusted code on load.
 */
public final class FakeAccountStore {

    private final Expconomy plugin;
    private final File file;
    private final Map<String, Double> accounts = new ConcurrentHashMap<>();

    public FakeAccountStore(Expconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "accounts.yml");
        load();
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public void load() {
        accounts.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String name : cfg.getKeys(false)) {
            accounts.put(key(name), cfg.getDouble(name));
        }
    }

    public synchronized void save() {
        FileConfiguration cfg = new YamlConfiguration();
        accounts.forEach(cfg::set);
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save accounts.yml", ex);
        }
    }

    public double get(String name) {
        return accounts.getOrDefault(key(name), 0.0);
    }

    /** Saved by the autosave task and on shutdown, not on every call. */
    public void set(String name, double balance) {
        if (Double.isNaN(balance) || Double.isInfinite(balance)) {
            return;
        }
        accounts.put(key(name), Math.max(0.0, balance));
    }
}
