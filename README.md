# Ciel Launcher

A floating "chathead" overlay for a Skylight calendar device. A draggable
always-on-top face sits over whatever is on screen; tap it for a radial menu
with **Back**, **Home** (back to the Skylight calendar), and a set of apps you
choose. **Long-press** the face to pick which apps appear. The bubble gets out
of the way on its own while the photo screensaver is up.

## Features

- Always-on-top draggable bubble, starts on boot.
- Radial quick-launch menu that adapts its layout to the number of apps.
- Long-press → full-screen app picker (choose up to 8 apps; choices are saved).
- A **Back** button and a **Home** shortcut to the Skylight calendar.
- Auto-hides during the Skylight photo screensaver and the app picker.

## Quick start

```bash
./scripts/dev-install.sh
```

Builds, installs to the connected device (USB or Wi-Fi), and launches it.

## Contributing / working on this

See [AGENTS.md](AGENTS.md) for setup details, device specifics, the dev
workflow, and gotchas (it's also the context file AI coding agents read).
