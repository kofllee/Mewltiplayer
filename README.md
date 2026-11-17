# Mewltiplayer

Minecraft Java multiplayer powered by [Tailcat](https://github.com/tailscale/tailcat), without port forwarding or a separately installed VPN.

## Supported versions

| Minecraft | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| 1.20.1 | Yes | Yes | — |
| 1.21.1 | Yes | — | Yes |
| 26.3 | Yes | — | Yes |

The players joining each other must use the same Minecraft version. Fabric requires Fabric API; Mod Menu is optional. The native Tailcat helper is bundled with the mod.

## Playing together

Open a singleplayer world and use **Open to LAN** (1.20.1 / 1.21.1) or **World Options → Multiplayer** (26.3). Turn on **Share via Tailcat** and publish the world normally. Mewltiplayer displays a `tc...` invitation code in chat, with a clickable **Copy** action. Friends enter that code into Minecraft's usual **Direct Connection** or **Add Server** address field. Saved servers show their Minecraft status through encrypted on-demand probes.

The Tailcat helper runs only while needed. By default, sharing generates a new invitation code each time. The settings screen also supports persistent codes per world or across worlds; either can be reset. DERP routing can be left automatic or configured with an HTTPS map URL or an editable JSON map and a preferred region.

## Commands and settings

`/mew` or `/mew status` shows connection information, including Direct P2P or DERP when known. `/mew settings` opens settings for clients. When hosting a singleplayer world, `/mew code`, `/mew start`, `/mew stop`, and `/mew reset` control sharing.

Settings also open from the vanilla LAN/world-sharing screen and, on Fabric, from optional **Mod Menu**. Each world's Share via Tailcat choice is saved independently.

Dedicated servers have `/mew` console commands and `/mewserver` operator commands (status, code, start, stop, reset, settings). Server sharing is enabled by default and can be controlled through `config/mewltiplayer/server.json`; codes and identities persist across ordinary server restarts. Treat server logs and invitation codes as sensitive.

## Development

Choose a target per invocation; switching versions does not require changing source code or editing a global version setting:

```powershell
.\dev.cmd fabric-1.20.1 :platform:runClient
.\dev.cmd forge-1.20.1 :platform:runClient
.\dev.cmd fabric-1.21.1 :platform:runClient
.\dev.cmd neoforge-1.21.1 :platform:runClient
.\dev.cmd fabric-26.3 :platform:runClient
.\dev.cmd neoforge-26.3 :platform:runClient
```

Replace `:platform:runClient` with `:platform:runServer` to run a dedicated development server, or with `:core:test :platform:classes` to compile and test without producing a mod JAR. `.\dev.cmd fabric-26.3 :showTarget` lists all available targets.

The build uses JDK 25, Java 17/21 toolchains for older versions, Gradle Wrapper, and Go 1.27 for the bundled helper. Minecraft requires Java 17 for 1.20.1, Java 21 for 1.21.1, and Java 25 for 26.3.
