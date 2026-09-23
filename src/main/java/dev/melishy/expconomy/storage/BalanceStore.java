package dev.melishy.expconomy.storage;

import dev.melishy.expconomy.Expconomy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Balances for offline players, keyed by UUID, saved to balances.yml.
 * <p>
 * Online players keep their balance on the XP bar, so this store only matters
 * while they're offline. XPConomy parsed and rewrote the vanilla .dat files,
 * which could race with the server's own saves. We avoid that.
 */
public final class BalanceStore {

    private final Expconomy plugin;
    private final File file;
    private final Map<UUID, Integer> balances = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    /** Players whose balance changed while offline. Their XP bar gets this value on join. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public BalanceStore(Expconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        load();
    }

    public synchronized void load() {
        balances.clear();
        names.clear();
        pending.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                balances.put(uuid, cfg.getInt(key + ".balance", 0));
                String name = cfg.getString(key + ".name");
                if (name != null) {
                    names.put(uuid, name);
                }
                if (cfg.getBoolean(key + ".pending", false)) {
                    pending.add(uuid);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed balance entry: " + key);
            }
        }
    }

    public synchronized void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : balances.entrySet()) {
            String key = entry.getKey().toString();
            cfg.set(key + ".balance", entry.getValue());
            String name = names.get(entry.getKey());
            if (name != null) {
                cfg.set(key + ".name", name);
            }
            if (pending.contains(entry.getKey())) {
                cfg.set(key + ".pending", true);
            }
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save balances.yml", ex);
        }
    }

    public int get(UUID uuid) {
        return balances.getOrDefault(uuid, 0);
    }

    public void put(UUID uuid, @Nullable String name, int balance) {
        balances.put(uuid, Math.max(0, balance));
        if (name != null) {
            names.put(uuid, name);
        }
    }

    public boolean isPending(UUID uuid) {
        return pending.contains(uuid);
    }

    public void markPending(UUID uuid) {
        pending.add(uuid);
    }

    public void clearPending(UUID uuid) {
        pending.remove(uuid);
    }

    /** Snapshot of all cached balances, ordered highest first, for the leaderboard. */
    public Map<UUID, Integer> sortedByBalanceDescending() {
        return balances.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll);
    }

    @Nullable
    public String nameOf(UUID uuid) {
        return names.get(uuid);
    }
}
