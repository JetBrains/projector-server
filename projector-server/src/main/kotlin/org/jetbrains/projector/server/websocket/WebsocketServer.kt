/*
 * Copyright (c) 2019-2021, JetBrains s.r.o. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. JetBrains designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact JetBrains, Na Hrebenech II 1718/10, Prague, 14000, Czech Republic
 * if you need additional information or have any questions.
 */
package org.jetbrains.projector.server.websocket

import org.jetbrains.projector.common.protocol.data.ImageData
import org.jetbrains.projector.common.protocol.data.ImageId
import org.jetbrains.projector.common.protocol.toClient.MainWindow
import org.jetbrains.projector.server.ProjectorServer
import org.jetbrains.projector.server.core.util.getProperty
import org.jetbrains.projector.server.core.websocket.HttpWsClientBuilder
import org.jetbrains.projector.server.core.websocket.HttpWsServerBuilder
import org.jetbrains.projector.server.core.websocket.WsTransportBuilder
import org.jetbrains.projector.server.service.ProjectorImageCacher
import org.jetbrains.projector.util.logging.Logger

object WebsocketServer {
  internal fun createTransportBuilders(): List<WsTransportBuilder> {
    val builders = arrayListOf<WsTransportBuilder>()

    val relayUrl = getProperty(RELAY_PROPERTY_NAME)
    val serverId = getProperty(SERVER_ID_PROPERTY_NAME)

    if (relayUrl != null && serverId != null) {
      val scheme = when (getProperty(RELAY_USE_WSS)?.toBoolean() ?: true) {
        false -> "ws"
        true -> "wss"
      }

      logger.info { "${ProjectorServer::class.simpleName} connecting to relay $relayUrl with serverId $serverId" }
      builders.add(HttpWsClientBuilder("$scheme://$relayUrl", serverId))
    }

    val host = ProjectorServer.getEnvHost()
    val port = ProjectorServer.getEnvPort()
    logger.info { "${ProjectorServer::class.simpleName} is starting on host $host and port $port" }

    val serverBuilder = HttpWsServerBuilder(host, port)
    serverBuilder.getMainWindows = {
      ProjectorServer.getMainWindows().map {
        MainWindow(
          title = it.title,
          pngBase64Icon = it.icons
            ?.firstOrNull()
            ?.let { imageId -> ProjectorImageCacher.getImage(imageId as ImageId) as? ImageData.PngBase64 }
            ?.pngBase64,
        )
      }
    }

    builders.add(serverBuilder)
    return builders
  }

  private val logger = Logger<WebsocketServer>()

  private const val RELAY_PROPERTY_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_RELAY_URL"
  private const val SERVER_ID_PROPERTY_NAME = "ORG_JETBRAINS_PROJECTOR_SERVER_RELAY_SERVER_ID"
  private const val RELAY_USE_WSS = "ORG_JETBRAINS_PROJECTOR_SERVER_RELAY_USE_WSS"
}
