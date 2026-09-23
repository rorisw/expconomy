package dev.melishy.expconomy.command;

import dev.melishy.expconomy.Expconomy;
import dev.melishy.expconomy.economy.XpEconomy;
import dev.melishy.expconomy.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "balance", "give", "pay", "top", "deposit", "withdraw", "bank",
            "add", "remove", "set", "reload", "help");

    private final Expconomy plugin;

    public ExpCommand(Expconomy plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private XpEconomy econ() {
        return plugin.economy();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return balance(sender, new String[]{"balance"});
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String feature = featureOf(sub);
        if (feature != null && !plugin.feature(feature)) {
            msg().send(sender, "command.disabled");
            return true;
        }
        return switch (sub) {
            case "balance", "bal" -> balance(sender, args);
            case "give", "pay" -> give(sender, args, sub);
            case "top" -> top(sender, args);
            case "deposit", "dep" -> deposit(sender, args);
            case "withdraw", "with" -> withdraw(sender, args);
            case "bank" -> bank(sender, args);
            case "add" -> adminChange(sender, args, Op.ADD);
            case "remove" -> adminChange(sender, args, Op.REMOVE);
            case "set" -> adminChange(sender, args, Op.SET);
            case "reload" -> reload(sender);
            case "help" -> help(sender);
            default -> {
                msg().send(sender, "command.unknown");
                yield true;
            }
        };
    }

    /** Map a subcommand to its config feature key, or null if it's always on. */
    private static String featureOf(String sub) {
        return switch (sub) {
            case "give", "pay" -> "give";
            case "top" -> "leaderboard";
            case "deposit", "dep", "withdraw", "with", "bank" -> "bank";
            default -> null;
        };
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission("exp.balance.others")) {
                yieldNoPermission(sender);
                return true;
            }
            OfflinePlayer target = resolve(args[1]);
            if (target == null) {
                msg().send(sender, "error.unknown-player");
                return true;
            }
            msg().send(sender, "balance.other", target.getName(), econ().getBalance(target));
            return true;
        }
        if (!(sender instanceof Player player)) {
            msg().send(sender, "console.player-only");
            return true;
        }
        if (!player.hasPermission("exp.balance")) {
            yieldNoPermission(sender);
            return true;
        }
        msg().send(sender, "balance.self", econ().getBalance(player));
        return true;
    }

    private boolean give(CommandSender sender, String[] args, String sub) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "console.player-only");
            return true;
        }
        // /xpc pay accepts exp.pay or exp.give, /xpc give needs exp.give.
        boolean allowed = player.hasPermission("exp.give")
                || (sub.equals("pay") && player.hasPermission("exp.pay"));
        if (!allowed) {
            yieldNoPermission(sender);
            return true;
        }
        if (args.length != 3) {
            msg().send(sender, "help." + sub);
            return true;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            msg().send(sender, "error.unknown-player");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            msg().send(sender, "give.self");
            return true;
        }
        Integer amount = parseAmount(sender, args[2]);
        if (amount == null) {
            return true;
        }
        // One locked step: check, take and credit together.
        int moved = econ().transfer(player, target, amount);
        if (moved <= 0) {
            msg().send(sender, econ().has(player, amount) ? "error.max-balance" : "give.insufficient");
            return true;
        }
        msg().send(sender, "give.sent", moved, target.getName());
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            msg().send(onlineTarget, "give.received", moved, player.getName());
        }
        if (onlineTarget == null || onlineTarget.isDead()) {
            // The target's XP lives in balances.yml. Save it and the sender's
            // player file together, so a crash can't restore the sender's XP
            // while keeping the payment.
            econ().store().save();
            player.saveData();
        }
        return true;
    }

    private boolean top(CommandSender sender, String[] args) {
        if (!sender.hasPermission("exp.top")) {
            yieldNoPermission(sender);
            return true;
        }
        // Online balances change with every orb, so copy them in before sorting.
        econ().refreshOnline();
        Map<java.util.UUID, Integer> sorted = econ().store().sortedByBalanceDescending();
        if (sorted.isEmpty()) {
            msg().send(sender, "top.empty");
            return true;
        }
        int pageSize = Math.max(1, plugin.getConfig().getInt("leaderboard.page-size", 10));
        int pages = (sorted.size() + pageSize - 1) / pageSize;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        msg().sendLine(sender, "top.header", page, pages);
        int start = (page - 1) * pageSize;
        int rank = start + 1;
        int shown = 0;
        for (Map.Entry<java.util.UUID, Integer> entry : sorted.entrySet()) {
            if (shown < start) {
                shown++;
                continue;
            }
            if (rank > start + pageSize) {
                break;
            }
            String name = econ().store().nameOf(entry.getKey());
            if (name == null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                name = op.getName() != null ? op.getName() : entry.getKey().toString().substring(0, 8);
            }
            msg().sendLine(sender, "top.entry", rank, name, entry.getValue());
            rank++;
            shown++;
        }
        msg().sendLine(sender, "top.footer");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("expconomy.reload")) {
            yieldNoPermission(sender);
            return true;
        }
        plugin.reload();
        msg().send(sender, "command.reloaded");
        return true;
    }

    private boolean deposit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "console.player-only");
            return true;
        }
        if (!player.hasPermission("exp.bank")) {
            yieldNoPermission(sender);
            return true;
        }
        if (args.length != 2) {
            msg().send(sender, "help.deposit");
            return true;
        }
        Integer amount = parseAmount(sender, args[1]);
        if (amount == null) {
            return true;
        }
        if (!econ().has(player, amount)) {
            msg().send(sender, "bank.insufficient-balance");
            return true;
        }
        int bankMax = plugin.getConfig().getInt("bank.maximum", 0);
        int cap = bankMax <= 0 ? Integer.MAX_VALUE : bankMax;
        int room = cap - plugin.bank().get(player.getUniqueId());
        if (room <= 0) {
            msg().send(sender, "bank.full");
            return true;
        }
        if (amount > room) {
            amount = room;
            msg().send(sender, "bank.overflow");
        }
        // Move XP off the live bar into the bank.
        if (!econ().change(player, -amount)) {
            msg().send(sender, "bank.insufficient-balance");
            return true;
        }
        int banked = plugin.bank().deposit(player.getUniqueId(), amount);
        saveBankAndPlayer(player);
        msg().send(sender, "bank.deposited", amount, banked);
        return true;
    }

    /**
     * bank.yml and the player file must save together. Otherwise a crash
     * between them keeps the banked XP and gives back the XP bar (a dupe).
     */
    private void saveBankAndPlayer(Player player) {
        plugin.bank().save();
        econ().store().save();
        player.saveData();
    }

    private boolean withdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "console.player-only");
            return true;
        }
        if (!player.hasPermission("exp.bank")) {
            yieldNoPermission(sender);
            return true;
        }
        if (args.length != 2) {
            msg().send(sender, "help.withdraw");
            return true;
        }
        Integer amount = parseAmount(sender, args[1]);
        if (amount == null) {
            return true;
        }
        if (plugin.bank().get(player.getUniqueId()) < amount) {
            msg().send(sender, "bank.insufficient-bank");
            return true;
        }
        int taken = plugin.bank().withdraw(player.getUniqueId(), amount);
        int added = econ().add(player, taken);
        if (added < taken) {
            // Live balance hit the cap, put the remainder back in the bank.
            plugin.bank().deposit(player.getUniqueId(), taken - added);
            msg().send(sender, "error.max-balance");
        }
        saveBankAndPlayer(player);
        msg().send(sender, "bank.withdrew", added, plugin.bank().get(player.getUniqueId()));
        return true;
    }

    private boolean bank(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission("exp.bank.others")) {
                yieldNoPermission(sender);
                return true;
            }
            OfflinePlayer target = resolve(args[1]);
            if (target == null) {
                msg().send(sender, "error.unknown-player");
                return true;
            }
            msg().send(sender, "bank.balance-other", target.getName(), plugin.bank().get(target.getUniqueId()));
            return true;
        }
        if (!(sender instanceof Player player)) {
            msg().send(sender, "console.player-only");
            return true;
        }
        if (!player.hasPermission("exp.bank")) {
            yieldNoPermission(sender);
            return true;
        }
        msg().send(sender, "bank.balance", plugin.bank().get(player.getUniqueId()));
        return true;
    }

    private enum Op {ADD, REMOVE, SET}

    private boolean adminChange(CommandSender sender, String[] args, Op op) {
        String node = switch (op) {
            case ADD -> "exp.add";
            case REMOVE -> "exp.remove";
            case SET -> "exp.set";
        };
        if (!sender.hasPermission(node)) {
            yieldNoPermission(sender);
            return true;
        }
        if (args.length != 3) {
            msg().send(sender, "help." + op.name().toLowerCase(Locale.ROOT));
            return true;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            msg().send(sender, "error.unknown-player");
            return true;
        }
        Integer amount = parseAmount(sender, args[2]);
        if (amount == null) {
            return true;
        }
        Player online = target.getPlayer();
        switch (op) {
            case ADD -> {
                int added = econ().add(target, amount);
                msg().send(sender, "admin.added", added, target.getName());
                if (online != null) {
                    msg().send(online, "admin.added-target", added);
                }
            }
            case REMOVE -> {
                int removed = econ().remove(target, amount);
                msg().send(sender, "admin.removed", removed, target.getName());
                if (online != null) {
                    msg().send(online, "admin.removed-target", removed);
                }
            }
            case SET -> {
                int now = econ().setBalance(target, amount);
                msg().send(sender, "admin.set", target.getName(), now);
                if (online != null) {
                    msg().send(online, "admin.set-target", now);
                }
            }
        }
        return true;
    }

    private boolean help(CommandSender sender) {
        if (!sender.hasPermission("exp.help")) {
            yieldNoPermission(sender);
            return true;
        }
        msg().sendRaw(sender, msg().component("help.header"));
        msg().sendRaw(sender, msg().component("help.exp"));
        msg().sendRaw(sender, msg().component("help.balance"));
        if (plugin.feature("give")) {
            msg().sendRaw(sender, msg().component("help.give"));
            msg().sendRaw(sender, msg().component("help.pay"));
        }
        if (plugin.feature("leaderboard")) {
            msg().sendRaw(sender, msg().component("help.top"));
        }
        if (plugin.feature("bank")) {
            msg().sendRaw(sender, msg().component("help.deposit"));
            msg().sendRaw(sender, msg().component("help.withdraw"));
            msg().sendRaw(sender, msg().component("help.bank"));
        }
        if (sender.hasPermission("exp.add")) {
            msg().sendRaw(sender, msg().component("help.add"));
        }
        if (sender.hasPermission("exp.remove")) {
            msg().sendRaw(sender, msg().component("help.remove"));
        }
        if (sender.hasPermission("exp.set")) {
            msg().sendRaw(sender, msg().component("help.set"));
        }
        if (sender.hasPermission("expconomy.reload")) {
            msg().sendRaw(sender, msg().component("help.reload"));
        }
        return true;
    }

    private void yieldNoPermission(CommandSender sender) {
        sender.sendMessage(msg().withPrefix(Messages.deserialize("&cYou don't have permission.")));
    }

    /**
     * Only players the server has seen (Paper's user cache). No loop over every
     * player file, which let anyone lag the server with /xpc pay randomname 1.
     */
    @Nullable
    private OfflinePlayer resolve(String name) {
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    @Nullable
    private Integer parseAmount(CommandSender sender, String raw) {
        int amount;
        try {
            amount = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            msg().send(sender, "error.invalid-amount");
            return null;
        }
        if (amount <= 0) {
            msg().send(sender, "error.negative-amount");
            return null;
        }
        return amount;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS.stream()
                    .filter(this::featureActive)
                    .filter(s -> hasSubPermission(sender, s))
                    .toList(), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (List.of("give", "pay", "balance", "bal", "add", "remove", "set", "bank").contains(sub)) {
                return filter(onlineNames(), args[1]);
            }
            if (List.of("deposit", "dep", "withdraw", "with").contains(sub)) {
                return filter(List.of("<amount>"), args[1]);
            }
            if (sub.equals("top")) {
                return filter(List.of("1", "2", "3"), args[1]);
            }
        }
        if (args.length == 3 && List.of("give", "pay", "add", "remove", "set").contains(sub)) {
            return filter(List.of("<amount>"), args[2]);
        }
        return List.of();
    }

    private boolean featureActive(String sub) {
        String feature = featureOf(sub);
        return feature == null || plugin.feature(feature);
    }

    private boolean hasSubPermission(CommandSender sender, String sub) {
        return switch (sub) {
            case "balance" -> sender.hasPermission("exp.balance") || sender.hasPermission("exp.balance.others");
            case "give" -> sender.hasPermission("exp.give");
            case "pay" -> sender.hasPermission("exp.pay") || sender.hasPermission("exp.give");
            case "top" -> sender.hasPermission("exp.top");
            case "deposit", "withdraw" -> sender.hasPermission("exp.bank");
            case "bank" -> sender.hasPermission("exp.bank") || sender.hasPermission("exp.bank.others");
            case "add" -> sender.hasPermission("exp.add");
            case "remove" -> sender.hasPermission("exp.remove");
            case "set" -> sender.hasPermission("exp.set");
            case "reload" -> sender.hasPermission("expconomy.reload");
            case "help" -> sender.hasPermission("exp.help");
            default -> false;
        };
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
