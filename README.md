# Mewltiplayer

Minecraft Java multiplayer powered by Tailcat, without port forwarding or a separate VPN.

Fabric targets: Minecraft 26.3 and 1.21.1.

Open a singleplayer world and use Minecraft's existing **Open to LAN** (1.21.1) or **World Options → Multiplayer** (26.3). Enable **Share via Tailcat** and publish normally. The invite is copied to your clipboard. Friends paste the native `tc...` code into Minecraft's normal server address field.

The Tailcat helper starts only when needed and is included in the JAR. Advanced relay configuration remains available in `config/mewltiplayer/settings.json`.

## Build

```powershell
.\dev.cmd fabric-26.3 :platform:build
.\dev.cmd fabric-1.21.1 :platform:build
```

Build requirements: JDK 25 and Go 1.27. Fabric API is required to play.
