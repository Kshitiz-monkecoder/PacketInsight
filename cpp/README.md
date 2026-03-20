# DPI Engine — C++ Implementation

This directory contains the **C++ implementation** of the DPI Engine. See the [root README](../README.md) for an overview of the project and comparison with the Java version.

---

## Prerequisites

- **macOS / Linux** with C++17 compiler (`g++` or `clang++`)
- No external libraries required

> For **Windows** setup instructions, see [WINDOWS_SETUP.md](WINDOWS_SETUP.md).

---

## Building

### Single-threaded Version (learning / small captures)

```bash
g++ -std=c++17 -O2 -I include -o dpi_simple \
    src/main_working.cpp \
    src/pcap_reader.cpp \
    src/packet_parser.cpp \
    src/sni_extractor.cpp \
    src/types.cpp
```

### Multi-threaded Version (production / large captures)

```bash
g++ -std=c++17 -pthread -O2 -I include -o dpi_engine \
    src/dpi_mt.cpp \
    src/pcap_reader.cpp \
    src/packet_parser.cpp \
    src/sni_extractor.cpp \
    src/types.cpp
```

---

## Running

```bash
# Basic run
./dpi_engine test_dpi.pcap output.pcap

# With blocking rules
./dpi_engine test_dpi.pcap output.pcap \
    --block-app YouTube \
    --block-app TikTok \
    --block-ip 192.168.1.50 \
    --block-domain facebook

# Configure thread count (multi-threaded only)
./dpi_engine input.pcap output.pcap --lbs 4 --fps 4
```

---

## Generating Test Data

```bash
python3 generate_test_pcap.py
# Creates test_dpi.pcap with sample traffic
```

---

## File Structure

```
cpp/
├── include/                    # Header files
│   ├── pcap_reader.h          # PCAP file reading
│   ├── packet_parser.h        # Network protocol parsing
│   ├── sni_extractor.h        # TLS/HTTP inspection
│   ├── types.h                # Data structures (FiveTuple, AppType, etc.)
│   ├── dpi_engine.h           # Main orchestrator
│   ├── fast_path.h            # FP processing thread
│   ├── load_balancer.h        # LB distribution thread
│   ├── connection_tracker.h   # Flow tracking
│   ├── rule_manager.h         # Blocking rules
│   ├── thread_safe_queue.h    # Thread-safe queue
│   └── platform.h             # Platform detection
│
├── src/                        # Source files
│   ├── main_working.cpp       # ★ Single-threaded version
│   ├── dpi_mt.cpp             # ★ Multi-threaded version
│   ├── pcap_reader.cpp        # PCAP I/O
│   ├── packet_parser.cpp      # Protocol parsing
│   ├── sni_extractor.cpp      # SNI extraction
│   ├── types.cpp              # App classification helpers
│   ├── dpi_engine.cpp         # Engine orchestration
│   ├── fast_path.cpp          # FastPath processing
│   ├── load_balancer.cpp      # Load balancer logic
│   ├── connection_tracker.cpp # Connection tracking
│   ├── rule_manager.cpp       # Rule matching
│   ├── main.cpp               # Alternate entry point
│   ├── main_dpi.cpp           # Alternate entry point
│   └── main_simple.cpp        # Minimal entry point
│
├── CMakeLists.txt             # CMake build config
├── WINDOWS_SETUP.md           # Windows build guide
├── generate_test_pcap.py      # Test data generator
└── test_dpi.pcap              # Sample capture
```
