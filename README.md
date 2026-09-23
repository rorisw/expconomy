# Expconomy

An XP economy for Paper. Your experience bar is your balance. Pay other players with `/xpc pay`, or keep XP safe in a bank.

Inspired by XPConomy. Expconomy is a new plugin written from scratch, not a fork.

Built for duping servers. Item economies collapse once people start duping diamonds, and XP is much harder to dupe.

## Features

- Registers as a Vault economy, so shops, jobs and land claim plugins charge and pay in XP.
- A personal bank. Banked XP doesn't drop when you die.
- A leaderboard with `/xpc top`.
- Offline payments. If someone pays you while you're offline, you get the XP when you join.
- Payments sent to or from a player on the death screen apply when they respawn.
- Messages support `&` codes, hex colors and MiniMessage.
- PlaceholderAPI placeholders.

## Requirements

- Paper 1.21.4 or newer (or a Paper fork)
- Java 21 or newer
- [Vault](https://www.spigotmc.org/resources/vault.34315/) (optional, for other plugins to use the economy)
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (optional)

## Commands

The main command is `/xpc`, with `/expconomy` as an alias. EssentialsX already owns `/exp` and vanilla owns `/xp`.

| Command | Description | Permission |
| --- | --- | --- |
| `/xpc` or `/xpc balance` | Show your balance | `exp.balance` |
| `/xpc balance <player>` | Show another player's balance | `exp.balance.others` |
| `/xpc give <player> <amount>` | Give XP to a player | `exp.give` |
| `/xpc pay <player> <amount>` | Same as give | `exp.pay` or `exp.give` |
| `/xpc top [page]` | Show the leaderboard | `exp.top` |
| `/xpc deposit <amount>` | Deposit XP into your bank | `exp.bank` |
| `/xpc withdraw <amount>` | Withdraw XP from your bank | `exp.bank` |
| `/xpc bank [player]` | Show a bank balance | `exp.bank` / `exp.bank.others` |
| `/xpc add <player> <amount>` | Add XP to a player | `exp.add` |
| `/xpc remove <player> <amount>` | Remove XP from a player | `exp.remove` |
| `/xpc set <player> <amount>` | Set a player's XP | `exp.set` |
| `/xpc reload` | Reload the config | `expconomy.reload` |

`exp.admin` grants every admin node.

## Placeholders

These need PlaceholderAPI:

- `%expconomy_balance%`: XP balance as a plain number
- `%expconomy_balance_formatted%`: balance with the number format from the config
- `%expconomy_currency%`: currency name

## Death

With `death.modify-drops: true`, a player drops `death.drop-percentage` of their XP as orbs and respawns with the rest. The default drop is 10%. With `keepInventory` on, players keep all their XP. Banked XP never drops.

## Building

You need JDK 25. The Gradle wrapper downloads everything else.

```sh
./gradlew shadowJar
```

The jar goes to `build/libs/Expconomy-<version>.jar`. The build targets Java 21, so the jar runs on Java 21 and Java 25 servers.

## Configuration

See [config.yml](src/main/resources/config.yml). You can switch off whole features under `features:`. A disabled feature disappears from tab completion, and its commands tell the player it's off.

```yaml
features:
  give: true        # /xpc give and /xpc pay
  leaderboard: true # /xpc top
  bank: true        # /xpc deposit, /xpc withdraw, /xpc bank
```

The file also covers the max balance, number format, bank cap, death drops, integrations and leaderboard page size. Messages are in [locale/en_us.yml](src/main/resources/locale/en_us.yml).

Run `/xpc reload` after editing.

## License

Expconomy is free software under the [GNU General Public License v3.0 only](LICENSE) (`GPL-3.0-only`). You can use, change and share it, even commercially. If you share a modified version, you have to release its source code under the same license.

Copyright © 2026 Melishy (Rori Softworks).
