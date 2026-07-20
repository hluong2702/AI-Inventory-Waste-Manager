package vn.inventoryai.common.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

final class IpNetwork {
    private final byte[] networkAddress;
    private final int prefixLength;

    private IpNetwork(byte[] networkAddress, int prefixLength) {
        this.networkAddress = networkAddress;
        this.prefixLength = prefixLength;
    }

    static IpNetwork parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Trusted proxy CIDR must not be blank");
        }

        String candidate = value.trim();
        int slash = candidate.indexOf('/');
        if (slash != candidate.lastIndexOf('/')) {
            throw new IllegalArgumentException("Invalid trusted proxy CIDR");
        }

        String addressPart = slash < 0 ? candidate : candidate.substring(0, slash);
        InetAddress address = parseLiteral(addressPart)
                .orElseThrow(() -> new IllegalArgumentException("Trusted proxy CIDR must contain a literal IP address"));
        int addressBits = address.getAddress().length * Byte.SIZE;
        int prefix = slash < 0 ? addressBits : parsePrefix(candidate.substring(slash + 1), addressBits);
        byte[] masked = address.getAddress().clone();
        maskHostBits(masked, prefix);
        return new IpNetwork(masked, prefix);
    }

    boolean contains(String address) {
        Optional<InetAddress> parsed = parseLiteral(address);
        if (parsed.isEmpty()) {
            return false;
        }

        byte[] candidate = parsed.get().getAddress();
        if (candidate.length != networkAddress.length) {
            return false;
        }

        int fullBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        for (int index = 0; index < fullBytes; index++) {
            if (candidate[index] != networkAddress[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }

        int mask = 0xff << (Byte.SIZE - remainingBits);
        return (candidate[fullBytes] & mask) == (networkAddress[fullBytes] & mask);
    }

    static Optional<String> normalizeLiteral(String value) {
        return parseLiteral(value).map(InetAddress::getHostAddress);
    }

    private static Optional<InetAddress> parseLiteral(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String candidate = value.trim();
        if (candidate.isEmpty() || !candidate.equals(value.trim())) {
            return Optional.empty();
        }

        if (candidate.indexOf(':') >= 0) {
            return parseIpv6(candidate);
        }
        return parseIpv4(candidate);
    }

    private static Optional<InetAddress> parseIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return Optional.empty();
        }

        byte[] address = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || octet.length() > 3) {
                return Optional.empty();
            }
            int parsed = 0;
            for (int character = 0; character < octet.length(); character++) {
                char digit = octet.charAt(character);
                if (digit < '0' || digit > '9') {
                    return Optional.empty();
                }
                parsed = parsed * 10 + (digit - '0');
            }
            if (parsed > 255) {
                return Optional.empty();
            }
            address[index] = (byte) parsed;
        }

        try {
            InetAddress parsed = InetAddress.getByAddress(address);
            return parsed instanceof Inet4Address ? Optional.of(parsed) : Optional.empty();
        } catch (UnknownHostException impossible) {
            return Optional.empty();
        }
    }

    private static Optional<InetAddress> parseIpv6(String value) {
        if (value.indexOf('%') >= 0 || value.length() > 45) {
            return Optional.empty();
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean permitted = (character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')
                    || (character >= 'A' && character <= 'F')
                    || character == ':'
                    || character == '.';
            if (!permitted) {
                return Optional.empty();
            }
        }

        try {
            InetAddress parsed = InetAddress.getByName(value);
            return parsed instanceof Inet6Address || parsed instanceof Inet4Address
                    ? Optional.of(parsed)
                    : Optional.empty();
        } catch (UnknownHostException ignored) {
            return Optional.empty();
        }
    }

    private static int parsePrefix(String value, int addressBits) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Trusted proxy CIDR prefix is required");
        }
        try {
            int prefix = Integer.parseInt(value);
            if (prefix < 0 || prefix > addressBits) {
                throw new IllegalArgumentException("Trusted proxy CIDR prefix is out of range");
            }
            return prefix;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Trusted proxy CIDR prefix must be numeric", ex);
        }
    }

    private static void maskHostBits(byte[] address, int prefix) {
        int fullBytes = prefix / Byte.SIZE;
        int remainingBits = prefix % Byte.SIZE;
        if (remainingBits != 0 && fullBytes < address.length) {
            address[fullBytes] = (byte) (address[fullBytes] & (0xff << (Byte.SIZE - remainingBits)));
            fullBytes++;
        }
        for (int index = fullBytes; index < address.length; index++) {
            address[index] = 0;
        }
    }
}
