package vn.inventoryai.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ClientIpResolver {
    private final List<IpNetwork> trustedProxies;
    private final int maxHeaderLength;
    private final int maxHops;

    public ClientIpResolver(ClientIpProperties properties) {
        this.trustedProxies = properties.trustedProxies().stream().map(IpNetwork::parse).toList();
        this.maxHeaderLength = properties.maxHeaderLength();
        this.maxHops = properties.maxHops();
    }

    public String resolve(HttpServletRequest request) {
        String directPeer = IpNetwork.normalizeLiteral(request.getRemoteAddr()).orElse("unknown");
        if (!isTrusted(directPeer)) {
            return directPeer;
        }

        HeaderRead forwarded = readBoundedHeader(request, "Forwarded");
        if (forwarded.present()) {
            if (!forwarded.valid()) {
                return directPeer;
            }
            Optional<List<String>> chain = parseForwarded(forwarded.value());
            return chain.map(addresses -> resolveTrustedChain(directPeer, addresses)).orElse(directPeer);
        }

        HeaderRead xForwardedFor = readBoundedHeader(request, "X-Forwarded-For");
        if (!xForwardedFor.present()) {
            return directPeer;
        }
        if (!xForwardedFor.valid()) {
            return directPeer;
        }
        Optional<List<String>> chain = parseXForwardedFor(xForwardedFor.value());
        return chain.map(addresses -> resolveTrustedChain(directPeer, addresses)).orElse(directPeer);
    }

    private String resolveTrustedChain(String directPeer, List<String> chain) {
        String candidate = directPeer;
        for (int index = chain.size() - 1; index >= 0 && isTrusted(candidate); index--) {
            candidate = chain.get(index);
        }
        return candidate;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(network -> network.contains(address));
    }

    private HeaderRead readBoundedHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) {
            return HeaderRead.absent();
        }

        StringBuilder combined = new StringBuilder();
        while (values.hasMoreElements()) {
            String value = values.nextElement();
            if (value == null || containsControlCharacter(value)) {
                return HeaderRead.invalid();
            }
            int separatorLength = combined.isEmpty() ? 0 : 1;
            if (combined.length() + separatorLength + value.length() > maxHeaderLength) {
                return HeaderRead.invalid();
            }
            if (separatorLength == 1) {
                combined.append(',');
            }
            combined.append(value);
        }
        return combined.isEmpty() ? HeaderRead.invalid() : HeaderRead.valid(combined.toString());
    }

    private boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) && character != '\t') {
                return true;
            }
        }
        return false;
    }

    private Optional<List<String>> parseForwarded(String value) {
        Optional<List<String>> elements = splitOutsideQuotes(value, ',');
        if (elements.isEmpty() || elements.get().isEmpty() || elements.get().size() > maxHops) {
            return Optional.empty();
        }

        List<String> addresses = new ArrayList<>(elements.get().size());
        for (String element : elements.get()) {
            Optional<List<String>> parameters = splitOutsideQuotes(element, ';');
            if (parameters.isEmpty()) {
                return Optional.empty();
            }

            String forwardedFor = null;
            for (String parameter : parameters.get()) {
                int equals = parameter.indexOf('=');
                if (equals <= 0) {
                    return Optional.empty();
                }
                String name = parameter.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                if (!name.equals("for")) {
                    continue;
                }
                if (forwardedFor != null) {
                    return Optional.empty();
                }
                Optional<String> decoded = decodeParameterValue(parameter.substring(equals + 1).trim());
                if (decoded.isEmpty()) {
                    return Optional.empty();
                }
                forwardedFor = decoded.get();
            }

            if (forwardedFor == null) {
                return Optional.empty();
            }
            Optional<String> address = parseNodeIdentifier(forwardedFor);
            if (address.isEmpty()) {
                return Optional.empty();
            }
            addresses.add(address.get());
        }
        return Optional.of(List.copyOf(addresses));
    }

    private Optional<List<String>> parseXForwardedFor(String value) {
        String[] nodes = value.split(",", -1);
        if (nodes.length == 0 || nodes.length > maxHops) {
            return Optional.empty();
        }

        List<String> addresses = new ArrayList<>(nodes.length);
        for (String node : nodes) {
            Optional<String> address = parseNodeIdentifier(node.trim());
            if (address.isEmpty()) {
                return Optional.empty();
            }
            addresses.add(address.get());
        }
        return Optional.of(List.copyOf(addresses));
    }

    private Optional<String> parseNodeIdentifier(String value) {
        if (value == null || value.isBlank()
                || value.equalsIgnoreCase("unknown")
                || value.charAt(0) == '_'
                || containsWhitespace(value)) {
            return Optional.empty();
        }

        if (value.charAt(0) == '[') {
            int closingBracket = value.indexOf(']');
            if (closingBracket <= 1) {
                return Optional.empty();
            }
            String suffix = value.substring(closingBracket + 1);
            if (!suffix.isEmpty() && !isValidPortSuffix(suffix)) {
                return Optional.empty();
            }
            return IpNetwork.normalizeLiteral(value.substring(1, closingBracket));
        }

        Optional<String> literal = IpNetwork.normalizeLiteral(value);
        if (literal.isPresent()) {
            return literal;
        }

        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon && isValidPortSuffix(value.substring(firstColon))) {
            return IpNetwork.normalizeLiteral(value.substring(0, firstColon));
        }
        return Optional.empty();
    }

    private boolean isValidPortSuffix(String suffix) {
        if (suffix.length() < 2 || suffix.charAt(0) != ':') {
            return false;
        }
        int port = 0;
        for (int index = 1; index < suffix.length(); index++) {
            char character = suffix.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
            port = port * 10 + (character - '0');
            if (port > 65_535) {
                return false;
            }
        }
        return true;
    }

    private boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> decodeParameterValue(String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        if (value.charAt(0) != '"') {
            return value.indexOf('"') >= 0 ? Optional.empty() : Optional.of(value);
        }
        if (value.length() < 2 || value.charAt(value.length() - 1) != '"') {
            return Optional.empty();
        }

        StringBuilder decoded = new StringBuilder(value.length() - 2);
        boolean escaped = false;
        for (int index = 1; index < value.length() - 1; index++) {
            char character = value.charAt(index);
            if (escaped) {
                decoded.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"' || Character.isISOControl(character)) {
                return Optional.empty();
            } else {
                decoded.append(character);
            }
        }
        return escaped || decoded.isEmpty() ? Optional.empty() : Optional.of(decoded.toString());
    }

    private Optional<List<String>> splitOutsideQuotes(String value, char delimiter) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quoted && character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && character == delimiter) {
                String part = value.substring(start, index).trim();
                if (part.isEmpty()) {
                    return Optional.empty();
                }
                parts.add(part);
                start = index + 1;
            }
        }
        if (quoted || escaped) {
            return Optional.empty();
        }
        String last = value.substring(start).trim();
        if (last.isEmpty()) {
            return Optional.empty();
        }
        parts.add(last);
        return Optional.of(parts);
    }

    private record HeaderRead(boolean present, boolean valid, String value) {
        static HeaderRead absent() {
            return new HeaderRead(false, true, "");
        }

        static HeaderRead invalid() {
            return new HeaderRead(true, false, "");
        }

        static HeaderRead valid(String value) {
            return new HeaderRead(true, true, value);
        }
    }
}
