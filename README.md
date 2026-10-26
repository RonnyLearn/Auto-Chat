# Auto Chat

A client-side Fabric mod for Minecraft that sends chat messages automatically at a set interval. Everything runs on your client; the mod only sends the messages you write.

## Features

- Automatic messages with a start delay and an interval
- A queue of up to six messages sent in turn
- Send now button with a 15-second cooldown
- Pause that keeps the remaining time
- HUD with the timer: position, anchor, scale, opacity, colours, compact mode, RGB
- Menu colours, animation speed, transparency, 3D style and background dimming
- English, Russian, German and Spanish

## Controls

| Key | Action |
|---|---|
| F8 | Open / close the menu |
| F9 | Pause / resume |
| F10 | Reset appearance settings |

Keys can be changed in the Controls settings.

## Commands

| Command | What it does |
|---|---|
| `/localtools hint` | Show the tooltip hint in the menu again |

## Download

Get the mod from the official page: [modrinth.com/mod/auto-chat](https://modrinth.com/mod/auto-chat)

## Source code

| Minecraft | Fabric |
|---|---|
| 26.3 | [fabric/26.3](fabric/26.3) |

Requires Fabric Loader 0.20.1+, Fabric API and Java 25. To build, run `gradlew build` in `fabric/26.3`. The jar appears in `build/libs`.

## License

All rights reserved. See [LICENSE](LICENSE).
