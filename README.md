# CarryMe

CarryMe is a lightweight Minecraft plugin for Spigot, Paper, and Folia servers that lets players carry each other with clickable requests, configurable aliases, update notifications, and optional GSit compatibility.

## Features

- Clickable carry requests with one-click accept and reject buttons
- Configurable command aliases for the built-in commands
- Request expiration and automatic cleanup when players leave
- Operator update notifications with a clickable Modrinth download link
- Optional GSit hook to block carrying seated, laying, or posing players
- No NMS usage, which helps keep the plugin simple across supported versions

## Compatibility

CarryMe builds two jars from the same codebase:

| Jar | Server versions | Notes |
| --- | --- | --- |
| `CarryMe-<version>-legacy.jar` | 1.8.8 - 1.12.2 | Java 8+, no `api-version` in `plugin.yml` |
| `CarryMe-<version>.jar` | 1.13+ | Java 8+ bytecode, includes `api-version: 1.13` and Folia support metadata |

Folia compatibility is handled through runtime scheduler detection, so the modern jar still works on Spigot and Paper. Folia itself is only available on newer Paper/Folia servers, not on the legacy Minecraft versions.

## Installation

1. Download the latest CarryMe jar from [Modrinth](https://modrinth.com/plugin/carryme).
2. Place it in your server's `plugins/` folder.
3. Start the server once to generate `config.yml` and `message.yml`.
4. Edit the files to match your server setup.
5. Restart the server.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/carry <player>` | Sends a carry request to a player | `carryme.cargar` |
| `/accept` | Accepts a pending carry request | `carryme.aceptar` |
| `/reject` | Rejects a pending carry request | `carryme.rechazar` |
| `/drop` | Drops the player you are carrying | `carryme.soltar` |
| `/carryme reload` | Reloads the plugin configuration and messages | `carryme.reload` |

## Main Permissions

| Permission | Description |
| --- | --- |
| `carryme.cargar` | Allows sending carry requests |
| `carryme.aceptar` | Allows accepting carry requests |
| `carryme.rechazar` | Allows rejecting carry requests |
| `carryme.soltar` | Allows dropping carried players |
| `carryme.reload` | Allows reloading CarryMe |
| `carryme.update` | Receives update notifications on join |

## Configuration

CarryMe generates these main files on first startup:

- `plugins/CarryMe/config.yml`
- `plugins/CarryMe/message.yml`

Notable configurable areas include:

- maximum carry distance
- request expiration time
- command aliases
- plugin prefix
- action and error messages
- update-check join and console messages

## Building

This project uses Gradle.

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The publishable jars will be generated in:

```text
build/libs/
```

## Links

- Download: [Modrinth](https://modrinth.com/plugin/carryme)

## License

This repository does not include a license file yet. Add your preferred open-source license before publishing it publicly on GitHub.
