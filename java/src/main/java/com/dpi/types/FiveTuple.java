package com.dpi.types;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Five-Tuple: Uniquely identifies a network connection/flow
 * Consists of: Source IP, Destination IP, Source Port, Destination Port, Protocol
 */
public class FiveTuple {
    private final InetAddress srcIP;
    private final InetAddress dstIP;
    private final int srcPort;
    private final int dstPort;
    private final byte protocol;  // TCP=6, UDP=17

    public FiveTuple(InetAddress srcIP, InetAddress dstIP, int srcPort, int dstPort, byte protocol) {
        this.srcIP = srcIP;
        this.dstIP = dstIP;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.protocol = protocol;
    }

    // Create from raw bytes (for packet parsing)
    public static FiveTuple fromBytes(byte[] srcIPBytes, byte[] dstIPBytes,
                                      int srcPort, int dstPort, byte protocol) {
        try {
            InetAddress srcIP = InetAddress.getByAddress(srcIPBytes);
            InetAddress dstIP = InetAddress.getByAddress(dstIPBytes);
            return new FiveTuple(srcIP, dstIP, srcPort, dstPort, protocol);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP address bytes", e);
        }
    }

    /**
     * Create reverse tuple (for matching bidirectional flows)
     */
    public FiveTuple reverse() {
        return new FiveTuple(dstIP, srcIP, dstPort, srcPort, protocol);
    }

    /**
     * Hash function for consistent load balancing
     * Same as C++ version: combines all fields with magic constant
     */
    @Override
    public int hashCode() {
        long h = 0;
        // Use same algorithm as C++ for consistency
        h ^= Objects.hashCode(srcIP) + 0x9e3779b9L + (h << 6) + (h >> 2);
        h ^= Objects.hashCode(dstIP) + 0x9e3779b9L + (h << 6) + (h >> 2);
        h ^= Integer.hashCode(srcPort) + 0x9e3779b9L + (h << 6) + (h >> 2);
        h ^= Integer.hashCode(dstPort) + 0x9e3779b9L + (h << 6) + (h >> 2);
        h ^= Byte.hashCode(protocol) + 0x9e3779b9L + (h << 6) + (h >> 2);
        return (int) h;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FiveTuple)) return false;
        FiveTuple other = (FiveTuple) obj;
        return srcPort == other.srcPort &&
               dstPort == other.dstPort &&
               protocol == other.protocol &&
               Objects.equals(srcIP, other.srcIP) &&
               Objects.equals(dstIP, other.dstIP);
    }

    @Override
    public String toString() {
        return String.format("%s:%d -> %s:%d (proto=%d)",
                srcIP.getHostAddress(), srcPort,
                dstIP.getHostAddress(), dstPort,
                protocol);
    }

    // Getters
    public InetAddress getSrcIP() { return srcIP; }
    public InetAddress getDstIP() { return dstIP; }
    public int getSrcPort() { return srcPort; }
    public int getDstPort() { return dstPort; }
    public byte getProtocol() { return protocol; }
}
