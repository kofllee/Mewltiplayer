# Mewltiplayer

Minecraft Java multiplayer via Tailcat, without port forwarding or a separate VPN.

Work in progress. Fabric targets: Minecraft 26.3 and 1.21.1. Tailcat connections are not implemented yet.

## Build

```powershell
.\gradlew.bat :platform:build
.\gradlew.bat '-Ptarget=fabric-1.21.1' :platform:build
```

Requires JDK 25. On Windows, `dev.cmd fabric-26.3 :platform:build` uses the local JDK.

Builds are saved to `targets/<target>/build/libs/`.
