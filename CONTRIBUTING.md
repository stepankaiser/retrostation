# Contributing to RetroStation

Thanks for your interest in contributing! RetroStation is intentionally small and hackable — here's how to get involved.

## Quick Start

1. Fork and clone the repo
2. Set up the [Android SDK prerequisites](README.md#prerequisites)
3. Run `./build.sh install` to build and deploy to a connected device
4. Make your changes, rebuild, test

## What to Work On

- **UI tweaks** — Edit `LauncherHTML.java` to change the interface (it's all HTML/CSS/JS)
- **New systems** — Add entries to `CoreMap.java` and update `ConsoleBridge.java`
- **Cover art styles** — Add new pattern types in `generate_covers.py`
- **Bug fixes** — File an issue first so we can discuss the approach

## Code Style

- Keep it simple — this project compiles with raw `javac`, no frameworks
- Java source targets Java 11 compatibility
- The UI lives in a single HTML string — keep it self-contained (no external dependencies)
- Test on a real device when possible

## Submitting Changes

1. Create a branch for your feature or fix
2. Keep commits focused and descriptive
3. Open a PR with a clear description of what changed and why
4. Include a screenshot if you changed the UI

## Reporting Issues

Open a GitHub issue with:
- Device model and Android version
- Steps to reproduce
- Screenshots or logcat output if applicable

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
