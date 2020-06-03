/*
 * GNU General Public License version 2
 *
 * Copyright (C) 2019-2020 JetBrains s.r.o.
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
package org.jetbrains.projector.server.protocol

import org.jetbrains.projector.common.protocol.compress.MessageCompressor
import org.jetbrains.projector.common.protocol.compress.MessageDecompressor
import org.jetbrains.projector.common.protocol.compress.NotCompressor
import org.jetbrains.projector.common.protocol.compress.NotDecompressor
import org.jetbrains.projector.common.protocol.handshake.CompressionType
import org.jetbrains.projector.common.protocol.handshake.ProtocolType
import org.jetbrains.projector.common.protocol.toClient.ToClientMessageEncoder
import org.jetbrains.projector.common.protocol.toClient.ToClientTransferableType
import org.jetbrains.projector.common.protocol.toServer.ToServerMessageDecoder
import org.jetbrains.projector.common.protocol.toServer.ToServerTransferableType

object HandshakeTypesSelector {

  fun selectToClientCompressor(supportedToClientCompressions: List<CompressionType>): MessageCompressor<ToClientTransferableType>? {
    fun CompressionType.toToClientCompressor(): MessageCompressor<ToClientTransferableType>? = when (this) {
      CompressionType.GZIP -> GZipMessageCompressor

      CompressionType.NONE -> NotCompressor()
    }

    return supportedToClientCompressions.mapNotNull(CompressionType::toToClientCompressor).firstOrNull()
  }

  fun selectToClientEncoder(supportedToClientProtocols: List<ProtocolType>): ToClientMessageEncoder? {
    fun ProtocolType.toToClientEncoder(): ToClientMessageEncoder? = when (this) {
      ProtocolType.KOTLINX_JSON -> KotlinxJsonToClientMessageEncoder
      ProtocolType.KOTLINX_PROTOBUF -> KotlinxProtoBufToClientMessageEncoder
    }

    return supportedToClientProtocols.mapNotNull(ProtocolType::toToClientEncoder).firstOrNull()
  }

  fun selectToServerDecompressor(supportedToServerCompressions: List<CompressionType>): MessageDecompressor<ToServerTransferableType>? {
    fun CompressionType.toToServerDecompressor(): MessageDecompressor<ToServerTransferableType>? = when (this) {
      CompressionType.NONE -> NotDecompressor()

      else -> null
    }

    return supportedToServerCompressions.mapNotNull(CompressionType::toToServerDecompressor).firstOrNull()
  }

  fun selectToServerDecoder(supportedToServerProtocols: List<ProtocolType>): ToServerMessageDecoder? {
    fun ProtocolType.toToServerDecoder(): ToServerMessageDecoder? = when (this) {
      ProtocolType.KOTLINX_JSON -> KotlinxJsonToServerMessageDecoder
      ProtocolType.KOTLINX_PROTOBUF -> KotlinxJsonToServerMessageDecoder
    }

    return supportedToServerProtocols.mapNotNull(ProtocolType::toToServerDecoder).firstOrNull()
  }
}
