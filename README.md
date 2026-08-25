# SyncNuke Desktop

SyncNuke Desktop connects a local video player to a SyncNuke synchronization server. It translates synchronized playback state into player commands while keeping player-specific integrations separate from the synchronization core.

MPV is currently the only supported player, more to come in the future.

## Requirements

- Java 21
- MPV Player
- A running SyncNuke-compatible server

## Building

Clone the repository, then build the Desktop fat JAR:

```shell
git clone git@github.com:if-shouldrs/syncnuke-desktop.git
cd syncnuke-desktop
./scripts/build.sh
```

On Windows, use `scripts\build.bat` instead.

The executable artifact is generated at:

```text
build/libs/syncnuke-desktop-1.0-SNAPSHOT-all.jar
```

## Usage

SyncNuke Desktop can either launch MPV itself or connect to an MPV instance that already exposes an IPC endpoint.

### Launch MPV

On Linux or macOS, create a directory for the Unix socket and pass its expanded path:

```shell
mkdir -p "$HOME/.mpv-ipc"

java -jar build/libs/syncnuke-desktop-1.0-SNAPSHOT-all.jar \
  --player mpv \
  --player-host "$HOME/.mpv-ipc/mpvsocket" \
  --launch-player \
  --host localhost \
  --port 8999 \
  --protocol datasaver \
  --file "/path/to/video.mkv"
```

On Windows, use a named pipe as the player host:

```powershell
java -jar build/libs/syncnuke-desktop-1.0-SNAPSHOT-all.jar `
  --player mpv `
  --player-host '\\.\pipe\mpvsocket' `
  --launch-player `
  --host localhost `
  --port 8999 `
  --protocol datasaver `
  --file 'C:\path\to\video.mkv'
```

The application starts MPV with an idle window and configures the supplied IPC endpoint. It exits when the MPV process exits.

### Or run the scripts

The MPV launcher scripts provide the correct player and IPC options for each platform.

On Linux:

```shell
./scripts/mpv/start.sh \
  --host localhost \
  --port 8999 \
  --protocol datasaver \
  --file "/path/to/video.mkv"
```

On Windows:

```batch
scripts\mpv\start.bat ^
  --host localhost ^
  --port 8999 ^
  --protocol datasaver ^
  --file "C:\path\to\video.mkv"
```

All forwarded arguments are optional. For example, running `./scripts/mpv/start.sh` or `scripts\mpv\start.bat` without arguments uses the application defaults and does not pass a media file.


## Command-line options

| Option | Description | Default |
| --- | --- | --- |
| `--player <name>` | Video player provider | `mpv` |
| `--player-host <host>` | Player IPC socket, named pipe, or network endpoint | Required |
| `--launch-player` | Launch the selected player before connecting | Disabled |
| `--host <host>` | Synchronization server host | `localhost` |
| `--port <port>` | Synchronization server port | `8999` |
| `--protocol <protocol>` | Synchronization protocol: `datasaver`, etc. | `datasaver` |
| `--file <path>` | Media file to load after connecting | Current player media |

The player connection can also be configured with JVM system properties:

```shell
java \
  -Dsyncnuke.player.host="$HOME/.mpv-ipc/mpvsocket" \
  -Dsyncnuke.player.launch=true \
  -jar build/libs/syncnuke-desktop-1.0-SNAPSHOT-all.jar
```

An explicit `--player-host` takes precedence over `syncnuke.player.host`.

## License

[![GNU Affero General Public License v3](https://www.gnu.org/graphics/agplv3-155x51.png)](LICENSE.md)

SyncNuke Desktop is free software licensed under the [GNU Affero General Public License v3.0](LICENSE.md) (`AGPL-3.0-only`). You may use, modify, and redistribute it under the terms of that license.

This software is provided without warranty, to the extent permitted by applicable law. See [LICENSE.md](LICENSE.md) for the complete license terms, including the warranty disclaimer and limitation of liability.
