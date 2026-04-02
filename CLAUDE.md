# CityBuild Core — Projekt-Kontext

## Technologie
- Minecraft Paper API 1.21.1
- Java 17
- Maven mit SQLite (shade) + HikariCP (shade)
- Package-Root: de.citybuild.core

## Architektur
- Modulares Plugin-System: Core + Addon-Plugins
- Addon-Plugins docken via CityBuildAPI (Bukkit ServiceManager) an
- Session 1: Generator-Engine (fertig)
- Session 2: Plugin-Logik (in Arbeit)
- Session 3: API + Addon-Gerüst (ausstehend)

## Wichtige Regeln
- Block-Platzierung IMMER auf Main Thread
- Berechnungen können async laufen
- Keine externen Libraries außer sqlite-jdbc und HikariCP
- Alle public Methoden mit JavaDoc