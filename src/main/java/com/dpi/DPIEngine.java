package com.dpi;

import com.dpi.pipeline.FastPath;
import com.dpi.pipeline.LoadBalancer;
import com.dpi.rules.RuleManager;
import com.dpi.types.*;
import com.dpi.util.PacketParser;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IllegalRawDataException;

import java.io.EOFException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main DPI Engine - Multi-threaded Deep Packet Inspection
 * Architecture: Reader → LoadBalancers → FastPaths → OutputWriter
 */
public class DPIEngine {

    private static final String VERSION = "2.0";

    private final String inputFile;
    private final String outputFile;
    private final int numLBs;
    private final int numFPsPerLB;
    private final RuleManager ruleManager;
    private final DPIStats stats;

    // Queues for pipeline
    private final List<BlockingQueue<PacketJob>> lbQueues;
    private final BlockingQueue<PacketJob> outputQueue;

    // Thread pools
    private final ExecutorService lbPool;
    private final ExecutorService fpPool;
    private final ExecutorService writerPool;

    // Workers
    private final List<LoadBalancer> loadBalancers;
    private final List<FastPath> fastPaths;

    public DPIEngine(String inputFile, String outputFile, int numLBs, int numFPsPerLB) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.numLBs = numLBs;
        this.numFPsPerLB = numFPsPerLB;
        this.ruleManager = new RuleManager();
        this.stats = new DPIStats();

        // Initialize queues
        this.lbQueues = new ArrayList<>();
        for (int i = 0; i < numLBs; i++) {
            lbQueues.add(new LinkedBlockingQueue<>(10000));
        }
        this.outputQueue = new LinkedBlockingQueue<>(50000);

        // Initialize thread pools
        this.lbPool = Executors.newFixedThreadPool(numLBs);
        this.fpPool = Executors.newFixedThreadPool(numLBs * numFPsPerLB);
        this.writerPool = Executors.newSingleThreadExecutor();

        // Initialize workers
        this.loadBalancers = new ArrayList<>();
        this.fastPaths = new ArrayList<>();
    }

    /**
     * Run the DPI engine
     */
    public void run() throws Exception {
        printHeader();
        ruleManager.printRules();

        // Create FastPath workers
        int totalFPs = numLBs * numFPsPerLB;
        for (int i = 0; i < totalFPs; i++) {
            FastPath fp = new FastPath(i, outputQueue, ruleManager, stats);
            fastPaths.add(fp);
            fpPool.submit(fp);
        }

        // Create LoadBalancer workers
        for (int i = 0; i < numLBs; i++) {
            // Each LB gets a subset of FPs
            int startIdx = i * numFPsPerLB;
            int endIdx = startIdx + numFPsPerLB;
            List<FastPath> assignedFPs = fastPaths.subList(startIdx, endIdx);

            LoadBalancer lb = new LoadBalancer(i, lbQueues.get(i), assignedFPs);
            loadBalancers.add(lb);
            lbPool.submit(lb);
        }

        // Start output writer thread
        Future<?> writerFuture = writerPool.submit(() -> outputWriter());

        // Read and process packets
        System.out.println("[Reader] Processing packets from: " + inputFile);
        long startTime = System.currentTimeMillis();
        readAndDispatchPackets();
        long endTime = System.currentTimeMillis();

        // Wait for pipeline to drain
        System.out.println("[Reader] Waiting for pipeline to drain...");
        Thread.sleep(1000);  // Give workers time to finish

        // Stop workers
        loadBalancers.forEach(LoadBalancer::stop);
        fastPaths.forEach(FastPath::stop);

        // Shutdown thread pools
        lbPool.shutdown();
        fpPool.shutdown();
        lbPool.awaitTermination(5, TimeUnit.SECONDS);
        fpPool.awaitTermination(5, TimeUnit.SECONDS);

        // Signal output writer to stop
        outputQueue.put(createSentinelPacket());
        writerFuture.get();  // Wait for writer to finish
        writerPool.shutdown();

        // Print results
        printResults(endTime - startTime);
    }

    private void readAndDispatchPackets() throws Exception {
        PcapHandle handle = Pcaps.openOffline(inputFile, PcapHandle.TimestampPrecision.MICRO);

        long packetId = 0;
        Packet packet;

        try {
            while ((packet = handle.getNextPacketEx()) != null) {
                long timestampSec = handle.getTimestamp().getTime() / 1000;
                long timestampMicro = (handle.getTimestamp().getTime() % 1000) * 1000;

                // Parse packet
                Optional<PacketJob> job = PacketParser.parse(packetId++, packet, timestampSec, timestampMicro);
                if (job.isEmpty()) {
                    continue;  // Skip unsupported packets
                }

                PacketJob packetJob = job.get();

                // Update stats
                stats.incrementTotalPackets();
                stats.incrementTotalBytes(packetJob.getPacketLength());
                if (packetJob.isTCP()) {
                    stats.incrementTCP();
                } else if (packetJob.isUDP()) {
                    stats.incrementUDP();
                } else {
                    stats.incrementOther();
                }

                // Hash to select LoadBalancer
                int lbIndex = Math.abs(packetJob.getTuple().hashCode()) % numLBs;
                lbQueues.get(lbIndex).put(packetJob);
            }
        } catch (EOFException e) {
            // End of file - normal
        }

        handle.close();
        System.out.println("[Reader] Done reading " + packetId + " packets");
    }

    private void outputWriter() {
        try {
            PcapHandle readHandle = Pcaps.openOffline(inputFile, PcapHandle.TimestampPrecision.MICRO);
            PcapDumper dumper = readHandle.dumpOpen(outputFile);

            long packetsWritten = 0;

            while (true) {
                PacketJob packetJob = outputQueue.take();

                // Check for sentinel (stop signal)
                if (packetJob.getPacketId() == -1) {
                    break;
                }

                // Reconstruct Packet object from raw data
                try {
                    byte[] rawData = packetJob.getRawData();
                    Packet packet = EthernetPacket.newPacket(rawData, 0, rawData.length);

                    // Create timestamp
                    java.sql.Timestamp timestamp = new java.sql.Timestamp(packetJob.getTimestampSeconds() * 1000);
                    timestamp.setNanos((int) (packetJob.getTimestampMicros() * 1000));

                    // Dump packet
                    dumper.dump(packet, timestamp);
                    packetsWritten++;
                } catch (IllegalRawDataException e) {
                    System.err.println("[Writer] Error parsing packet: " + e.getMessage());
                }
            }

            dumper.close();
            readHandle.close();

            System.out.println("[Writer] Wrote " + packetsWritten + " packets to: " + outputFile);

        } catch (Exception e) {
            System.err.println("[Writer] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private PacketJob createSentinelPacket() {
        // Sentinel packet to signal output writer to stop
        try {
            FiveTuple dummy = new FiveTuple(
                    InetAddress.getByName("0.0.0.0"),
                    InetAddress.getByName("0.0.0.0"),
                    0, 0, (byte) 0
            );
            return new PacketJob(-1, dummy, new byte[0], new byte[0], 0, 0, 0, (byte) 0, false, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void printResults(long processingTimeMs) {
        System.out.println();
        stats.printSummary();

        // Thread statistics
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                   THREAD STATISTICS                           ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        for (LoadBalancer lb : loadBalancers) {
            System.out.printf("║   LB%-2d dispatched:    %10d                          ║%n",
                    lb.getId(), lb.getPacketsDispatched());
        }
        for (FastPath fp : fastPaths) {
            System.out.printf("║   FP%-2d processed:     %10d                          ║%n",
                    fp.getId(), fp.getPacketsProcessed());
        }
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // Performance metrics
        double packetsPerSec = stats.getTotalPackets() / (processingTimeMs / 1000.0);
        double mbPerSec = (stats.getTotalBytes() / (1024.0 * 1024.0)) / (processingTimeMs / 1000.0);

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                   PERFORMANCE METRICS                         ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Processing Time:       %10.2f seconds                  ║%n", processingTimeMs / 1000.0);
        System.out.printf("║ Throughput:            %10.0f packets/sec              ║%n", packetsPerSec);
        System.out.printf("║ Bandwidth:             %10.2f MB/sec                   ║%n", mbPerSec);
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // Application breakdown
        printApplicationBreakdown();

        // Detected SNIs
        printDetectedSNIs();
    }

    private void printApplicationBreakdown() {
        Map<AppType, Long> appCounts = new HashMap<>();

        // Aggregate flows from all FastPaths
        for (FastPath fp : fastPaths) {
            for (FlowEntry flow : fp.getFlows().values()) {
                appCounts.merge(flow.getAppType(), 1L, Long::sum);
            }
        }

        if (appCounts.isEmpty()) {
            return;
        }

        long totalFlows = appCounts.values().stream().mapToLong(Long::longValue).sum();

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                   APPLICATION BREAKDOWN                       ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");

        appCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(15)
                .forEach(entry -> {
                    AppType app = entry.getKey();
                    long count = entry.getValue();
                    double percentage = (count * 100.0) / totalFlows;
                    int barLength = (int) (percentage / 2);
                    String bar = "#".repeat(Math.max(0, barLength));

                    System.out.printf("║ %-20s %5d  %5.1f%% %-20s ║%n",
                            app.getDisplayName(), count, percentage, bar);
                });

        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    private void printDetectedSNIs() {
        Set<String> detectedSNIs = new TreeSet<>();

        for (FastPath fp : fastPaths) {
            for (FlowEntry flow : fp.getFlows().values()) {
                if (flow.getSNI() != null && !flow.getSNI().isEmpty()) {
                    detectedSNIs.add(flow.getSNI() + " -> " + flow.getAppType().getDisplayName());
                }
            }
        }

        if (!detectedSNIs.isEmpty()) {
            System.out.println("\n[Detected Domains/SNIs]");
            detectedSNIs.stream().limit(20).forEach(sni -> System.out.println("  - " + sni));
            if (detectedSNIs.size() > 20) {
                System.out.println("  ... and " + (detectedSNIs.size() - 20) + " more");
            }
        }
    }

    private void printHeader() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              DPI ENGINE v" + VERSION + " (Multi-threaded Java)          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Load Balancers:  %-2d    FPs per LB:  %-2d    Total FPs:  %-2d     ║%n",
                numLBs, numFPsPerLB, numLBs * numFPsPerLB);
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    // Main entry point
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];
        int numLBs = 2;
        int numFPsPerLB = 2;

        DPIEngine engine = new DPIEngine(inputFile, outputFile, numLBs, numFPsPerLB);

        // Parse command-line options
        for (int i = 2; i < args.length; i++) {
            try {
                switch (args[i]) {
                    case "--block-ip":
                        engine.getRuleManager().blockIP(InetAddress.getByName(args[++i]));
                        break;
                    case "--block-app":
                        AppType app = AppType.valueOf(args[++i].toUpperCase());
                        engine.getRuleManager().blockApp(app);
                        break;
                    case "--block-domain":
                        engine.getRuleManager().blockDomain(args[++i]);
                        break;
                    case "--lbs":
                        numLBs = Integer.parseInt(args[++i]);
                        break;
                    case "--fps":
                        numFPsPerLB = Integer.parseInt(args[++i]);
                        break;
                    default:
                        System.err.println("Unknown option: " + args[i]);
                        printUsage();
                        System.exit(1);
                }
            } catch (Exception e) {
                System.err.println("Error parsing option: " + e.getMessage());
                System.exit(1);
            }
        }

        try {
            engine.run();
        } catch (Exception e) {
            System.err.println("Error running DPI engine: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("DPI Engine v" + VERSION + " - Multi-threaded Deep Packet Inspection");
        System.out.println("========================================================");
        System.out.println();
        System.out.println("Usage: java -jar dpi-engine.jar <input.pcap> <output.pcap> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --block-ip <ip>        Block source IP");
        System.out.println("  --block-app <app>      Block application (YOUTUBE, FACEBOOK, etc.)");
        System.out.println("  --block-domain <dom>   Block domain (substring match)");
        System.out.println("  --lbs <n>              Number of load balancer threads (default: 2)");
        System.out.println("  --fps <n>              FP threads per LB (default: 2)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar dpi-engine.jar capture.pcap filtered.pcap --block-app YOUTUBE --block-ip 192.168.1.50");
        System.out.println();
    }
}
