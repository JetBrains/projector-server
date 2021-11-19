# projector-plugin changelog

Notable changes to this project are documented in this file.

# 1.9.0

## Added
- HTTPs support

## Updated
- PRJ-717 Support for platform 193 dropped
- Projector Server v1.6.0

## Fixed
- PRJ-770 Update GotIt message position if widget is moved

# 1.8.0

## Fixed

- PRJ-744 Get rid of "java.lang.IllegalStateException: cannot open system clipboard" logging

## Changed

- Big refactoring of agent internal logic to make it closer to Projector AWT

## Updated

- Projector Server v1.5.0.

# 1.7.0

## Fixed

- Null action workaround in fireAction.

## Added

- PRJ-355 PRJ-553 Support copy/paste on client when the server is started in agent mode.

## Updated

- Projector Server v1.4.0.

# 1.6.1

## Fixed

- PRJ-658 Fix Projector plugin startup in IDEA 2021.2.

# 1.6.0

## Changed

- Increase max row count in Hosts control.

## Fixed

- Fix possible leak when the server is in manually stopped state.
- PRJ-314 Projector sessions dialog rendering takes uncomfortably long time to appear.
- PRJ-122 Hide menu on plugin uninstall.

## Updated

- Projector Server v1.3.0.

# 1.5.1

## Updated

- Projector Server v1.2.1.

# 1.5.0

## Changed

- PRJ-172 Projector menu is now moved to the bottom of the screen to widgets, showing status icon and connected client count.
- PJR-524 Some refactoring movements.

## Added

- PRJ-204 Allow stopping Projector server without restarts.

## Updated

- Projector Server v1.2.0.

# 1.4.2

## Updated

- Projector Server v1.1.6.

# 1.4.1

## Changed

- Various UI changes.

# Updated

- Projector Server v1.1.5.

# 1.4.0

## Changed

- Store tokens in secure storage.
- Remove existing tokens from secure storage.
- Simplify GUI.

# Updated

- Projector Server v1.1.4.

# 1.3.0

## Added

- PRJ-402 Save connection parameters between launches.
- PRJ-402 Support Projector server auto start.

## Changed

- PRJ-402 Random passwords are now generated manually by a special button.

# Updated

- Projector Server v1.1.3.

# 1.2.3

## Fixed

- PRJ-430 Fix headless projector detection.

# 1.2.2

## Added

- Allow disabling connection confirmation.

## Fixed

- PRJ-388 Switch off EnableAction if Projector detected.
- PRJ-314 Get rid of blocking calls getHostName.

## Changed

- Minor UI improvements.

# Updated

- Projector Server v1.1.2.

# 1.2.1

## Added

- Loopback address selection.

## Changed

- Minor UI improvements.

# Updated

- Projector Server v1.1.1.

# 1.2.0

## Added

- Allow specifying the listening address.

# Updated

- Projector Server v1.1.0.

# 1.1.0

## Fixed

- IntelliJ's compatibility deprecation warnings.

# Added

- PRJ-153 Show connected users.
- Allow disconnecting users.

# 1.0.4

## Changed

- Icons.

# Updated

- Projector Server v1.0.0.

# 1.0.3

## Fixed

- PRJ-290 Failing start of remote access.

# Updated

- Projector Server v0.51.15.

# 1.0.2

## Added

- Description, icon, and change notes.

# 0.43.1

## Added

- Use DNS names instead of IPs where possible.

## Changed

- Passwords settings.
- Dialogs look and copying URL.

## Fixed
- Compatibility with 2020.3.
