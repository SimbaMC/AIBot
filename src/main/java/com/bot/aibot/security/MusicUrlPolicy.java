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
        if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH) {
            throw new MusicUrlException("URL 长度无效");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_URL_LENGTH) {
            throw new MusicUrlException("URL 编码长度无效");
        }

        final URI uri;
        try {
            uri = URI.create(value).normalize();
        } catch (IllegalArgumentException e) {
            throw new MusicUrlException("URL 格式无效", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || uri.getHost() == null || uri.getRawAuthority() == null
                || uri.getRawAuthority().indexOf('%') >= 0 || uri.getPort() != -1
                || uri.getRawFragment() != null) {
            throw new MusicUrlException("只允许标准端口的 HTTPS 网易云音乐地址");
        }

        final String host;
        try {
            host = IDN.toASCII(uri.getHost(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw new MusicUrlException("主机名无效", e);
        }
        if (host.isEmpty() || host.endsWith(".") || "localhost".equals(host) || isIpLiteral(host)
                || !host.endsWith(NETEASE_CDN_SUFFIX)
                || host.length() == NETEASE_CDN_SUFFIX.length()) {
            throw new MusicUrlException("不允许的音乐 CDN 主机");
        }

        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new MusicUrlException("音乐 CDN DNS 解析失败", e);
        }
        if (addresses.length == 0) throw new MusicUrlException("音乐 CDN DNS 无结果");
        for (InetAddress address : addresses) {
            if (!isPublic(address) && !isAllowedProxyFakeIp(address)) {
                throw new MusicUrlException("音乐 CDN 解析到非公网地址");
            }
        }
        return uri;
    }

    private static boolean isAllowedProxyFakeIp(InetAddress address) {
        // The URL has already passed strict HTTPS, standard-port and Netease CDN hostname checks.
        // TLS hostname verification remains enabled by SecureMusicStream.
        if (!(address instanceof Inet4Address)) return false;
        byte[] bytes = address.getAddress();
        return (bytes[0] & 0xff) == 198 && ((bytes[1] & 0xff) == 18 || (bytes[1] & 0xff) == 19);
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) return true;
        return host.matches("[0-9.]+") || host.startsWith("[") || host.endsWith("]");
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) return isPublicV4(bytes);
        if (!(address instanceof Inet6Address)) return false;

        if (isIpv4Mapped(bytes)) {
            return isPublicV4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        // Unspecified/loopback, discard, documentation, unique-local and other reserved space.
        if ((first & 0xfe) == 0xfc || first == 0 || first >= 0xfe) return false;
        if (first == 0x20 && second == 0x01 && (bytes[2] & 0xff) <= 1) return false; // 2001::/23 special-use
        if (first == 0x20 && second == 0x01 && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) return false;
        if (first == 0x20 && second == 0x02 || first == 0x3f && second == 0xfe) return false;
        if (first == 0x00 && second == 0x64 && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b) return false;
        return (first & 0xe0) == 0x20; // Only currently allocated global-unicast 2000::/3.
    }

    private static boolean isIpv4Mapped(byte[] b) {
        if (b.length != 16 || b[10] != (byte) 0xff || b[11] != (byte) 0xff) return false;
        for (int i = 0; i < 10; i++) if (b[i] != 0) return false;
        return true;
    }

    private static boolean isPublicV4(byte[] b) {
        int a = b[0] & 0xff, c = b[1] & 0xff, d = b[2] & 0xff, e = b[3] & 0xff;
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && c >= 64 && c <= 127) return false;
        if (a == 169 && c == 254 || a == 172 && c >= 16 && c <= 31) return false;
        if (a == 192 && (c == 0 || c == 88 && d == 99 || c == 168)) return false;
        if (a == 192 && c == 0 && d == 0 && e >= 8) return false;
        if (a == 192 && c == 31 && d == 196) return false;
        if (a == 198 && (c == 18 || c == 19 || c == 51 && d == 100)) return false;
        if (a == 203 && c == 0 && d == 113) return false;
        return a < 240;
    }

    public static final class MusicUrlException extends Exception {
        public MusicUrlException(String message) { super(message); }
        public MusicUrlException(String message, Throwable cause) { super(message, cause); }
    }
}
