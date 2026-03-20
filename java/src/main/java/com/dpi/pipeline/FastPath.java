package com.dpi.pipeline;

import com.dpi.inspection.HTTPHostExtractor;
import com.dpi.inspection.SNIExtractor;
import com.dpi.rules.RuleManager;
import com.dpi.types.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fast Path Thread - Performs DPI and classification
 * Each FP maintains its own flow table for the flows it handles
 */
public class FastPath implements Runnable {
    private final int id;
    private final BlockingQueue<PacketJob> inputQueue;
    private final BlockingQueue<PacketJob> outputQueue;
    private final RuleManager ruleManager;
    private final DPIStats stats;

    // Per-thread flow table (no locking needed!)
    private final Map<FiveTuple, FlowEntry> flows = new HashMap<>();

    private final AtomicLong packetsProcessed = new AtomicLong(0);
    private volatile boolean running = true;

    public FastPath(int id, BlockingQueue<PacketJob> outputQueue,
                    RuleManager ruleManager, DPIStats stats) {
        this.id = id;
        this.inputQueue = new LinkedBlockingQueue<>(10000);
        this.outputQueue = outputQueue;
        this.ruleManager = ruleManager;
        this.stats = stats;
    }

    /**
     * Enqueue packet for processing (called by LoadBalancer)
     */
    public void enqueue(PacketJob packet) throws InterruptedException {
        inputQueue.put(packet);
    }

    @Override
    public void run() {
        Thread.currentThread().setName("FP-" + id);
        System.out.println("[FP" + id + "] Started");

        while (running) {
            try {
                PacketJob packet = inputQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (packet == null) {
                    continue;  // Timeout, check running flag
                }

                processPacket(packet);
                packetsProcessed.incrementAndGet();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[FP" + id + "] Error processing packet: " + e.getMessage());
            }
        }

        System.out.println("[FP" + id + "] Stopped (processed " + packetsProcessed.get() + " packets)");
    }

    private void processPacket(PacketJob packet) throws InterruptedException {
        FiveTuple tuple = packet.getTuple();

        // Get or create flow entry
        FlowEntry flow = flows.computeIfAbsent(tuple, FlowEntry::new);

        // Classify if not yet classified
        if (flow.getAppType() == AppType.UNKNOWN || flow.getSNI() == null) {
            classifyFlow(packet, flow);
        }

        // Update flow statistics
        flow.updateStats(packet.getPacketLength(), false);

        // Check blocking rules
        boolean shouldBlock = ruleManager.isBlocked(
                tuple.getSrcIP(),
                flow.getAppType(),
                flow.getSNI()
        );

        if (shouldBlock) {
            flow.setBlocked(true);
            stats.incrementDropped();
            // Don't forward to output
        } else {
            stats.incrementForwarded();
            // Forward to output
            outputQueue.put(packet);
        }
    }

    /**
     * Classify flow by extracting SNI or HTTP Host
     */
    private void classifyFlow(PacketJob packet, FlowEntry flow) {
        byte[] payload = packet.getPayload();
        if (payload == null || payload.length == 0) {
            return;
        }

        // Try HTTPS (port 443) - Extract SNI
        if (packet.isTCP() && packet.getTuple().getDstPort() == 443) {
            Optional<String> sni = SNIExtractor.extract(payload);
            if (sni.isPresent()) {
                flow.setSNI(sni.get());
                flow.setAppType(AppType.fromSNI(sni.get()));
                return;
            }
        }

        // Try HTTP (port 80) - Extract Host header
        if (packet.isTCP() && packet.getTuple().getDstPort() == 80) {
            Optional<String> host = HTTPHostExtractor.extract(payload);
            if (host.isPresent()) {
                flow.setSNI(host.get());
                flow.setAppType(AppType.HTTP);
                return;
            }
        }

        // DNS (port 53)
        if (packet.isUDP() && packet.getTuple().getDstPort() == 53) {
            flow.setAppType(AppType.DNS);
        }
    }

    public void stop() {
        running = false;
    }

    public long getPacketsProcessed() {
        return packetsProcessed.get();
    }

    public int getFlowCount() {
        return flows.size();
    }

    public Map<FiveTuple, FlowEntry> getFlows() {
        return flows;
    }

    public int getId() {
        return id;
    }
}
