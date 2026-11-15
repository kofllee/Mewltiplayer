# Mewltiplayer

Minecraft Java multiplayer via Tailcat, without port forwarding or a separate VPN.

Work in progress. Fabric targets: Minecraft 26.3 and 1.21.1. Share a singleplayer world from the pause menu, copy its `tc...` invite, and join from Multiplayer using the invite as a server address. Dedicated servers can publish a persistent invite.

## Build

```powershell
.\dev.cmd fabric-26.3 :platform:build
.\dev.cmd fabric-1.21.1 :platform:build
.\helper.cmd build
```

Requires JDK 25 and Fabric API. The Tailcat helper is currently a separate executable: place it at `config/mewltiplayer/mewltiplayer-helper.exe` on Windows, or supply its absolute path via `MEWLTIPLAYER_HELPER`. Native builds are not bundled yet.

Dedicated servers automatically create `config/mewltiplayer/server.json` and write the invite to `config/mewltiplayer/invite.txt`. Keep invitations private. Set `enabled` to `false` in `server.json` to disable sharing.
