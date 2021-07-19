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
package org.jetbrains.projector.server.idea

import com.intellij.openapi.editor.markup.TextAttributes

internal data class ExtendedTextAttributes(val attrs: TextAttributes, val range: IntRange, val priority: Int) {

  companion object {

    /**
     * Returns attributes that are placed on top of others if we imagine TextAttributes ranges as a stack
     */
    internal fun topLayeredAttributes(attrs: ExtendedTextAttributes?, otherAttrs: ExtendedTextAttributes?): ExtendedTextAttributes? {
      if (attrs == null) return otherAttrs
      if (otherAttrs == null) return attrs

      if ((otherAttrs.range.first < attrs.range.first && otherAttrs.range.last < attrs.range.last && otherAttrs.range.last < attrs.range.first)
          || (otherAttrs.range.first > attrs.range.first && otherAttrs.range.last > attrs.range.last && otherAttrs.range.first > attrs.range.last)) {

        throw IllegalArgumentException("Cannot compare ${attrs.range} and ${otherAttrs.range} (${attrs.priority} vs ${otherAttrs.priority})")
      }

      if (attrs.priority == otherAttrs.priority) {
        if ((otherAttrs.range.first >= attrs.range.first && otherAttrs.range.last < attrs.range.last)
            || (otherAttrs.range.first > attrs.range.first && otherAttrs.range.last <= attrs.range.last)) return otherAttrs
      }

      // fallback to priority if we have same ranges or when ranges partly overlap
      return if (otherAttrs.priority > attrs.priority) otherAttrs else attrs
    }
  }
}
