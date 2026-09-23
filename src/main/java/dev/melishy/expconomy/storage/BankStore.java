package dev.melishy.expconomy.storage;

import dev.melishy.expconomy.Expconomy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Each player's XP bank, saved to bank.yml. Deaths only take XP from the bar,
 * so banked XP never drops.
 */
public final class BankStore {

    private final Expconomy plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, Integer> banks = new ConcurrentHashMap<>();

    public BankStore(Expconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bank.yml");
        load();
    }

    public void load() {
        banks.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                banks.put(UUID.fromString(key), cfg.getInt(key));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed bank entry: " + key);
            }
        }
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        banks.forEach((uuid, balance) -> cfg.set(uuid.toString(), balance));
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save bank.yml", ex);
        }
    }

    public int get(UUID uuid) {
        return banks.getOrDefault(uuid, 0);
    }

    /** Add to a player's bank, returning the new banked total (capped at Integer.MAX_VALUE). */
    public synchronized int deposit(UUID uuid, int amount) {
        long sum = (long) get(uuid) + Math.max(0, amount);
        int updated = (int) Math.min(Integer.MAX_VALUE, sum);
        banks.put(uuid, updated);
        return updated;
    }

    /**
     * Remove up to {@code amount} from a player's bank.
     *
     * @return the amount actually withdrawn (never more than the banked total)
     */
    public synchronized int withdraw(UUID uuid, int amount) {
        int current = get(uuid);
        int taken = Math.min(Math.max(0, amount), current);
        banks.put(uuid, current - taken);
        return taken;
    }
}
