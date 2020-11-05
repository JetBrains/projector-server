# Projector
This document describes the ecosystem as a whole.

## TL;DR
Projector is a way to run Swing applications without any windowing system and accessing them either locally or remotely.

If you seek for clear info of how this can be practically used, we advise you to read [README-JETBRAINS.md](../README-JETBRAINS.md) about accessing remotely run JetBrains IDEs.

## The system
Here you can find some info on how this works.

### AWT implementation
The Foundation stone of Projector is an implementation of AWT. It allows running Swing applications headlessly but without `HeadlessException`s. Also, it intercepts some AWT calls and remembers them. The most calls are invocations of `Graphics2D` methods. This module is called `projector-awt`.

### Server and client
To run an application with our AWT and access it, we provide a server and a client.

The module of the server implementation is called `projector-server`. It provides a launcher that sets AWT system properties and fields and is compatible with the most of Swing apps.

Also, it extracts remembered AWT calls from `projector-awt` and can send them on a client. This implementation uses WebSocket and a serialization protocol which is located in the `projector-common` module. A client can execute the received calls on its side. The only client implemented now is for Web browsers, it's available in the `projector-client-web` module.

Finally, the client can send its events such as mouse clicks or window resizes in the reverse direction. `projector-server` translates these events to AWT.

### Client components
Some Swing apps contain content that can't be easily represented as `Graphics2D` method calls. For example, it can be an embedded heavyweight component. In such cases, it's sometimes possible to serialize this content another way.

The only client component implemented now is Markdown Preview in JetBrains IDEs.

## Repositories
Here we enumerate repos related to this project and describe their content.

### Main
* [projector-client](https://github.com/JetBrains/projector-client):
    * `projector-common` &mdash; protocol and other common code for clients and a server.
    * `projector-client-common` &mdash; common code for clients.
    * `projector-client-web` &mdash; a client implementation for Web browsers.
* [projector-server](https://github.com/JetBrains/projector-server):
    * `projector-awt` &mdash; AWT implementation.
    * `projector-server` &mdash; an application server for running and remote accessing a Swing app.

### Examples and utils
* [projector-demo](https://github.com/JetBrains/projector-demo) &mdash; a sample app showing how to run any Swing app with Projector.
* [projector-docker](https://github.com/JetBrains/projector-docker) &mdash; scripts for building Docker images containing JetBrains IDEs, Projector Server, and a web server hosting Web Client inside.
* [projector-installer](https://github.com/JetBrains/projector-installer) &mdash; a utility for installing, configuring, and running JetBrains IDEs with `projector-server` on Linux or in WSL, available at PyPI.

### Obsolete
* [projector-markdown-plugin](https://github.com/JetBrains/projector-markdown-plugin) &mdash; Markdown Preview for JetBrains IDEs which can be used with Projector. Now we managed to bundle it to projector-server, so you don't need to install it separately anymore.

## How to use
You can find some hints in the corresponding repos.

You can use [JitPack](https://jitpack.io/) to make a dependency on our project. Also, you can clone a project and build it yourself.

We show these ways in example repos.

## Use cases
We believe there are many cases when you may want to use Projector. Let's enumerate some of them to inspire you:
* Not running a Swing IDE on a high-end laptop, but running it on a server and accessing it via a **thin client**:
    * Usually, **high-end laptops cost more** than a server with comparable performance and a thin client.
    * A thin client not only has a relatively small price but also it **doesn't contain any valuable data**, so a case of its loss is not a big deal.
    * Thin clients are **more mobile**: you aren't restricted to use only x86, you can select ARM and devices that are cooler, more compact, and have longer battery life, for example, you can use iPad and a keyboard instead of MacBook.
* Not **remote debugging** in a local Swing IDE, but accessing a remote Swing IDE doing local debugging.
* Not **coding in nano or vim over ssh** but copying a Swing IDE and Projector there and access it.
* Run your Swing IDE in a **Linux environment but on Windows**: you can simply run it via Projector Server in WSL and use a client in Windows. This is easier than other methods such as installing X Server to WSL or using a visual virtual machine.
* Do **pair programming** sessions remotely: multiple clients can simultaneously connect to the same Swing IDE and interact with it in rotation.
* You can **turn your device off** while your app continues work on a server. For example, if your project is so huge that it takes a night to compile it fully, you can leave this task for a server and take your device with you without risk of overheating in your bag.

## Comparison to other remote access solutions
Here we formulate the pros of Projector:
* Since our goal is to support only AWT apps, Projector is **more compact** than more general remote access software like RDP or VNC. You don't even have to install special apps anywhere: on a server-side, you need only JRE, but there is one because your app is a Java app; on a client-side, you need only a Web browser, and you have it on almost any device.
* Since Projector Server knows about **AWT components**, some of them can be serialized in a special way to be shown on a client **natively**. The example is the Markdown Preview of JetBrains IDEs.
* Projector supports **simultaneous client connections** to the same application instance.
