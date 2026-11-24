# Commands

Mewltiplayer has separate commands for singleplayer hosts and dedicated servers.

## In-game commands

| Command | Description |
| --- | --- |
| `/mew` | Show current connection status |
| `/mew status` | Show the connection route and latency; when hosting, list connected players and their routes |
| `/mew code` | Copy the current share code |
| `/mew start` | Start Tailcat sharing |
| `/mew stop` | Stop Tailcat sharing |
| `/mew reset` | Replace the current share code |
| `/mew settings` | Open Mewltiplayer Settings |

Hosting commands require a local singleplayer world. Open the world to LAN first before using `/mew start`. The `code`, `start`, `stop`, and `reset` subcommands are only available while a local world is open. Resetting requires active sharing.

## Dedicated servers

Install the mod on the dedicated server with the matching Minecraft version and loader. By default, the server starts Tailcat sharing automatically and prints the code in its console. Its saved identity lets the code remain usable across restarts. You can disable automatic sharing in `config/mewltiplayer/server.json`.

Run server commands in the console or as an operator (permission level 2 or higher):

| Command | Description |
| --- | --- |
| `/mewserver` | Show server status |
| `/mewserver status` | Show sharing status and connected players |
| `/mewserver code` | Show the share code (or a clickable Copy action in game) |
| `/mewserver start` | Start sharing |
| `/mewserver stop` | Stop sharing without stopping Minecraft |
| `/mewserver reset` | Replace the saved server share code |
| `/mewserver settings` | Show DERP settings and configuration file paths |

The server also registers all the equivalent `/mew` commands. Use `/mewserver` when administering a dedicated server in game, so it is not confused with the client's own `/mew` command. In server consoles, omit the leading slash if required.

Keep share codes and server console logs containing them private.

[Back to main page](../README.md) | [Settings & errors](settings.md)
