# Ciel Launcher

A floating "chathead" overlay for a Skylight calendar device. A draggable
always-on-top face sits over whatever is on screen; **tap** it for a radial
menu, **long-press** it to choose what the menu contains.

## Features

- Always-on-top draggable bubble; starts on boot.
- Radial menu of circular app icons with labels, plus **Back** and **Home**
  (return to the Skylight calendar). Layout adapts to the number of items.
- **URL shortcuts**: add a deep link (e.g. a Trello board) that opens straight
  in its app — long-press the face → *Add URL shortcut*.
- Long-press → full-screen picker that's also a mini-launcher:
  - check apps / shortcuts to show them in the ring (up to 8, sized for a
    comfortable tap target);
  - a launch button on every row to open it directly without adding it;
  - keep shortcuts but toggle them off, or delete them.
- Auto-hides while the Skylight photo screensaver or the picker is showing.

## Quick start

```bash
./scripts/dev-install.sh
```

Builds, installs to the connected device (USB or Wi-Fi), and launches it.

## Contributing / working on this

See [AGENTS.md](AGENTS.md) for setup details, device specifics, the dev
workflow, and gotchas (it's also the context file AI coding agents read).
