# Rover21

Network management utility for PLANET switches. Provides device discovery, RSTP topology visualization, and configuration management through a modern graphical interface.

## Features

### Device Discovery
- Automatic detection of PLANET switches on the local network via UDP broadcast
- SNMP-based device information retrieval (model, firmware, location, MAC address)
- Protocol availability testing (SSH, SNMP, HTTP/HTTPS)
- Support for multiple network adapters

### RSTP Topology Visualization
- Interactive network graph with zoom, pan, and drag capabilities
- Hierarchical layout based on spanning tree hop count
- Real-time display of link states (forwarding/blocking)
- Visual identification of root bridge and port roles
- Dynamic edge labels showing port connections

### Configuration Management
- SSH-based configuration backup and download
- Secure connection handling with credential management
- Support for various PLANET switch CLI formats

### Data Export
- Export device inventory to Excel (XLSX)
- Customizable export fields

## Requirements

- Java 25 or higher
- Network access to target switches
- SNMP community string (default: public)
- SSH credentials for advanced features

## Installation

### From Source

```bash
git clone https://github.com/adm21/Rover21.git
cd Rover21
mvn clean package
java -jar target/Rover21.jar
```

### Pre-built Release

Download the latest release from the Releases page and run:

```bash
java -jar Rover21.jar
```

## Usage

### Quick Start

1. Launch the application
2. Select your network adapter from the dropdown menu
3. Click "Discover" to scan for PLANET switches
4. Double-click a device to view details or right-click for options

### RSTP Topology

1. Select one or more devices in the main table
2. Click the "RSTP" button in the toolbar
3. The topology dialog displays both table and graph views
4. Use the graph controls to zoom, pan, or reset the layout
5. Click on a node to view detailed port information

### Configuration Backup

1. Right-click on a device
2. Select "Download Configuration"
3. Enter SSH credentials if not already saved
4. Choose the destination folder

## Project Structure

```
src/main/java/com/rover/
    controller/     Main application controller
    model/          Data models (PlanetSwitchDevice, RSTPNode, RSTPLink)
    services/       Business logic and network operations
        rstp/       RSTP-specific services
    ui/             Swing UI components
        graph/      JUNG-based topology visualization
    utils/          Helper classes (SSH, preferences, i18n)
```

## Technology Stack

| Component | Library |
|-----------|---------|
| UI Framework | Swing with FlatLaf 3.2.5 |
| SNMP | SNMP4J 3.7.7 |
| SSH | JSch 0.1.55 |
| Graph Visualization | JUNG 2.1.1 |
| Excel Export | Apache POI 5.4.0 |
| Logging | Logback 1.5.6 (via SLF4J) |

## Configuration

Application settings are stored in user preferences and can be modified through the Preferences dialog:

- SNMP community string and timeout
- SSH default port and credentials
- Discovery timeout settings
- Language selection (English/French)

## Logging

Rover21 uses SLF4J with Logback for logging. Logs are written to:

- **Console**: Colored output by log level
- **File**: `~/.rover21/logs/rover21.log` (daily rotation, 30-day retention)
- **In-app viewer**: View > View Logs

Log levels can be configured in `src/main/resources/logback.xml`.

## Network Protocols

Rover21 uses the following protocols to communicate with switches:

| Protocol | Port | Purpose |
|----------|------|---------|
| UDP | 10000 | PLANET device discovery broadcast |
| SNMP | 161 | Device information retrieval |
| SSH | 22 | CLI access, configuration backup, RSTP/LLDP data |
| HTTP | 80 | Web interface availability check |
| HTTPS | 443 | Secure web interface check |

## Supported Devices

Tested with PLANET managed switches including:

- IGS-6xxx series
- IGS-4xxx series
- IGS-801M
- GS-4210 series
- Other PLANET switches supporting standard SNMP MIBs

## Building

### Prerequisites

- JDK 25+
- Maven 3.6+

### Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package (creates executable JAR with dependencies)
mvn package
```

## License

Copyright 2026 ADM21. All rights reserved.

## Developer

Benjamin FOUILHAC - ADM21

## Contact

If you have any questions about this projet, feel free to contact me at: [bfouilhac@gmail.com](mailto:bfouilhac@gmail.com)
