#!/bin/bash

# Build script for DPI Engine (Java)

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║              Building DPI Engine (Java)                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven first."
    echo "   Download from: https://maven.apache.org/download.cgi"
    exit 1
fi

# Check Java version
echo "✓ Checking Java version..."
java -version 2>&1 | head -1

# Clean and build
echo ""
echo "✓ Cleaning previous builds..."
mvn clean -q

echo "✓ Compiling sources..."
mvn compile -q

echo "✓ Running tests..."
mvn test -q

echo "✓ Packaging JAR..."
mvn package -q

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                      Build Successful!                        ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║ JAR Location: target/dpi-engine-1.0.0.jar                    ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "Usage:"
echo "  java -jar target/dpi-engine-1.0.0.jar <input.pcap> <output.pcap>"
echo ""
echo "Example:"
echo "  java -jar target/dpi-engine-1.0.0.jar ../test_dpi.pcap output.pcap --block-app YOUTUBE"
echo ""
