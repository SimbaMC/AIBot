AiBot 1.5.1-gtnh284 - Minecraft 1.7.10 / GT New Horizons 2.8.4

AiBot bridges a Forge server to QQ through a OneBot 11 WebSocket and includes optional AI chat/death translation, QQ account binding, achievements, join/leave/chat/death sync, and client-side playback of server-provided music URLs.

Build
-----
Use JDK 17+ to run the bundled RetroFuturaGradle wrapper:
  gradlew build
The produced classes and distributable jar target Java 8. Forge is exactly 1.7.10-10.13.4.1614-1.7.10.

Configuration
-------------
config/aibot.cfg: WebSocket, groups, bridge features, AI endpoint/key, messages and music cooldown.
config/aibot-client.cfg: local-only Netease login cookie (never sent to the server).
config/aibot-qq-bindings.json: generated QQ binding database.
config/aibot/custom_death.json: generated AI death translation cache.
Defaults retain the old option names to ease migration from the 1.20 branch.

Commands
--------
/bot reload (OP), /bot stop, /bot qqbind <QQ number>. Press M to open the music GUI.
QQ commands: !status and !bind <player>.

Compatibility notes
-------------------
OneBot uses a shaded/relocated Java-WebSocket implementation and HTTP uses Java 8 URLConnection APIs; JLayer is shaded. The client GUI supports QR login, search, My Likes, playlist folders, paging, private/global playback, list/single/random modes and playback controls. The server never receives Netease credentials. Global reports are bounded, cooldown-authoritative, and asynchronously restricted to public DNS addresses under the exact .music.126.net label suffix. Playback revalidates every HTTPS redirect (maximum five); a small DNS validation-to-connect TOCTOU window remains.
