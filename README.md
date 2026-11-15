# Mewltiplayer

Minecraft Java multiplayer powered by Tailcat, without port forwarding or a separate VPN.

Fabric targets: Minecraft 26.3 and 1.21.1.

Open a singleplayer world and use Minecraft's existing **Open to LAN** (1.21.1) or **World Options → Multiplayer** (26.3). Enable **Share via Tailcat** and publish normally. The invite is copied to your clipboard. Friends paste the native `tc...` code into Minecraft's normal server address field.

The Tailcat helper starts only when needed and is included in the JAR. Saved Tailcat servers show the actual Minecraft status (players, MOTD, version and latency) through on-demand encrypted status probes.

## Controls and settings

`/mew status` (also `/mew`) and `/mew settings` are available to every client, including guests without operator access. In a shared singleplayer world, the host additionally has `/mew invite`, `/mew start`, `/mew stop` and `/mew reset`. The status command includes Direct P2P / DERP route information when a Tailcat connection is active and the measurement is available.

The settings screen opens from the normal world-sharing menu or from Mod Menu (optional). The **Tailcat on/off** selection is saved for each world even when LAN is not enabled. Settings provide three invitation modes: a new code for every share (default), a persistent code for each world, or one shared code across worlds. Persistent identities are kept when switching modes and can be reset explicitly. DERP routing supports Auto, an editable JSON map, or a custom HTTPS map URL, with a selectable region (Auto by default).

On dedicated servers, the server console uses `/mew`, while operators can use `/mewserver` to avoid conflicts with the client-only `/mew` commands. Server commands offer status, invite, start, stop, reset and settings; they require administrator permissions. `/mew settings` on the server points to `config/mewltiplayer/settings.json` and `server.json`, rather than opening a graphical screen. Dedicated server invitations and identity survive a normal restart. `/mew reset` revokes the previous invitation and generates a new one.

Chat uses a small custom pixel cat icon. While Tailcat sharing starts, one animated chat line updates in place; it disappears on completion. Once shared, the game shows a clear success message and a separate invitation message with a clickable `[Copy]` link. No action bar loading message or animated guest connecting screen is used.

## Build

```powershell
.\dev.cmd fabric-26.3 :platform:build
.\dev.cmd fabric-1.21.1 :platform:build
```

Build requirements: JDK 25 and Go 1.27. Fabric API is required to play.
