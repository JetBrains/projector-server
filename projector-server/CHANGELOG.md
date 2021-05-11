# 1.1.5

## Fixed

- PRJ-474 Fix italic fonts are shown as plain in editor

# 1.1.4

## Changed

- PRJ-453 Decrease log level of missing caret info
- PRJ-437 Decrease log level of missing MD preview
- PRJ-130 Allow selecting MacOS keymap for 203+ on Linux
- Switch Ubuntu font to Noto

## Added

- PRJ-369 Support full-shape CJK characters rendering

# 1.1.3

## Fixed

- Mobile keyboard now handles more keys more correctly
- Optimize async render more
- Optimize argbIntToRgbaString
- Apple Web Applications support
- PRJ-286 Fix Esc and Delete keys

## Changed

- PRJ-435 Merge AWT and server changelogs to make changelogs simpler

# 1.1.2

## Added

- PRJ-188 Support links opening via Cmd+Click in code, "Open on GitHub", Markdown Preview

# 1.1.1

## Fixed

- PRJ-345 Broken shortcuts

# 1.1.0

## Added

- PRJ-272 Allow specifying PIXEL_PER_UNIT.
- PRJ-287 PRJ-234 Disable ligatures automatically.

## Changed

- PRJ-275 Implement taking keymap from keyboard API.
- PRJ-317 Improve disconnection message.

## Fixed

- Manual JSON decoder.
- PRJ-315 Disable maximization of window when resizing.

# 1.0.0

## Changed

- PRJ-255 Change default port to 80 or 443 depending on window.location.protocol.
- Icons.

# 0.51.15

## Fixed

- PRJ-274 Fix not working backgroundColor parameter.
- PRJ-194 Make AltRight recognize as Alt Graph not simple Alt.
- PRJ-25 Ligatures are shown even when disabled.
- Fix compatibility with JetBrains Runtime bundled with 2021.1 EAP.
- Fix RMB doesn't work in Terminal.
- Fix context menu blinking because of showing twice.
- PRJ-226 Support MD in IDEs of version 203.

## Changed

- PRJ-274 Make default background color less bright.
- PRJ-274 Make background of windows default and not solid.

## Added

- PRJ-229 Allow to choose AZERTY keymap.
- PRJ-235 Add option to bind to specific host address.

# 0.50.14

## Fixed

- PRJ-246 Fix manual JSON decoder doesn't handle default empty lists.
- PRJ-212 Fix reconnection failure.
- PRJ-101 and PRJ-218: bounds of graphics configuration have to match what's used for events.

# 0.49.13

## Changed

- Logging.

## Fixed

- Focus disappearing after closing dialogs (partial fix).
- Detection of server start fail.
