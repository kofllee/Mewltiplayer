# Mewltiplayer

Minecraft Java multiplayer powered by Tailcat, without port forwarding, a separate VPN, or an account.

Fabric builds: Minecraft 26.3 and 1.21.1.

## Play

Open a singleplayer world, then choose **Pause → Mewltiplayer → Share world**. Copy the invite and send it privately. Friends choose **Multiplayer → Mewltiplayer**, paste the `tc...` code, and connect. Codes also work in Minecraft's normal Direct Connect and saved server address fields.

Use **Settings** to control LAN auto-sharing, notifications, retries, and DERP relays. Dedicated servers publish an invite in `config/mewltiplayer/invite.txt` and keep their identity across restarts. Delete `server-identity.json` while the server is stopped to revoke a permanent invitation.

## Build

```powershell
.\dev.cmd fabric-26.3 :platform:build
.\dev.cmd fabric-1.21.1 :platform:build
```

Requires JDK 25 and Go 1.27 to build. The resulting JAR includes Tailcat helpers for Windows, Linux, and macOS (x64/ARM64); players do not install Tailcat separately.

Tailcat is BSD-3-Clause licensed; notices are bundled under `META-INF/licenses/`.
