/*
 * GNU General Public License version 2
 *
 * Copyright (C) 2019-2021 JetBrains s.r.o.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jetbrains.projector.server

import org.jetbrains.projector.common.protocol.compress.MessageCompressor
import org.jetbrains.projector.common.protocol.compress.MessageDecompressor
import org.jetbrains.projector.common.protocol.toClient.ServerEvent
import org.jetbrains.projector.common.protocol.toClient.ToClientMessageEncoder
import org.jetbrains.projector.common.protocol.toClient.ToClientTransferableType
import org.jetbrains.projector.common.protocol.toServer.ToServerMessageDecoder
import org.jetbrains.projector.common.protocol.toServer.ToServerTransferableType
import org.jetbrains.projector.server.util.SizeAware
import org.jetbrains.projector.util.logging.Logger
import java.util.concurrent.ConcurrentLinkedQueue

sealed class ClientSettings {

  abstract val connectionMillis: Long
}

data class ConnectedClientSettings(override val connectionMillis: Long) : ClientSettings()

data class SetUpClientData(
  val hasWriteAccess: Boolean,
  val toClientMessageEncoder: ToClientMessageEncoder,
  val toClientMessageCompressor: MessageCompressor<ToClientTransferableType>,
  val toServerMessageDecoder: ToServerMessageDecoder,
  val toServerMessageDecompressor: MessageDecompressor<ToServerTransferableType>,
)

data class SetUpClientSettings(
  override val connectionMillis: Long,
  val setUpClientData: SetUpClientData,
) : ClientSettings()

data class ReadyClientSettings(
  override val connectionMillis: Long,
  val setUpClientData: SetUpClientData,
) : ClientSettings() {

  var touchState: TouchState = TouchState.Released

  val requestedData by SizeAware(ConcurrentLinkedQueue<ServerEvent>(), logger)

  companion object {

    private val logger = Logger<ReadyClientSettings>()
  }

  sealed class TouchState {

    object Released : TouchState()

    data class OnlyPressed(val connectionMillis: Int, override val lastX: Int, override val lastY: Int) : TouchState(), WithCoordinates

    object Dragging : TouchState()

    data class Scrolling(
      val initialX: Int, val initialY: Int,
      override val lastX: Int, override val lastY: Int,
    ) : TouchState(), WithCoordinates

    interface WithCoordinates {

      val lastX: Int
      val lastY: Int
    }
  }
}
