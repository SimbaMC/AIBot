package com.bot.aibot.security;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MusicUrlPolicy {

    public static final int MAX_URL_BYTES = 2048;
    private static final String CDN_SUFFIX = ".music.126.net";

    private MusicUrlPolicy() {}

    public static URI validate(String value) throws MusicUrlException {
        if (value == null || value.length() == 0
            || value.length() > MAX_URL_BYTES
            || value.getBytes(StandardCharsets.UTF_8).length > MAX_URL_BYTES)
            throw new MusicUrlException("Invalid URL length");
        final URI uri;
        try {
            uri = new URI(value).normalize();
        } catch (Exception e) {
            throw new MusicUrlException("Invalid URL", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
            || uri.getHost() == null
            || uri.getRawAuthority() == null
            || uri.getRawAuthority()
                .indexOf('%') >= 0
            || uri.getPort() != -1
            || uri.getRawFragment() != null) throw new MusicUrlException("Only standard HTTPS URLs are allowed");
        final String host;
        try {
            host = IDN.toASCII(uri.getHost(), IDN.USE_STD3_ASCII_RULES)
                .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new MusicUrlException("Invalid host", e);
        }
        if (host.length() == 0 || host.endsWith(".")
            || "localhost".equals(host)
            || isIpLiteral(host)
            || !host.endsWith(CDN_SUFFIX)
            || host.length() == CDN_SUFFIX.length()) throw new MusicUrlException("Host is not an approved Netease CDN");
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new MusicUrlException("DNS failed", e);
        }
        if (addresses.length == 0) throw new MusicUrlException("DNS returned no addresses");
        for (InetAddress address : addresses) if (!isPublic(address) && !isAllowedProxyFakeIp(address))
            throw new MusicUrlException("DNS returned a non-public address");
        return uri;
    }

    private static boolean isAllowedProxyFakeIp(InetAddress address) {
        // Clash-style enhanced DNS maps remote hosts into 198.18.0.0/15. This policy has
        // already restricted the target to an HTTPS Netease CDN hostname, so TLS hostname
        // verification still prevents this exception from becoming access to an arbitrary LAN service.
        if (!(address instanceof Inet4Address)) return false;
        byte[] b = address.getAddress();
        return (b[0] & 255) == 198 && ((b[1] & 255) == 18 || (b[1] & 255) == 19);
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+") || host.startsWith("[") || host.endsWith("]");
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) return publicV4(b);
        if (!(address instanceof Inet6Address)) return false;
        if (mappedV4(b)) return publicV4(new byte[] { b[12], b[13], b[14], b[15] });
        int a = b[0] & 255, c = b[1] & 255;
        if ((a & 0xfe) == 0xfc || a == 0 || a >= 0xfe) return false;
        if (a == 0x20 && c == 0x01 && (b[2] & 255) <= 1) return false;
        if (a == 0x20 && c == 0x01 && (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8) return false;
        if ((a == 0x20 && c == 0x02) || (a == 0x3f && c == 0xfe)) return false;
        if (a == 0 && c == 0x64 && (b[2] & 255) == 0xff && (b[3] & 255) == 0x9b) return false;
        return (a & 0xe0) == 0x20;
    }

    private static boolean mappedV4(byte[] b) {
        if (b.length != 16 || b[10] != (byte) 0xff || b[11] != (byte) 0xff) return false;
        for (int i = 0; i < 10; i++) if (b[i] != 0) return false;
        return true;
    }

    private static boolean publicV4(byte[] b) {
        int a = b[0] & 255, c = b[1] & 255, d = b[2] & 255, e = b[3] & 255;
        if (a == 0 || a == 10 || a == 127 || a >= 224 || (a == 100 && c >= 64 && c <= 127)) return false;
        if ((a == 169 && c == 254) || (a == 172 && c >= 16 && c <= 31)) return false;
        if (a == 192 && (c == 0 || (c == 88 && d == 99) || c == 168)) return false;
        if (a == 192 && c == 0 && d == 0 && e >= 8 || a == 192 && c == 31 && d == 196) return false;
        if (a == 198 && (c == 18 || c == 19 || (c == 51 && d == 100)) || a == 203 && c == 0 && d == 113) return false;
        return a < 240;
    }

    public static final class MusicUrlException extends Exception {

        public MusicUrlException(String message) {
            super(message);
        }

        public MusicUrlException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
