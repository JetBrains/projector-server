# 1.7.0

## Added
- PRJ-127 To-client clipboard synchronization without asynchronous clipboard API for Web client

## Fixed
- PRJ-691 PRJ-750 PRJ-779 Fix JetBrains account login
- Infinite transport initialization on relay failure
- (partial) PRJ-552 Fix transparent pop-ups in some cases
- (full) PRJ-62 PRJ-552 Fix incorrect window size in some cases
- PRJ-565 Fix wrong header position after the window is resized

## Changed
- Switch to JS IR compilation for projector-client-web
- Server startup logic - now the server does not require successful initialization of all transports and starts if at least one transport is successfully initialized

# 1.6.0

## Added

- PRJ-20 Synchronization between drawing to different surfaces
- PRJ-796 Support for displaying markdown preview with plugin version 213

## Fixed

- Support mono fonts without "mono" in their names (useful when building Projector with fonts other than JB Mono)
- PRJ-443 Remove "Allow popups for this site" alert when allowance is not needed
- PRJ-60 Not pixel perfect popups position

## Changed

- Remove green decoration of the web browser
- PRJ-199 Support native double buffering

# 1.5.0

## Added
- Server forbids Idea platform updates and plugins updates notifications. 
  This behaviour can be changed via ORG_JETBRAINS_PROJECTOR_SERVER_DISABLE_IDEA_UPDATES property.
- PRJ-157 Support WINDOW_ACTIVATED and WINDOW_DEACTIVATED events which are required for file sync in IDE
- PRJ-226 PRJ-530 Support for displaying markdown preview with plugin versions 203-212
- Support for displaying markdown preview in headless mode when JavaFX and Jcef are not available
- Composition symbols are now styled as real symbols before them
- PRJ-332 Disable IDE restarts. 

## Changed
- Update JetBrains Mono to v2.242

## Fixed
- PRJ-684 Exception when opening markdown files
- PRJ-679 Fix "Read access is allowed from inside read-action" when getting Editor of diagram's view
- PRJ-663 Fix broken graph rendering in IDE versions 2021.1 and newer

# 1.4.0

## Fixed

- PRJ-615 Fix NRE when creating font events
- Fix paste keystroke key event order
- Fix "bad cursor ID -1" warning
- PRJ-660 PRJ-510 Fix inability to close windows opened before connection
- PRJ-189 Fix correct content paste only after the second attempt
- PRJ-696 Fix IME textarea refocusing in Firefox when the whole window was blurred and then focused
- Remove logs of old mouse events creation (for already not showing windows)
- PRJ-214 Change window ID generation to sequential (prevent potential errors, e.g. sometimes windows open without decoration/header)
- PRJ-547 Fix agent failed to start

## Changed

- Speculative typing improvements
- Projector classloader improvements (used in agent-v1.6.1)
- Change ImageCacher from a singleton to an instantiated class (prevent potential errors)
- Refactor injector transformers

## Added

- Allow to specify external transports and to disable WebSocket server (useful when embedding Projector to other apps)

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
