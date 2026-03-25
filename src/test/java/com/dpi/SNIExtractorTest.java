package com.dpi;

import com.dpi.inspection.SNIExtractor;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SNI Extractor
 */
public class SNIExtractorTest {

    @Test
    public void testExtractSNI_ValidClientHello() {
        // Create a minimal TLS Client Hello with SNI
        ByteBuffer buffer = ByteBuffer.allocate(200);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // TLS Record Header
        buffer.put((byte) 0x16);  // Content Type: Handshake
        buffer.putShort((short) 0x0301);  // Version: TLS 1.0
        buffer.putShort((short) 100);  // Record length (placeholder)

        // Handshake Header
        buffer.put((byte) 0x01);  // Handshake Type: Client Hello
        buffer.put((byte) 0x00);  // Length (3 bytes)
        buffer.putShort((short) 96);

        // Client Version
        buffer.putShort((short) 0x0303);  // TLS 1.2

        // Random (32 bytes)
        for (int i = 0; i < 32; i++) {
            buffer.put((byte) 0x00);
        }

        // Session ID (empty)
        buffer.put((byte) 0x00);

        // Cipher Suites (2 suites)
        buffer.putShort((short) 4);
        buffer.putShort((short) 0x002f);  // TLS_RSA_WITH_AES_128_CBC_SHA
        buffer.putShort((short) 0x0035);  // TLS_RSA_WITH_AES_256_CBC_SHA

        // Compression Methods (null)
        buffer.put((byte) 0x01);
        buffer.put((byte) 0x00);

        // Extensions Length
        buffer.putShort((short) 23);

        // SNI Extension
        buffer.putShort((short) 0x0000);  // Extension Type: SNI
        buffer.putShort((short) 19);      // Extension Length

        // SNI List Length
        buffer.putShort((short) 17);

        // SNI Entry
        buffer.put((byte) 0x00);  // SNI Type: hostname
        buffer.putShort((short) 14);  // SNI Length
        buffer.put("www.google.com".getBytes());  // SNI Value

        byte[] packet = buffer.array();

        // Test extraction
        Optional<String> sni = SNIExtractor.extract(packet);

        assertTrue(sni.isPresent(), "SNI should be extracted");
        assertEquals("www.google.com", sni.get(), "SNI should match");
    }

    @Test
    public void testIsTLSClientHello_Valid() {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put((byte) 0x16);  // Content Type: Handshake
        buffer.putShort((short) 0x0303);  // Version: TLS 1.2
        buffer.putShort((short) 10);  // Length
        buffer.put((byte) 0x01);  // Handshake Type: Client Hello

        byte[] packet = buffer.array();

        assertTrue(SNIExtractor.isTLSClientHello(packet),
                "Should recognize valid TLS Client Hello");
    }

    @Test
    public void testIsTLSClientHello_NotHandshake() {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put((byte) 0x17);  // Content Type: Application Data (not handshake)
        buffer.putShort((short) 0x0303);
        buffer.putShort((short) 10);
        buffer.put((byte) 0x01);

        byte[] packet = buffer.array();

        assertFalse(SNIExtractor.isTLSClientHello(packet),
                "Should reject non-handshake packets");
    }

    @Test
    public void testIsTLSClientHello_NotClientHello() {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.put((byte) 0x16);  // Content Type: Handshake
        buffer.putShort((short) 0x0303);
        buffer.putShort((short) 10);
        buffer.put((byte) 0x02);  // Handshake Type: Server Hello (not Client Hello)

        byte[] packet = buffer.array();

        assertFalse(SNIExtractor.isTLSClientHello(packet),
                "Should reject Server Hello");
    }

    @Test
    public void testExtractSNI_TooShort() {
        byte[] packet = new byte[5];  // Too short to be valid TLS
        Optional<String> sni = SNIExtractor.extract(packet);

        assertFalse(sni.isPresent(), "Should return empty for too-short packets");
    }

    @Test
    public void testExtractSNI_NoSNIExtension() {
        // Valid Client Hello but without SNI extension
        ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // TLS Record Header
        buffer.put((byte) 0x16);
        buffer.putShort((short) 0x0301);
        buffer.putShort((short) 50);

        // Handshake Header
        buffer.put((byte) 0x01);
        buffer.put((byte) 0x00);
        buffer.putShort((short) 46);

        // Client Version
        buffer.putShort((short) 0x0303);

        // Random (32 bytes)
        for (int i = 0; i < 32; i++) {
            buffer.put((byte) 0x00);
        }

        // Session ID (empty)
        buffer.put((byte) 0x00);

        // Cipher Suites
        buffer.putShort((short) 2);
        buffer.putShort((short) 0x002f);

        // Compression Methods
        buffer.put((byte) 0x01);
        buffer.put((byte) 0x00);

        // No extensions

        byte[] packet = buffer.array();
        Optional<String> sni = SNIExtractor.extract(packet);

        assertFalse(sni.isPresent(), "Should return empty when no SNI extension");
    }
}
