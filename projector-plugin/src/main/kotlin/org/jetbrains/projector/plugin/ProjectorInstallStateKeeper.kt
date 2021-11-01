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
package org.jetbrains.projector.plugin

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ProjectorInstallStateKeeper {
  val isFirstRun = checkFirstRun()

  private fun checkFirstRun(): Boolean {
    val isFirstRun = getFirstRunMark() == null
    if (isFirstRun) {
      setFirstRunMark()
    }

    return isFirstRun
  }

  private fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

  fun sinceLastHello(): Duration {
    return Duration.between(getLastHelloTime(), nowUtc())
  }

  fun removeFirstRunMark() = unsetProperty(FIRST_RUN_MARK)

  private fun getFirstRunMark(): String? = getProperty(FIRST_RUN_MARK)

  private fun setFirstRunMark() = setProperty(FIRST_RUN_MARK, nowUtc().toString())

  fun setLastHelloTime() = setProperty(LAST_HELLO_TIME, nowUtc().toString())

  private fun getLastHelloTime(): OffsetDateTime {
    val lastHelloMark = getProperty(LAST_HELLO_TIME) ?: return OffsetDateTime.MIN
    return OffsetDateTime.parse(lastHelloMark)
  }

  private fun getProperty(name: String): String? = PropertiesComponent.getInstance().getValue("${PREFIX}.$name")

  private fun setProperty(name: String, value: String) {
    PropertiesComponent.getInstance().setValue("${PREFIX}.$name", value)
  }

  private fun unsetProperty(name: String) = PropertiesComponent.getInstance().unsetValue("${PREFIX}.$name")

  companion object {
    val FULL_DAY: Duration = Duration.ofDays(1)
    private const val PREFIX = "projector"
    private const val FIRST_RUN_MARK = "first.run.mark"
    private const val LAST_HELLO_TIME = "last.hello.time"
    fun getInstance(): ProjectorInstallStateKeeper = service()
  }
}
