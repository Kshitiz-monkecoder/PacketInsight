package com.dpi.types;

/**
 * Packet Job - Self-contained packet data passed through pipeline
 * Contains all necessary information for processing without external dependencies
 */
public class PacketJob {
    private final long packetId;
    private final FiveTuple tuple;
    private final byte[] rawData;  // Complete packet data
    private final byte[] payload;  // TCP/UDP payload only
    private final long timestampSeconds;
    private final long timestampMicros;
    private final int packetLength;
    private final byte tcpFlags;
    private final boolean isTCP;
    private final boolean isUDP;

    public PacketJob(long packetId, FiveTuple tuple, byte[] rawData, byte[] payload,
                     long timestampSeconds, long timestampMicros, int packetLength,
                     byte tcpFlags, boolean isTCP, boolean isUDP) {
        this.packetId = packetId;
        this.tuple = tuple;
        this.rawData = rawData;
        this.payload = payload;
        this.timestampSeconds = timestampSeconds;
        this.timestampMicros = timestampMicros;
        this.packetLength = packetLength;
        this.tcpFlags = tcpFlags;
        this.isTCP = isTCP;
        this.isUDP = isUDP;
    }

    // Getters
    public long getPacketId() { return packetId; }
    public FiveTuple getTuple() { return tuple; }
    public byte[] getRawData() { return rawData; }
    public byte[] getPayload() { return payload; }
    public long getTimestampSeconds() { return timestampSeconds; }
    public long getTimestampMicros() { return timestampMicros; }
    public int getPacketLength() { return packetLength; }
    public byte getTcpFlags() { return tcpFlags; }
    public boolean isTCP() { return isTCP; }
    public boolean isUDP() { return isUDP; }

    @Override
    public String toString() {
        return String.format("Packet[id=%d, %s, len=%d, tcp=%b]",
                packetId, tuple, packetLength, isTCP);
    }
}
