# SyncNuke Desktop

SyncNuke Desktop connects a local video player to a SyncNuke synchronization server. It translates synchronized playback state into player commands while keeping player-specific integrations separate from the synchronization core.

MPV is currently the only supported player, more to come in the future.

## Requirements

- Java 21
- MPV Player

## Building

Clone the repository, then build the Desktop fat JAR:

```shell
git clone git@github.com:if-shouldrs/syncnuke-desktop.git
cd syncnuke-desktop
./scripts/build/build.sh
```

On Windows, use `scripts\build\build.bat` instead.

The executable artifact is generated at:

```text
build/libs/syncnuke-desktop-*-all.jar
```

## Usage

SyncNuke Desktop can either launch MPV itself or connect to an MPV instance that already exposes an IPC endpoint.

### Launch MPV

On Linux or macOS, create a directory for the Unix socket and pass its expanded path:

```shell
mkdir -p "$HOME/.mpv-ipc"

java -jar build/libs/syncnuke-desktop-*-all.jar \
  --player mpv \
  --protocol datasaver \
  --user alice \
  --room movie-night \
  --password myRoomPassword \
  --file "/path/to/video.mkv"
```

On Windows, use a named pipe as the player host:

```powershell
java -jar build/libs/syncnuke-desktop-*-all.jar `
  --player mpv `
  --protocol datasaver `
  --user alice `
  --room movie-night `
  --password myRoomPassword `
  --file 'C:\path\to\video.mkv'
```

The application starts MPV with an idle window and configures the supplied IPC endpoint. It exits when the MPV process exits.

### Or run the scripts

The MPV launcher scripts provide the correct player and IPC options for each platform.

On Linux:

```shell
./scripts/mpv/start.sh \
  --user alice \
  --room movie-night \
  --password myRoomPassword
```

On Windows:

```batch
scripts\mpv\start.bat ^
  --user alice ^
  --room movie-night ^
  --password myRoomPassword
 */
```



## Command-line options

| Option | Description | Default               |
| --- | --- |-----------------------|
| `--user <name>` | Username used to join the synchronization server | Required              |
| `--room <name>` | Synchronization room to join | Required              |
| `--password <password>` | Room password | Optional              |
| `--file <path>` | Media file to load after connecting | Optional              |
| `--protocol <protocol>` | Synchronization protocol: `datasaver`, etc. | `datasaver`           |
| `--player <name>` | Video player provider | Optional              |
| `--player-host <host>` | Player IPC socket, named pipe, or network endpoint | Optional              |
| `--player-executable <path>` | Player executable or containing directory | Optional              |
| `--polling-rate <milliseconds>` | Player polling interval | Optional              |

The player connection can also be configured with JVM system properties:

```shell
java \
  -Dsyncnuke.player.host="$HOME/.mpv-ipc/mpvsocket" \
  -Dsyncnuke.mpv.executable="/path/to/mpv" \
  -jar build/libs/syncnuke-desktop-*-all.jar
```

## Usability

This application  is still in early development, it's expected behaviour in terms of usability will vary a lot before release `1.0.0`, please be patient. Thank you!

## License

[![GNU Affero General Public License v3](https://www.gnu.org/graphics/agplv3-155x51.png)](LICENSE.md)

SyncNuke Desktop is free software licensed under the [GNU Affero General Public License v3.0](LICENSE.md) (`AGPL-3.0-only`). You may use, modify, and redistribute it under the terms of that license.

This software is provided without warranty, to the extent permitted by applicable law. See [LICENSE.md](LICENSE.md) for the complete license terms, including the warranty disclaimer and limitation of liability.
