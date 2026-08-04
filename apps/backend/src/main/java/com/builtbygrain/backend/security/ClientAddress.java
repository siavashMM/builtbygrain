package com.builtbygrain.backend.security;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

public final class ClientAddress {

    private ClientAddress() { }

    public static String normalize(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return "unknown";
        String candidate = remoteAddress.trim();
        if (!candidate.contains(":")) return candidate;

        int zoneSeparator = candidate.indexOf('%');
        String literal = zoneSeparator < 0 ? candidate : candidate.substring(0, zoneSeparator);
        try {
            InetAddress address = InetAddress.getByName(literal);
            if (!(address instanceof Inet6Address)) return address.getHostAddress();
            byte[] network = address.getAddress();
            Arrays.fill(network, 8, network.length, (byte) 0);
            return InetAddress.getByAddress(network).getHostAddress().toLowerCase(Locale.ROOT) + "/64";
        } catch (UnknownHostException ignored) {
            return candidate.toLowerCase(Locale.ROOT);
        }
    }
}
