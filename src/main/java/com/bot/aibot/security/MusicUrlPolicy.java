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
    public static final int MAX_URL_LENGTH = 2048;
    private static final String NETEASE_CDN_SUFFIX = ".music.126.net";

    private MusicUrlPolicy() {}

    public static URI validate(String value) throws MusicUrlException {
        if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_URL_LENGTH) {
            throw new MusicUrlException("URL length is invalid");
        }
        final URI uri;
        try {
            uri = URI.create(value).normalize();
        } catch (IllegalArgumentException e) {
            throw new MusicUrlException("URL is invalid", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || uri.getHost() == null || uri.getRawAuthority() == null
                || uri.getRawAuthority().indexOf('%') >= 0 || uri.getPort() != -1
                || uri.getRawFragment() != null) {
            throw new MusicUrlException("Only standard-port HTTPS URLs are allowed");
        }
        final String host;
        try {
            host = IDN.toASCII(uri.getHost(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new MusicUrlException("Host is invalid", e);
        }
        if (host.isEmpty() || host.endsWith(".") || isIpLiteral(host)
                || !host.endsWith(NETEASE_CDN_SUFFIX) || host.length() == NETEASE_CDN_SUFFIX.length()) {
            throw new MusicUrlException("Music CDN host is not allowed");
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new MusicUrlException("Music CDN DNS lookup failed", e);
        }
        if (addresses.length == 0) throw new MusicUrlException("Music CDN DNS returned no addresses");
        for (InetAddress address : addresses) {
            if (!isPublic(address)) throw new MusicUrlException("Music CDN resolved to a non-public address");
        }
        return uri;
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+") || host.startsWith("[") || host.endsWith("]");
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isPublicV4(bytes);
        if (!(address instanceof Inet6Address)) return false;
        if (isIpv4Mapped(bytes)) return isPublicV4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
        int first = bytes[0] & 0xff, second = bytes[1] & 0xff;
        if ((first & 0xfe) == 0xfc || first == 0 || first >= 0xfe) return false;
        if (first == 0x20 && second == 0x01 && (bytes[2] & 0xff) <= 1) return false;
        if (first == 0x20 && second == 0x01 && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) return false;
        if ((first == 0x20 && second == 0x02) || (first == 0x3f && second == 0xfe)) return false;
        if (first == 0x00 && second == 0x64 && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b) return false;
        return (first & 0xe0) == 0x20;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16 || bytes[10] != (byte) 0xff || bytes[11] != (byte) 0xff) return false;
        for (int i = 0; i < 10; i++) if (bytes[i] != 0) return false;
        return true;
    }

    private static boolean isPublicV4(byte[] bytes) {
        int a = bytes[0] & 0xff, b = bytes[1] & 0xff, c = bytes[2] & 0xff, d = bytes[3] & 0xff;
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && b >= 64 && b <= 127) return false;
        if ((a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)) return false;
        if (a == 192 && (b == 0 || (b == 88 && c == 99) || b == 168)) return false;
        if (a == 192 && b == 0 && c == 0 && d >= 8) return false;
        if (a == 192 && b == 31 && c == 196) return false;
        if (a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100))) return false;
        if (a == 203 && b == 0 && c == 113) return false;
        return a < 240;
    }

    public static final class MusicUrlException extends Exception {
        public MusicUrlException(String message) { super(message); }
        public MusicUrlException(String message, Throwable cause) { super(message, cause); }
    }
}
