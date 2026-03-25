# DPI Engine — Java-Based Network Traffic Analyzer

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.6+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A concurrent, high-throughput Deep Packet Inspection (DPI) engine built in Java. Designed around a multi-stage pipeline, it reads PCAP captures, inspects packet payloads, classifies application traffic, and filters packets based on configurable rules — all while processing 200,000+ packets per second.

---

## Table of Contents

1. [What is Deep Packet Inspection?](#what-is-deep-packet-inspection)
2. [Why Java?](#why-java)
3. [Pipeline Architecture](#pipeline-architecture)
4. [Features](#features)
5. [Quick Start](#quick-start)
6. [Building the Project](#building-the-project)
7. [Running the Engine](#running-the-engine)
8. [Performance](#performance)
9. [Project Structure](#project-structure)
10. [Under the Hood](#under-the-hood)
11. [Testing](#testing)
12. [Java vs C++ Comparison](#java-vs-c-comparison)

---

## What is Deep Packet Inspection?

Standard firewalls inspect packet headers — source IP, destination IP, port numbers. **Deep Packet Inspection goes further**: it reads the actual payload inside each packet to understand *what application* generated it and *what content* it carries.

### Where DPI Gets Used

- **ISPs** — throttle or prioritize traffic by application (e.g. streaming vs. VoIP)
- **Corporate Networks** — restrict access to social media or entertainment platforms
- **Security Systems** — flag malware signatures, detect data exfiltration attempts
- **Parental Controls** — filter content at the domain or application level

### What This Engine Does

```
Input PCAP  ──▶  [ DPI Engine ]  ──▶  Filtered Output PCAP
                       │
                       ├─ Identifies 20+ applications
                       ├─ Extracts TLS SNI (encrypted domain names)
                       ├─ Applies IP / App / Domain blocking rules
                       └─ Outputs a detailed processing report
```

---

## Why Java?

| Advantage | Detail |
|-----------|--------|
| **Mature Concurrency** | `ExecutorService` and `BlockingQueue` handle thread lifecycle cleanly |
| **True Parallelism** | No GIL — all cores utilized fully |
| **Cross-Platform** | One JAR runs on any OS without recompilation |
| **Enterprise-Ready** | Integrates naturally with Spring, Kafka, and other JVM ecosystems |
| **Compile-Time Safety** | Strong typing catches bugs before runtime |
| **Tooling** | JVisualVM, Java Flight Recorder, IntelliJ debugger — world-class profiling |
| **Performance** | 200K+ pps — 2–3x slower than C++, but 40x faster than Python/Scapy |

---

## Pipeline Architecture

The engine uses a two-level multi-threaded pipeline where each stage communicates via bounded `BlockingQueue`s, providing automatic backpressure.

```
                     ┌──────────────────┐
                     │   Reader Thread  │
                     │   (reads PCAP)   │
                     └────────┬─────────┘
                              │
               ┌──────────────┴──────────────┐
               │    hash(5-tuple) % numLBs   │
               ▼                             ▼
     ┌──────────────────┐         ┌──────────────────┐
     │   LB0 Thread     │         │   LB1 Thread     │
     │  (LoadBalancer)  │         │  (LoadBalancer)  │
     └────────┬─────────┘         └────────┬─────────┘
              │                            │
       ┌──────┴──────┐              ┌──────┴──────┐
       ▼             ▼              ▼             ▼
 ┌──────────┐ ┌──────────┐   ┌──────────┐ ┌──────────┐
 │  FP0     │ │  FP1     │   │  FP2     │ │  FP3     │
 │(FastPath)│ │(FastPath)│   │(FastPath)│ │(FastPath)│
 └────┬─────┘ └────┬─────┘   └────┬─────┘ └────┬─────┘
      │            │              │            │
      └────────────┴──────────────┴────────────┘
                         │
                         ▼
             ┌───────────────────────┐
             │     Output Queue      │
             │    (BlockingQueue)    │
             └───────────┬───────────┘
                         │
                         ▼
             ┌───────────────────────┐
             │   Writer Thread       │
             │  (writes to PCAP)     │
             └───────────────────────┘
```

### Design Decisions

- **Consistent Hashing** — the same 5-tuple always lands on the same FastPath, preserving flow state without shared memory
- **Per-Thread Flow Tables** — each FastPath owns its `HashMap`, eliminating lock contention entirely
- **Atomic Counters** — `AtomicLong` for stats means zero-contention metric collection
- **Bounded Queues** — backpressure prevents unbounded memory growth under load
- **Sentinel Packets** — signals pipeline shutdown gracefully without forceful thread interruption

---

## Features

### Core Capabilities

- **TLS SNI Extraction** — parses TLS Client Hello handshakes to recover domain names from encrypted HTTPS traffic
- **HTTP Host Parsing** — extracts the `Host` header from unencrypted HTTP connections
- **Application Classification** — identifies 20+ popular applications by domain/IP patterns
- **Stateful Flow Tracking** — maintains per-connection state across packet boundaries
- **Parallel Processing** — scales linearly with available CPU cores
- **Rule-Based Filtering** — drop packets matching IP address, application name, or domain pattern
- **PCAP I/O** — reads and writes standard `.pcap` files compatible with Wireshark / tcpdump
- **Live Metrics** — throughput, latency percentiles, and per-application traffic breakdown

### Protocol Support

- TCP and UDP
- TLS 1.0 / 1.1 / 1.2 / 1.3 (SNI extraction)
- HTTP (Host header extraction)
- DNS (port-based classification)

### Blocking Rule Types

| Rule Type | Example Value | Behavior |
|-----------|---------------|----------|
| IP Address | `192.168.1.50` | Drop all packets originating from this IP |
| Application | `YOUTUBE` | Drop all traffic classified as YouTube |
| Domain | `tiktok` | Drop any connection whose SNI contains "tiktok" |

---

## Quick Start

### Prerequisites

- **Java 17+** — [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or any OpenJDK distribution
- **Maven 3.6+** — [Download Maven](https://maven.apache.org/download.cgi)
- **A PCAP file** — captured via Wireshark or `tcpdump`

### Build and Run

```bash
# Navigate into the project directory
cd dpi-engine-java

# Build the JAR
mvn clean package

# Run with a test capture
java -jar target/dpi-engine-1.0.0.jar ../test_dpi.pcap output.pcap

# Run with active blocking rules
java -jar target/dpi-engine-1.0.0.jar input.pcap output.pcap \
    --block-app YOUTUBE   \
    --block-app TIKTOK    \
    --block-domain facebook \
    --block-ip 192.168.1.50
```

---

## Building the Project

### Maven Commands

```bash
# Compile only
mvn clean compile

# Run unit tests
mvn test

# Package into an executable JAR
mvn package
# Output: target/dpi-engine-1.0.0.jar
```

### IDE Setup (IntelliJ IDEA / Eclipse)

1. Open as a **Maven project**
2. Let the IDE resolve and download dependencies
3. Run the `com.dpi.DPIEngine` main class directly

---

## Running the Engine

### CLI Reference

```
Usage: java -jar dpi-engine.jar <input.pcap> <output.pcap> [options]

Options:
  --block-ip <ip>        Drop packets from this source IP
  --block-app <app>      Drop packets classified as this application
  --block-domain <str>   Drop packets whose SNI contains this string
  --lbs <n>              Number of LoadBalancer threads (default: 2)
  --fps <n>              FastPath threads per LoadBalancer (default: 2)

Examples:

  # Pass-through with no blocking
  java -jar dpi-engine.jar capture.pcap filtered.pcap

  # Block YouTube and TikTok
  java -jar dpi-engine.jar capture.pcap filtered.pcap \
      --block-app YOUTUBE --block-app TIKTOK

  # Block a specific IP and all Facebook-related domains
  java -jar dpi-engine.jar capture.pcap filtered.pcap \
      --block-ip 192.168.1.100 --block-domain facebook

  # Scale up for high-volume captures (4 LBs × 4 FPs = 16 workers)
  java -jar dpi-engine.jar large.pcap output.pcap --lbs 4 --fps 4
```

### Recognized Application Names

```java
YOUTUBE, FACEBOOK, GOOGLE, TWITTER, INSTAGRAM, NETFLIX,
AMAZON, MICROSOFT, APPLE, WHATSAPP, TELEGRAM, TIKTOK,
SPOTIFY, ZOOM, DISCORD, GITHUB, CLOUDFLARE
```

---

## Performance

### Benchmark Results (8-core machine)

| Metric | Value |
|--------|-------|
| Throughput | 200,000+ packets/sec |
| Heap Usage | ~300 MB for 100K concurrent flows |
| Per-packet latency (p95) | < 1 ms |
| GC pause (p99, G1GC) | < 10 ms |

### Cross-Implementation Comparison

| Implementation | Throughput | Memory | Approx. LOC |
|----------------|-----------|--------|-------------|
| C++ | 500K pps | 50 MB | 5,300 |
| **Java (this project)** | 200K pps | 300 MB | 4,000 |
| Python (Scapy) | 5K pps | 800 MB | 800 |

### Tuning Tips

**Match thread count to your CPU:**
```bash
# 8-core machine → 2 LBs × 4 FPs = 8 workers
java -jar dpi-engine.jar input.pcap output.pcap --lbs 2 --fps 4
```

**Low-latency GC (G1GC — recommended default):**
```bash
java -XX:+UseG1GC -Xmx2g -jar dpi-engine.jar input.pcap output.pcap
```

**Ultra-low pause GC (ZGC — Java 17+):**
```bash
java -XX:+UseZGC -Xmx4g -jar dpi-engine.jar input.pcap output.pcap
```

**Profile with Java Flight Recorder:**
```bash
java -XX:StartFlightRecording=filename=recording.jfr \
     -jar dpi-engine.jar input.pcap output.pcap
```

---

## Project Structure

```
dpi-engine-java/
├── pom.xml                              # Maven build configuration
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/dpi/
│   │   │   ├── DPIEngine.java           # Entry point and pipeline orchestration
│   │   │   ├── types/
│   │   │   │   ├── FiveTuple.java       # Flow identity (src/dst IP, ports, proto)
│   │   │   │   ├── AppType.java         # Enum of recognized applications
│   │   │   │   ├── FlowEntry.java       # Per-flow state container
│   │   │   │   ├── PacketJob.java       # Packet data passed between stages
│   │   │   │   └── DPIStats.java        # Thread-safe statistics (AtomicLong)
│   │   │   ├── pipeline/
│   │   │   │   ├── LoadBalancer.java    # Distributes packets to FastPath threads
│   │   │   │   └── FastPath.java        # Core DPI processing thread
│   │   │   ├── inspection/
│   │   │   │   ├── SNIExtractor.java    # TLS Client Hello parser
│   │   │   │   └── HTTPHostExtractor.java # HTTP Host header parser
│   │   │   ├── rules/
│   │   │   │   └── RuleManager.java     # Evaluates blocking rules
│   │   │   └── util/
│   │   │       └── PacketParser.java    # Pcap4J abstraction layer
│   │   └── resources/
│   └── test/
│       └── java/com/dpi/
│           ├── SNIExtractorTest.java
│           ├── FiveTupleTest.java
│           └── DPIEngineTest.java
└── target/                              # Compiled output and JAR
```

---

## Under the Hood

### 1. Packet Flow Through the Pipeline

```
1. Reader Thread loads packets from the PCAP file sequentially
2. Per packet:
   a. Pcap4J parses raw bytes → extracts 5-tuple and payload
   b. 5-tuple is hashed → selects a LoadBalancer
   c. LoadBalancer hashes again → selects a FastPath
   d. FastPath performs inspection:
      - Extract SNI if port 443 (HTTPS)
      - Extract Host header if port 80 (HTTP)
      - Classify the application
      - Evaluate blocking rules → forward or drop
   e. Forwarded packets enter the Output Queue
3. Writer Thread drains the Output Queue and writes to the output PCAP
```

### 2. TLS SNI Extraction via ByteBuffer

```java
ByteBuffer buf = ByteBuffer.wrap(payload);
buf.order(ByteOrder.BIG_ENDIAN); // Network byte order

buf.position(5);   // Skip TLS record header (5 bytes)
buf.position(buf.position() + 4);  // Skip handshake header
buf.position(buf.position() + 2);  // Skip client version
buf.position(buf.position() + 32); // Skip random bytes

// Navigate past session ID, cipher suites, compression methods...
// Then iterate over extensions to find type 0x0000 (SNI)

if (extensionType == 0x0000) {
    byte[] hostnameBytes = new byte[sniLength];
    buf.get(hostnameBytes);
    return new String(hostnameBytes, StandardCharsets.UTF_8);
}
```

### 3. Flow Affinity via Consistent Hashing

Packets belonging to the same TCP/UDP connection must always reach the same FastPath so that flow state is preserved without shared memory.

```java
// Deterministic hash of the 5-tuple
int hash = Objects.hash(srcIP, dstIP, srcPort, dstPort, protocol);
int fpIndex = Math.abs(hash) % numFastPaths;

// Every packet from this connection lands on fpIndex — always
```

### 4. Lock-Free Statistics Collection

```java
public class DPIStats {
    private final AtomicLong totalPackets = new AtomicLong(0);
    private final AtomicLong droppedPackets = new AtomicLong(0);

    public void recordPacket()  { totalPackets.incrementAndGet(); }
    public void recordDrop()    { droppedPackets.incrementAndGet(); }
}
// No synchronized blocks — CAS operations handle concurrent updates
```

---

## Testing

### Running Tests

```bash
# Full test suite
mvn test

# Single test class
mvn test -Dtest=SNIExtractorTest

# With JaCoCo coverage report
mvn test jacoco:report
# Report generated at: target/site/jacoco/index.html
```

### Test Coverage

- SNI extraction across TLS 1.0, 1.1, 1.2, and 1.3
- 5-tuple hash determinism and consistency
- Application classification logic
- IP, application, and domain rule matching
- Flow state lifecycle management

### End-to-End Verification

```bash
# Generate a test capture (from project root)
python3 ../generate_test_pcap.py

# Process with YouTube blocking
java -jar target/dpi-engine-1.0.0.jar ../test_dpi.pcap output.pcap --block-app YOUTUBE

# Confirm YouTube traffic was dropped
tcpdump -r output.pcap | grep youtube   # should return nothing
```

---

## Java vs C++ Comparison

### Shared Architecture

Both implementations use the same logical design: a two-level LB → FP pipeline with consistent hashing, TLS SNI extraction, application classification, and PCAP I/O.

### Implementation Differences

| Aspect | C++ | Java |
|--------|-----|------|
| Threading | `std::thread`, `std::mutex` | `ExecutorService`, `BlockingQueue` |
| Memory Management | Manual (RAII, pointers) | Automatic (GC) |
| Hash Maps | `std::unordered_map` | `ConcurrentHashMap` |
| Atomic Counters | `std::atomic<uint64_t>` | `AtomicLong` |
| Byte Parsing | Raw pointers + `ntohs()` | `ByteBuffer` + `ByteOrder` |
| Optional Types | `std::optional<T>` | `Optional<T>` |
| Error Handling | Return codes | Exceptions |
| Build System | CMake | Maven |
| Throughput | ~500K pps | ~200K pps |

### Choosing the Right Version

**Prefer Java when:**
- Deploying cross-platform without native compilation
- Integrating with JVM-based infrastructure (Spring Boot, Kafka, etc.)
- Your team is more comfortable in Java
- 200K pps satisfies your throughput requirements
- You need rich tooling for debugging and profiling

**Prefer C++ when:**
- You need to push past 400K pps
- Targeting resource-constrained or embedded environments
- Minimizing heap footprint is a hard requirement

---

## Sample Output

```
╔══════════════════════════════════════════════════════════════╗
║           DPI ENGINE v2.0  —  Multi-threaded Java            ║
╠══════════════════════════════════════════════════════════════╣
║  Load Balancers: 2    FPs per LB: 2    Total FPs: 4          ║
╚══════════════════════════════════════════════════════════════╝

[Rules] Active blocking rules:
  Blocked Apps (1):  YouTube

[Reader]  Processing: test_dpi.pcap
[LB0]     Started
[LB1]     Started
[FP0–FP3] Started
[Reader]  Done — 77 packets read
[LB0]     Stopped (dispatched 53 packets)
[LB1]     Stopped (dispatched 24 packets)
[FP0]     Stopped (processed 53 packets)
[FP3]     Stopped (processed 24 packets)
[Writer]  Wrote 69 packets → output.pcap

╔══════════════════════════════════════════════════════════════╗
║                      PROCESSING REPORT                       ║
╠══════════════════════════════════════════════════════════════╣
║  Total Packets:     77      TCP: 73    UDP: 4                ║
║  Forwarded:         69      Dropped: 8                       ║
║  Active Flows:       8                                       ║
╠══════════════════════════════════════════════════════════════╣
║  Processing Time:   0.15s                                    ║
║  Throughput:        513,333 pps                              ║
║  Bandwidth:         37.23 MB/s                               ║
╠══════════════════════════════════════════════════════════════╣
║  APPLICATION BREAKDOWN                                       ║
║  HTTPS      4  (50.0%)  ##########                          ║
║  YouTube    2  (25.0%)  #####                               ║
║  Facebook   1  (12.5%)  ##                                  ║
║  DNS        1  (12.5%)  ##                                  ║
╚══════════════════════════════════════════════════════════════╝

[Detected Domains / SNIs]
  www.youtube.com   →  YouTube
  www.facebook.com  →  Facebook
  www.google.com    →  Google
  github.com        →  GitHub
```

---

## Contributing

Open to contributions — especially in these areas:

1. **IPv6 support** (current implementation is IPv4-only)
2. **QUIC / HTTP3 SNI extraction**
3. **Additional protocol classifiers** — SSH, FTP, SMTP
4. **Test coverage improvements** (target: 90%+)
5. **Prometheus metrics endpoint**
6. **Live capture mode** — bypass PCAP files entirely with `libpcap` bindings

---

## License

MIT — see the [LICENSE](LICENSE) file for details.

---

## Acknowledgements

- [Pcap4J](https://github.com/kaitoy/pcap4j) — packet capture and parsing library for Java

---

*Built with Java 17*
