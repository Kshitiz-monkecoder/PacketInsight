package com.dpi.types;

import java.time.Instant;

/**
 * Flow Entry - Tracks state for a single network connection
 */
public class FlowEntry {
    private final FiveTuple tuple;
    private AppType appType;
    private String sni;  // Server Name Indication (if detected)
    private boolean blocked;

    private long packetsIn;
    private long packetsOut;
    private long bytesIn;
    private long bytesOut;

    private final Instant firstSeen;
    private Instant lastSeen;

    public FlowEntry(FiveTuple tuple) {
        this.tuple = tuple;
        this.appType = AppType.UNKNOWN;
        this.sni = null;
        this.blocked = false;
        this.firstSeen = Instant.now();
        this.lastSeen = this.firstSeen;
    }

    /**
     * Update flow with SNI information
     */
    public void setSNI(String sni) {
        if (this.sni == null && sni != null) {
            this.sni = sni;
            this.appType = AppType.fromSNI(sni);
        }
    }

    /**
     * Update statistics
     */
    public void updateStats(long bytes, boolean outgoing) {
        if (outgoing) {
            packetsOut++;
            bytesOut += bytes;
        } else {
            packetsIn++;
            bytesIn += bytes;
        }
        lastSeen = Instant.now();
    }

    // Getters and Setters
    public FiveTuple getTuple() { return tuple; }
    public AppType getAppType() { return appType; }
    public void setAppType(AppType appType) { this.appType = appType; }
    public String getSNI() { return sni; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public long getPacketsIn() { return packetsIn; }
    public long getPacketsOut() { return packetsOut; }
    public long getBytesIn() { return bytesIn; }
    public long getBytesOut() { return bytesOut; }
    public long getTotalPackets() { return packetsIn + packetsOut; }
    public long getTotalBytes() { return bytesIn + bytesOut; }

    public Instant getFirstSeen() { return firstSeen; }
    public Instant getLastSeen() { return lastSeen; }

    @Override
    public String toString() {
        return String.format("Flow[%s, app=%s, sni=%s, pkts=%d, bytes=%d, blocked=%b]",
                tuple, appType, sni, getTotalPackets(), getTotalBytes(), blocked);
    }
}
