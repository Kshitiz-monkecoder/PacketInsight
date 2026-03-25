package com.dpi.inspection;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * SNI Extractor - Parses TLS Client Hello to extract Server Name Indication
 *
 * TLS Client Hello Structure:
 * - Record Layer (5 bytes): ContentType, Version, Length
 * - Handshake Layer: HandshakeType, Length, ClientVersion, Random, SessionID, etc.
 * - Extensions: Where SNI lives (Extension Type 0x0000)
 */
public class SNIExtractor {

    // TLS Constants
    private static final byte CONTENT_TYPE_HANDSHAKE = 0x16;
    private static final byte HANDSHAKE_CLIENT_HELLO = 0x01;
    private static final int EXTENSION_SNI = 0x0000;
    private static final byte SNI_TYPE_HOSTNAME = 0x00;

    /**
     * Extract SNI from TLS Client Hello packet
     * @param payload TCP payload containing TLS data
     * @return Optional containing SNI hostname if found
     */
    public static Optional<String> extract(byte[] payload) {
        if (payload == null || payload.length < 9) {
            return Optional.empty();
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.order(ByteOrder.BIG_ENDIAN);  // Network byte order

        if (!isTLSClientHello(buffer)) {
            return Optional.empty();
        }

        try {
            return extractSNIFromClientHello(buffer);
        } catch (Exception e) {
            // Malformed packet or parsing error
            return Optional.empty();
        }
    }

    /**
     * Check if this looks like a TLS Client Hello
     */
    public static boolean isTLSClientHello(byte[] payload) {
        if (payload == null || payload.length < 9) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return isTLSClientHello(buffer);
    }

    private static boolean isTLSClientHello(ByteBuffer buffer) {
        buffer.rewind();

        // Check TLS record header
        byte contentType = buffer.get(0);
        if (contentType != CONTENT_TYPE_HANDSHAKE) {
            return false;
        }

        // Check TLS version (0x0300 - SSL 3.0 through 0x0304 - TLS 1.3)
        short version = buffer.getShort(1);
        if (version < 0x0300 || version > 0x0304) {
            return false;
        }

        // Check record length
        int recordLength = buffer.getShort(3) & 0xFFFF;
        if (recordLength > buffer.remaining() - 5) {
            return false;
        }

        // Check handshake type
        byte handshakeType = buffer.get(5);
        return handshakeType == HANDSHAKE_CLIENT_HELLO;
    }

    private static Optional<String> extractSNIFromClientHello(ByteBuffer buffer) {
        buffer.rewind();

        // Skip TLS record header (5 bytes)
        buffer.position(5);

        // Skip handshake header (4 bytes: type + 3-byte length)
        buffer.position(buffer.position() + 4);

        // Skip client version (2 bytes)
        buffer.position(buffer.position() + 2);

        // Skip random (32 bytes)
        buffer.position(buffer.position() + 32);

        // Skip session ID
        int sessionIdLength = buffer.get() & 0xFF;
        buffer.position(buffer.position() + sessionIdLength);

        // Skip cipher suites
        int cipherSuitesLength = buffer.getShort() & 0xFFFF;
        buffer.position(buffer.position() + cipherSuitesLength);

        // Skip compression methods
        int compressionMethodsLength = buffer.get() & 0xFF;
        buffer.position(buffer.position() + compressionMethodsLength);

        // Check if extensions are present
        if (buffer.remaining() < 2) {
            return Optional.empty();
        }

        // Read extensions length
        int extensionsLength = buffer.getShort() & 0xFFFF;
        int extensionsEnd = buffer.position() + extensionsLength;

        // Search for SNI extension
        while (buffer.position() + 4 <= extensionsEnd) {
            int extensionType = buffer.getShort() & 0xFFFF;
            int extensionLength = buffer.getShort() & 0xFFFF;

            if (extensionType == EXTENSION_SNI) {
                return parseSNIExtension(buffer, extensionLength);
            }

            // Skip this extension
            buffer.position(buffer.position() + extensionLength);
        }

        return Optional.empty();
    }

    private static Optional<String> parseSNIExtension(ByteBuffer buffer, int extensionLength) {
        int extensionEnd = buffer.position() + extensionLength;

        // SNI List Length (2 bytes)
        int sniListLength = buffer.getShort() & 0xFFFF;

        while (buffer.position() < extensionEnd) {
            // SNI Type (1 byte)
            byte sniType = buffer.get();

            // SNI Length (2 bytes)
            int sniLength = buffer.getShort() & 0xFFFF;

            if (sniType == SNI_TYPE_HOSTNAME && sniLength > 0) {
                // Extract hostname
                byte[] hostnameBytes = new byte[sniLength];
                buffer.get(hostnameBytes);
                String hostname = new String(hostnameBytes, StandardCharsets.UTF_8);
                return Optional.of(hostname);
            } else {
                // Skip unknown SNI type
                buffer.position(buffer.position() + sniLength);
            }
        }

        return Optional.empty();
    }

    /**
     * Read uint16 in big-endian (network byte order)
     */
    private static int readUint16BE(ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    /**
     * Read uint24 in big-endian (3 bytes)
     */
    private static int readUint24BE(ByteBuffer buffer) {
        int value = 0;
        value |= (buffer.get() & 0xFF) << 16;
        value |= (buffer.get() & 0xFF) << 8;
        value |= (buffer.get() & 0xFF);
        return value;
    }
}
