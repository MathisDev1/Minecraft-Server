# CityBuild Core Plugin

A high-performance, procedural city generator and plot management system for Minecraft 1.21.1 / Paper.

## Overview
CityBuild Core provides a unique experience by procedurally generating a complex city layout including terrain, rivers, lakes, and a multi-tiered road network. It features a robust spatial plot management system, built-in protection, and a flexible API for addons.

## Features
- **Procedural City Generation**: 7-phase generation pipeline (Terrain, Water, Roads, Plots).
- **Advanced Road Network**: MST-based arteries, secondary branches, and tertiary connectors.
- **Dynamic Plot Management**: Automatic subdivision of city blocks into purchasable plots.
- **Built-in Protection**: Automatic protection for claimed and unclaimed plots (TNT, liquid flow, block breaking).
- **Extensible API**: Easy-to-use API for adding Economy, NPC, or custom logic.
- **Performance Optimized**: Async generation and O(1) spatial lookups.

## Installation
1. Ensure you are running **Paper 1.21.1** or higher.
2. Use **Java 17** or higher.
3. Download the `CityBuildCore.jar`.
4. Place the JAR in your server's `plugins/` directory.
5. Restart the server.

## Setup
To generate a new city:
1. Join the server.
2. Type `/cbsetup generate`.
3. Follow the progress in the chat.
4. Once completed, players can start claiming plots using `/plot claim`.

## Commands
| Command | Description | Permission |
|:---|:---|:---|
| `/plot claim` | Claims the plot you are standing on | `citybuild.plot.claim` |
| `/plot info` | Shows information about the current plot | `citybuild.plot.info` |
| `/plot trust <player>` | Trusts a player on your plot | `citybuild.plot.trust` |
| `/plot tp <id>` | Teleports to a specific plot | `citybuild.plot.tp` |
| `/adminplot reset <id>` | Resets a plot (Admin) | `citybuild.admin` |
| `/cbsetup generate` | Starts the city generation | `citybuild.setup` |
| `/spawn` | Teleports to the city spawn | `citybuild.spawn` |

## Configuration
The `config.yml` allows you to customize various aspects:
- `world-name`: The name of the city world.
- `generator.map-radius`: Size of the generated city.
- `economy.price-per-block`: Base price for plot square meters.
- `protection.prevent-fire-spread`: Stop fire from destroying the city.

## For Developers (Addon API)
Add CityBuild Core as a provided dependency in your `pom.xml`:

```xml
<dependency>
    <groupId>de.citybuild</groupId>
    <artifactId>citybuild-core</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Loading the API
```java
CityBuildAPI api = Bukkit.getServicesManager().load(CityBuildAPI.class);
```

### Registering an Economy Provider
```java
api.registerEconomyProvider(new MyEconomyProvider());
```

### Listening to Events
```java
@EventHandler
public void onPlotClaim(PlotClaimEvent event) {
    Player player = event.getPlayer();
    Plot plot = event.getPlot();
    // Custom logic
}
```

## Modular System
CityBuild Core is designed to be modular. Features like complex Economy, custom NPCs, or Quests should be implemented as separate "Addon" plugins using the provided API.

## Build
To build the project yourself:
1. Clone the repository.
2. Run `mvn clean package`.
3. Find the JAR in the `target/` directory.
