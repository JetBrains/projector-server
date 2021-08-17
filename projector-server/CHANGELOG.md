# 1.3.0

## Fixed

- Speculative typing improvements
- Get rid of Markdown plugin requirement in IDE to make IDE hooks work without MD plugin installed
- PRJ-510 Fix caret position for IME, add color, adjust caret visibility
- Move IME overlay from the bottom to the top (when no info about caret is found)
- PRJ-625 Fix IME stopped working in Firefox after some mouse actions
- PRJ-614 Fix Numpad Enter
- PRJ-194 Support Option+key on Mac clients
- PRJ-597 Check editor component is showing before getting location on screen
- PRJ-613 Support PGraphics2D.drawRenderedImage image and null transformation parameter

## Changed

- Kotlin 1.5.20 and other dependencies updates
- Classloader to load Projector and app classes

# 1.2.1

## Fixed

- Support set up the server using the old properties

# 1.2.0

## Added

- PRJ-35 Support version probing in the handshake
- PRJ-543 Specify the reason for disconnection
- Symbol glyphs
- PRJ-373 PRJ-551 Connection via relay
- PRJ-330 PRJ-354 PRJ-514 Add input method compatible with IME and Dead Keys and make it default
- PRJ-413 Support web client inside iframe
- Support opening links to localhost
- PRJ-241 PRJ-386 Support automatic URL path usage and its manual specification, support running behind a reverse proxy
- PRJ-541 PRJ-542 Show CloseEvent details on disconnection

## Fixed

- PRJ-513 Fix parsing html representation of markdown documents
- Prevent "overscrolling" of web page for macos
- PRJ-528 Fix redundant click sent for IME and mobile input methods
- PRJ-544 Support connection end during handshake

## Changed

- Env vars names for server set up are more consistent now
- PRJ-61 Internal HiDPI support (not implemented in web client yet)
- Async rendering algorithm is now more performant
- Protocol is changed, so this release introduces incompatible changes

# 1.1.6

## Changed

- Performance: Make window redrawing rare if it takes long time

## Fixed

- Minor logging improvements

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
