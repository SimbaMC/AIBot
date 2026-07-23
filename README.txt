AiBot 1.0.0-gtnh284 - Minecraft 1.7.10 / GT New Horizons 2.8.4

AiBot bridges a Forge server to QQ through a OneBot 11 WebSocket and includes optional AI chat/death translation, QQ account binding, achievements, join/leave/chat/death sync, and client-side playback of server-provided music URLs.

Build
-----
Use JDK 17+ to run the bundled RetroFuturaGradle wrapper:
  gradlew build
The produced classes and distributable jar target Java 8. Forge is exactly 1.7.10-10.13.4.1614-1.7.10.

Configuration
-------------
config/aibot.cfg: WebSocket, groups, bridge features, AI endpoint/key, messages and music cooldown.
config/aibot-qq-bindings.json: generated QQ binding database.
config/aibot/custom_death.json: generated AI death translation cache.
Defaults retain the old option names to ease migration from the 1.20 branch.

Commands
--------
/bot reload (OP), /bot stop, /bot qqbind <QQ number>
QQ commands: !status and !bind <player>.

Compatibility notes
-------------------
OneBot uses a shaded/relocated Java-WebSocket implementation and HTTP uses HttpURLConnection; no Java 11 APIs are linked. JLayer is shaded. QQ CQ image/face segments are represented as chat markers. Native hover image previews, QR login, and the full modern playlist GUI are not included because their 1.20 rendering/input implementation is incompatible with the GTNH 1.7.10 client; direct URL playback and packet controls remain.
