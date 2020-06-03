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
package org.jetbrains.projector.server.util

import com.nhaarman.mockitokotlin2.*
import org.jetbrains.projector.server.ProjectorServer
import org.jetbrains.projector.server.log.Logger
import kotlin.test.Test

class SizeAwareTest {

  @Test
  fun loggerErrorShouldBeCalled() {
    val logger = mock<Logger>()

    doNothing().whenever(logger).error(anyOrNull(), any())

    val checkedSize = 100
    val targetSize = 200

    ProjectorServer.ENABLE_BIG_COLLECTIONS_CHECKS = true
    ProjectorServer.BIG_COLLECTIONS_CHECKS_START_SIZE = checkedSize

    val template = TemplateClass(logger)

    repeat(targetSize) {
      template.list.add(it)
    }

    verify(logger, times(targetSize - checkedSize)).error(anyOrNull(), any())
  }

  class TemplateClass(logger: Logger) {

    val list by SizeAware(mutableListOf<Int>(), logger)
  }
}
