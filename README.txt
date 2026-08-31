
Source installation information for modders
-------------------------------------------
AiBot 1.6.0 adds GeoLite2 country display to !status. Place
GeoLite2-Country.mmdb at config/aibot/GeoLite2-Country.mmdb. Static node
mappings retain priority, and the network protocol remains compatible with
older 1.x clients.

AiBot 1.5.1 for NeoForge / Minecraft 1.21.1
--------------------------------------------
Version 1.5.1 restores persisted and QR-login Netease cookies as browser-compatible
cookies, upgrades playback HTTP URLs only for strict music.126.net CDN subdomains,
and supports Clash enhanced-DNS fake IPs while retaining HTTPS hostname verification.

Netease account lookup and cookies remain client-only. The server receives only
bounded song metadata and a resolved playback URL, then validates strict HTTPS
label-boundary subdomains of music.126.net and every DNS result. Private playback
returns only to the authenticated sender; global playback uses a server-authoritative
cooldown. The client disables automatic redirects and validates every redirect.
The HTTPS implementation performs another DNS lookup while connecting, leaving a
small DNS time-of-check/time-of-use window without changing global JVM DNS behavior.

This code follows the Minecraft Forge installation methodology. It will apply
some small patches to the vanilla MCP source code, giving you and it access 
to some of the data and functions you need to build a successful mod.

Note also that the patches are built against "un-renamed" MCP source code (aka
SRG Names) - this means that you will not be able to read them directly against
normal code.

Setup Process:
==============================

Step 1: Open your command-line and browse to the folder where you extracted the zip file.

Step 2: You're left with a choice.
If you prefer to use Eclipse:
1. Run the following command: `./gradlew genEclipseRuns`
2. Open Eclipse, Import > Existing Gradle Project > Select Folder 
   or run `gradlew eclipse` to generate the project.

If you prefer to use IntelliJ:
1. Open IDEA, and import project.
2. Select your build.gradle file and have it import.
3. Run the following command: `./gradlew genIntellijRuns`
4. Refresh the Gradle Project in IDEA if required.

If at any point you are missing libraries in your IDE, or you've run into problems you can 
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
(this does not affect your code) and then start the process again.

Mapping Names:
=============================
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license, if you do not agree with it you can change your mapping names to other crowdsourced names in your 
build.gradle. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md

Additional Resources: 
=========================
Community Documentation: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
LexManos' Install Video: https://youtu.be/8VEdtQLuLO0
Forge Forums: https://forums.minecraftforge.net/
Forge Discord: https://discord.minecraftforge.net/
