# Termux File Manager (APK)

An Android APK file manager and editor designed to work with Termux.

## Features
- Browse files
- Create / rename / delete files & folders
- Built-in editor (monospace)
- Non-root mode uses `/sdcard/TermuxProjects`
- Rooted devices can use SAF to access Termux folders
- GitHub Actions builds APK automatically

## Build
APK is built automatically via GitHub Actions.

Go to:
- Actions → Build APK → Artifacts → download APK

## Install
1. Enable "Install unknown apps"
2. Install the APK
3. On first run:
   - Non-root: workspace is created automatically
   - Root: pick Termux folder (optional)

## Roadmap
- Termux script runner
- Syntax highlighting
- Integrated terminal view
- Plugin system
