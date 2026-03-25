package com.dpi.inspection;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * HTTP Host Header Extractor - For unencrypted HTTP traffic
 */
public class HTTPHostExtractor {

    private static final String HOST_HEADER = "Host: ";
    private static final byte[] HTTP_GET = "GET ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HTTP_POST = "POST ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HTTP_HEAD = "HEAD ".getBytes(StandardCharsets.US_ASCII);

    /**
     * Extract Host header from HTTP request
     */
    public static Optional<String> extract(byte[] payload) {
        if (payload == null || payload.length < 16) {
            return Optional.empty();
        }

        if (!isHTTPRequest(payload)) {
            return Optional.empty();
        }

        try {
            String httpData = new String(payload, StandardCharsets.US_ASCII);
            String[] lines = httpData.split("\r\n");

            for (String line : lines) {
                if (line.startsWith(HOST_HEADER)) {
                    String host = line.substring(HOST_HEADER.length()).trim();
                    // Remove port if present
                    int colonIndex = host.indexOf(':');
                    if (colonIndex > 0) {
                        host = host.substring(0, colonIndex);
                    }
                    return Optional.of(host);
                }
            }
        } catch (Exception e) {
            // Parsing error
        }

        return Optional.empty();
    }

    /**
     * Check if this looks like an HTTP request
     */
    public static boolean isHTTPRequest(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return false;
        }

        return startsWith(payload, HTTP_GET) ||
               startsWith(payload, HTTP_POST) ||
               startsWith(payload, HTTP_HEAD);
    }

    private static boolean startsWith(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
