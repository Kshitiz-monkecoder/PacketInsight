package com.dpi.types;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics - Thread-safe counters for DPI engine metrics
 */
public class DPIStats {
    private final AtomicLong totalPackets = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong forwardedPackets = new AtomicLong(0);
    private final AtomicLong droppedPackets = new AtomicLong(0);
    private final AtomicLong tcpPackets = new AtomicLong(0);
    private final AtomicLong udpPackets = new AtomicLong(0);
    private final AtomicLong otherPackets = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);

    // Increment methods
    public void incrementTotalPackets() { totalPackets.incrementAndGet(); }
    public void incrementTotalBytes(long bytes) { totalBytes.addAndGet(bytes); }
    public void incrementForwarded() { forwardedPackets.incrementAndGet(); }
    public void incrementDropped() { droppedPackets.incrementAndGet(); }
    public void incrementTCP() { tcpPackets.incrementAndGet(); }
    public void incrementUDP() { udpPackets.incrementAndGet(); }
    public void incrementOther() { otherPackets.incrementAndGet(); }
    public void setActiveConnections(long count) { activeConnections.set(count); }

    // Getters
    public long getTotalPackets() { return totalPackets.get(); }
    public long getTotalBytes() { return totalBytes.get(); }
    public long getForwardedPackets() { return forwardedPackets.get(); }
    public long getDroppedPackets() { return droppedPackets.get(); }
    public long getTcpPackets() { return tcpPackets.get(); }
    public long getUdpPackets() { return udpPackets.get(); }
    public long getOtherPackets() { return otherPackets.get(); }
    public long getActiveConnections() { return activeConnections.get(); }

    /**
     * Print statistics summary
     */
    public void printSummary() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                      PROCESSING REPORT                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Total Packets:          %10d                          ║%n", getTotalPackets());
        System.out.printf("║ Total Bytes:            %10d                          ║%n", getTotalBytes());
        System.out.printf("║ TCP Packets:            %10d                          ║%n", getTcpPackets());
        System.out.printf("║ UDP Packets:            %10d                          ║%n", getUdpPackets());
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Forwarded:              %10d                          ║%n", getForwardedPackets());
        System.out.printf("║ Dropped:                %10d                          ║%n", getDroppedPackets());
        System.out.printf("║ Active Connections:     %10d                          ║%n", getActiveConnections());
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    /**
     * Reset all counters
     */
    public void reset() {
        totalPackets.set(0);
        totalBytes.set(0);
        forwardedPackets.set(0);
        droppedPackets.set(0);
        tcpPackets.set(0);
        udpPackets.set(0);
        otherPackets.set(0);
        activeConnections.set(0);
    }
}
