package dev.melishy.expconomy.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import dev.melishy.expconomy.Expconomy;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Loads a locale YAML file and renders messages.
 * <p>
 * A string can mix legacy ampersand codes (&amp;a, &amp;#RRGGBB) and MiniMessage
 * tags. Placeholders are {@code {0}, {1}, ...} replaced in order.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Expconomy plugin;
    private FileConfiguration locale;
    private Component prefix = Component.empty();
    private DecimalFormat numberFormat = new DecimalFormat("#,##0");

    public Messages(Expconomy plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String code = plugin.getConfig().getString("general.locale", "en_us");
        File dir = new File(plugin.getDataFolder(), "locale");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create locale folder.");
        }
        saveBundledLocale("en_us");
        if (!code.equals("en_us")) {
            saveBundledLocale(code);
        }

        File localeFile = new File(dir, code + ".yml");
        if (localeFile.exists()) {
            this.locale = YamlConfiguration.loadConfiguration(localeFile);
        } else {
            plugin.getLogger().warning("Locale '" + code + "' not found, falling back to en_us.");
            this.locale = bundledLocale("en_us");
        }
        // Layer the bundled en_us underneath as defaults so missing keys still resolve.
        FileConfiguration defaults = bundledLocale("en_us");
        if (defaults != null) {
            this.locale.setDefaults(defaults);
        }

        this.prefix = deserialize(locale.getString("prefix", ""));
        buildNumberFormat();
    }

    private void buildNumberFormat() {
        String pattern = plugin.getConfig().getString("balance.decimal-format", "#,##0");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        if (pattern.contains("@")) {
            // '@' is the original's sentinel for "no grouping".
            pattern = pattern.replace("@", "");
            this.numberFormat = new DecimalFormat(pattern.isBlank() ? "0" : pattern, symbols);
            this.numberFormat.setGroupingUsed(false);
        } else {
            this.numberFormat = new DecimalFormat(pattern.isBlank() ? "#,##0" : pattern, symbols);
        }
    }

    private void saveBundledLocale(String code) {
        File target = new File(plugin.getDataFolder(), "locale/" + code + ".yml");
        if (target.exists()) {
            return;
        }
        if (plugin.getResource("locale/" + code + ".yml") != null) {
            plugin.saveResource("locale/" + code + ".yml", false);
        }
    }

    private FileConfiguration bundledLocale(String code) {
        InputStream in = plugin.getResource("locale/" + code + ".yml");
        if (in == null) {
            return new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            return new YamlConfiguration();
        }
    }

    public String raw(String key) {
        String value = locale.getString(key);
        return value == null ? key : value;
    }

    public String currency() {
        return raw("currency");
    }

    public String currencyPlural() {
        return raw("currency-plural");
    }

    public String formatNumber(double amount) {
        return numberFormat.format(amount);
    }

    /** Build a component for a key with {@code {0}...} placeholders filled in. */
    public Component component(String key, Object... args) {
        String text = raw(key);
        for (int i = 0; i < args.length; i++) {
            String replacement = args[i] instanceof Number n ? formatNumber(n.doubleValue()) : String.valueOf(args[i]);
            // Escape values so a name or number can't inject formatting tags.
            text = text.replace("{" + i + "}", MINI.escapeTags(replacement));
        }
        return deserialize(text);
    }

    /** Send a single message with the prefix in front. */
    public void send(CommandSender to, String key, Object... args) {
        to.sendMessage(prefix.append(component(key, args)));
    }

    /** Send a line of a list (leaderboard rows, help lines) without the prefix. */
    public void sendLine(CommandSender to, String key, Object... args) {
        to.sendMessage(component(key, args));
    }

    /** Send a raw (already resolved) component without the prefix. */
    public void sendRaw(CommandSender to, Component component) {
        to.sendMessage(component);
    }

    public Component withPrefix(Component component) {
        return prefix.append(component);
    }

    /**
     * Renders text that can mix legacy &amp; codes and MiniMessage tags in one
     * string. Legacy codes become MiniMessage tags first, then MiniMessage parses
     * everything. MiniMessage leaves unknown tags like {@code <player>} as plain
     * text, so usage hints still show up.
     */
    public static Component deserialize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(legacyToMini(text));
    }

    private static final String[] LEGACY_COLORS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    private static String legacyToMini(String text) {
        StringBuilder out = new StringBuilder(text.length() + 32);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '&' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            // &#RRGGBB
            if (code == '#' && i + 7 < text.length() && isHex(text, i + 2, 6)) {
                out.append("<reset><#").append(text, i + 2, i + 8).append('>');
                i += 7;
                continue;
            }
            // &x&R&R&G&G&B&B
            if (code == 'x' && i + 13 < text.length() && isRepeatedHex(text, i + 2)) {
                out.append("<reset><#");
                for (int k = 0; k < 6; k++) {
                    out.append(text.charAt(i + 3 + k * 2));
                }
                out.append('>');
                i += 13;
                continue;
            }
            int color = Character.digit(code, 16);
            String tag = color >= 0 ? "<reset><" + LEGACY_COLORS[color] + ">" : switch (code) {
                // Legacy colors clear bold and friends, so colors emit <reset> first.
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> null;
            };
            if (tag == null) {
                out.append(c); // a plain '&', like "Tom & Jerry"
                continue;
            }
            out.append(tag);
            i++;
        }
        return out.toString();
    }

    private static boolean isHex(String s, int from, int len) {
        for (int i = from; i < from + len; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRepeatedHex(String s, int from) {
        for (int k = 0; k < 6; k++) {
            int at = from + k * 2;
            if (s.charAt(at) != '&' || Character.digit(s.charAt(at + 1), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
