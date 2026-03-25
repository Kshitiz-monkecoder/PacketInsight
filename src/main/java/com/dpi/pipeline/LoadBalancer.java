package com.dpi.pipeline;

import com.dpi.types.PacketJob;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load Balancer Thread - Distributes packets to Fast Path workers
 * Uses consistent hashing on five-tuple to maintain flow affinity
 */
public class LoadBalancer implements Runnable {
    private final int id;
    private final BlockingQueue<PacketJob> inputQueue;
    private final List<FastPath> fastPaths;
    private final AtomicLong packetsDispatched = new AtomicLong(0);
    private volatile boolean running = true;

    public LoadBalancer(int id, BlockingQueue<PacketJob> inputQueue, List<FastPath> fastPaths) {
        this.id = id;
        this.inputQueue = inputQueue;
        this.fastPaths = fastPaths;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("LB-" + id);
        System.out.println("[LB" + id + "] Started");

        while (running) {
            try {
                PacketJob packet = inputQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (packet == null) {
                    continue;  // Timeout, check running flag
                }

                // Use consistent hashing on five-tuple to select FastPath
                int fpIndex = Math.abs(packet.getTuple().hashCode()) % fastPaths.size();
                FastPath targetFP = fastPaths.get(fpIndex);

                // Dispatch to selected FastPath
                targetFP.enqueue(packet);
                packetsDispatched.incrementAndGet();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[LB" + id + "] Error: " + e.getMessage());
            }
        }

        System.out.println("[LB" + id + "] Stopped (dispatched " + packetsDispatched.get() + " packets)");
    }

    public void stop() {
        running = false;
    }

    public long getPacketsDispatched() {
        return packetsDispatched.get();
    }

    public int getId() {
        return id;
    }
}
