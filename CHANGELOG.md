# Changelog

## [Unreleased]

## [1.0.0]

First release.

- Use XP as money. `/xpc pay` sends XP to other players, including offline ones.
- A personal bank. Banked XP doesn't drop on death.
- Vault economy provider, so shops and other plugins charge and pay in XP.
- Configurable death drops. `keepInventory` keeps all XP.
- `/xpc top` leaderboard.
- PlaceholderAPI placeholders: `%expconomy_balance%`, `%expconomy_balance_formatted%`, `%expconomy_currency%`.
- Messages support `&` color codes, hex colors and MiniMessage in the same line.
- Payments and bank transfers run in one locked step. Bank deposits and offline payments save right away, so a server crash can't duplicate XP.
