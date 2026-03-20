package com.dpi.util;

import com.dpi.types.FiveTuple;
import com.dpi.types.PacketJob;
import org.pcap4j.packet.*;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Packet Parser - Extracts five-tuple and payload from Pcap4J packets
 */
public class PacketParser {

    /**
     * Parse Pcap4J packet into our PacketJob format
     */
    public static Optional<PacketJob> parse(long packetId, Packet packet,
                                           long timestampSec, long timestampMicro) {
        try {
            // Extract Ethernet packet
            if (!(packet instanceof EthernetPacket)) {
                return Optional.empty();
            }

            EthernetPacket ethPacket = (EthernetPacket) packet;

            // Extract IP packet
            if (!ethPacket.contains(IpV4Packet.class)) {
                return Optional.empty();  // Skip non-IPv4 packets
            }

            IpV4Packet ipPacket = ethPacket.get(IpV4Packet.class);
            IpV4Packet.IpV4Header ipHeader = ipPacket.getHeader();

            InetAddress srcIP = ipHeader.getSrcAddr();
            InetAddress dstIP = ipHeader.getDstAddr();
            byte protocol = ipHeader.getProtocol().value();

            // Extract TCP or UDP
            if (ipPacket.contains(TcpPacket.class)) {
                return parseTCP(packetId, ipPacket, srcIP, dstIP, protocol,
                        timestampSec, timestampMicro, packet.getRawData());
            } else if (ipPacket.contains(UdpPacket.class)) {
                return parseUDP(packetId, ipPacket, srcIP, dstIP, protocol,
                        timestampSec, timestampMicro, packet.getRawData());
            }

            return Optional.empty();

        } catch (Exception e) {
            // Parsing error - skip packet
            return Optional.empty();
        }
    }

    private static Optional<PacketJob> parseTCP(long packetId, IpV4Packet ipPacket,
                                                InetAddress srcIP, InetAddress dstIP, byte protocol,
                                                long timestampSec, long timestampMicro, byte[] rawData) {
        TcpPacket tcpPacket = ipPacket.get(TcpPacket.class);
        TcpPacket.TcpHeader tcpHeader = tcpPacket.getHeader();

        int srcPort = tcpHeader.getSrcPort().valueAsInt();
        int dstPort = tcpHeader.getDstPort().valueAsInt();

        // Create five-tuple
        FiveTuple tuple = new FiveTuple(srcIP, dstIP, srcPort, dstPort, protocol);

        // Extract TCP flags
        byte tcpFlags = (byte) ((tcpHeader.getSyn() ? 0x02 : 0) |
                                (tcpHeader.getAck() ? 0x10 : 0) |
                                (tcpHeader.getFin() ? 0x01 : 0) |
                                (tcpHeader.getRst() ? 0x04 : 0) |
                                (tcpHeader.getPsh() ? 0x08 : 0));

        // Extract payload
        byte[] payload = null;
        if (tcpPacket.getPayload() != null) {
            payload = tcpPacket.getPayload().getRawData();
        }
        if (payload == null) {
            payload = new byte[0];
        }

        return Optional.of(new PacketJob(
                packetId, tuple, rawData, payload,
                timestampSec, timestampMicro, rawData.length,
                tcpFlags, true, false
        ));
    }

    private static Optional<PacketJob> parseUDP(long packetId, IpV4Packet ipPacket,
                                                InetAddress srcIP, InetAddress dstIP, byte protocol,
                                                long timestampSec, long timestampMicro, byte[] rawData) {
        UdpPacket udpPacket = ipPacket.get(UdpPacket.class);
        UdpPacket.UdpHeader udpHeader = udpPacket.getHeader();

        int srcPort = udpHeader.getSrcPort().valueAsInt();
        int dstPort = udpHeader.getDstPort().valueAsInt();

        // Create five-tuple
        FiveTuple tuple = new FiveTuple(srcIP, dstIP, srcPort, dstPort, protocol);

        // Extract payload
        byte[] payload = null;
        if (udpPacket.getPayload() != null) {
            payload = udpPacket.getPayload().getRawData();
        }
        if (payload == null) {
            payload = new byte[0];
        }

        return Optional.of(new PacketJob(
                packetId, tuple, rawData, payload,
                timestampSec, timestampMicro, rawData.length,
                (byte) 0, false, true
        ));
    }
}
