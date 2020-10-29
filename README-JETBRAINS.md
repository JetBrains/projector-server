# The Projector for JetBrains IDE

The Projector is a technology for rendering IDE GUI over the network. It should be compatible with all existing JetBrains IDEs.


## Use cases

1. Running the code from IDE located near the runtime (whatever it is) or near the database to reduce roundtrips;
2. High-security zones and corporate environments;
3. Really huge projects;
4. No source code on a laptop;
5. Slow or hot users’ laptops;
6. Thin clients and cheap hardware like Android tablets;
7. Running the IDE in a GNU/Linux environment on Windows machines or even on exotic operating systems like ChromeOS;
8. The ability to turn off your computer, while your app continues to work on the server;
9. Slow internet connection;
10. Remote debugging on a server-side (devtest, devprod);
11. VM or Docker images with debug sources and pre-configured IDE.
12. Any configuration that requires remote access.

![](https://hsto.org/webt/bn/rf/rk/bnrfrkzogrdfp5sxs6t5g7hllc4.jpeg)

*IntelliJ IDEA on Android tablet (Huawei MediaPad M5)*


## The state of the Projector

Currently, it is in a private preview stage. Please evaluate it and send your feedback to the Projector team. Your feedback is vital for understanding the Projector's future and prototyping its new features.

You can report any issues here:[ https://youtrack.jetbrains.com/issues/PRJ](https://youtrack.jetbrains.com/issues/PRJ)  \
Feel free to report any bug or submit a feature request:  every single opinion is important.


## Where to start

The easiest way to try the project is to use Docker Images. Here's the repo:[ https://github.com/JetBrains/projector-docker](https://github.com/JetBrains/projector-docker)

If you can't use Docker for some reasons (for example, due to any security limitations), try this installation script:[ https://github.com/JetBrains/projector-installer](https://github.com/JetBrains/projector-installer)

If you can't even use this installation script, you need to dive deeper and understand how it works under the hood. You can check README here:[ https://github.com/JetBrains/projector-server/](https://github.com/JetBrains/projector-server/)

As for the client-side, just open the URL (something like [https://127.0.0.1:8887](https://127.0.0.1:8887)) in the browser, and that’s it.


## Client-side

Currently, you can use a web browser to connect to the IDE. The experience is very similar to using any interactive website. You can use a fullscreen mode in your browser to achieve an almost desktop-like experience.

If the technology preview is considered successful, and if there is a demand from users, in the next stages we may build separate native applications.


## Server-side

You can use the Projector as a set of Docker images, or as a standalone distribution. A standalone distribution is currently available for GNU/Linux hosts only.

Dockerfiles are public and Open Source, so you can verify them with your security team.


## VPN and SSH tunnels

The Projector should run on popular VPN solutions like OpenVPN. It uses the HTTP and WebSocket protocols to connect to the browser. You shouldn't have any problems with that.

Also, you can use the following SSH command to redirect these ports through a plain SSH tunnel :

```
ssh -i key.pem -L 8887:127.0.0.1:8887 user@server
```

You can try this on the Amazon cloud.


## Competitors

The сlosest competitors to the Projector are X Window System, VNC, and RDP.

One the one hand, the Projector is much more responsive. Unlike all these alternatives, it uses extended and precise information about applications written in Java (which all JetBrains IDEs are) and sends it over the network. For example, that allows rendering crisp, pixel-perfect vector fonts. And it doesn't matter if your connection is slow or not, your text always is perfect, because the fonts are all in a vector format.

On the other hand, X11, VNC, and RDP are well-known solutions, and system administrators know exactly how to run them in a corporate environment.


## Is it Open Source?

Now, everything is an Open Source or Free Software:

*   Dockerfiles (Apache License 2.0):[ https://github.com/JetBrains/projector-docker](https://github.com/JetBrains/projector-docker)
*   Server (GNU GPL v2.0):[ https://github.com/JetBrains/projector-server.git](https://github.com/JetBrains/projector-server.git)
*   Client (MIT License):[ https://github.com/JetBrains/projector-client.git](https://github.com/JetBrains/projector-client.git)
*   Markdown Plugin (MIT License): [https://github.com/JetBrains/projector-markdown-plugin.git](https://github.com/JetBrains/projector-markdown-plugin.git)


## Known problems

The server side is supported as a Docker Image and as a local distribution for Linux servers. Local installations of the server on Windows and Mac are not a subject for the preview stage of the project. However, technically it is quite possible.

We still have some inconsistencies in shortcuts. For example, a known problem is that we cannot use Ctrl+W to close a source file, because it closes a tab in the browser. The only action Projector can do here is to ask the user if they really want to close that tab. In the future, we can create separate native apps instead of the browser client to fix this.

There's a known limitation on using plugins with a custom rendering provided by Chromium Embedded Framework because it bypasses the standard rendering pipeline in Java. It's a hard problem, probably we can fix this, but not at an early preview stage. Now we have a smart workaround for the Markdown component: we render Markdown HTML locally on a client. Actually, it even improved the experience of writing markdown a lot.

We cannot render separate external applications. For example, if you run the Android Emulator by activating a Run/Debug configuration, and expect to see GUI, it's impossible to render it with the Projector. In the future, it may be solved by combining with VNC technologies, but it's definitely not a target of the preview stage.


## What's new

The following improvements have been added recently:

*   An improved protocol:
    *   Before this change, parsing the protocol took four times longer than rendering. After the change it should be ten times faster;
    *   That's all about the client-side. This change does nothing with the data transfer part of the pipeline;
*   Better graphics performance:
    *   Improved bitmap image deduplication significantly reduced the network load;
    *   Image caching algorithms and two-pass rendering eliminated freezes on a client-side;
*   Better support on mobile devices:
    *   On-screen keyboard button;
    *   Functional keys;
    *   Touch interfaces — we're testing it right now.
*   Multiple projects inside a single IDE:
    *   A separate URL for each project;
    *   API for managing tabs may come later;
*   A command-line interface for quick installation:
    *   Using a single command to start the server and output the URL for connection;
    *   You can pick an IDE from a pre-populated list or specify the location on your local storage;
*   Updated scripts for the modern IntelliJ IDEA versions.