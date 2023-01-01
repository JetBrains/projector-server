/*
 * Copyright (c) 2019-2023, JetBrains s.r.o. and/or its affiliates. All rights reserved.
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

public val openAndExportJvmArgs: List<String> = listOf(
  "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.text=ALL-UNNAMED",
  "--add-opens=java.base/java.time=ALL-UNNAMED",
  "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
  "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
  "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
  "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED",
  "--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED",
  "--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED",
  "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED",
  "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
  "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED",
)
