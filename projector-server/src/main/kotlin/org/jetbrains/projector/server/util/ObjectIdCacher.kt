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
package org.jetbrains.projector.server.util

class ObjectIdCacher<IdType : Number, ObjectType : Any>(initialId: IdType, private val incrementer: (IdType) -> IdType) {

  private var nextId = initialId

  private val idToObject = mutableMapOf<IdType, ObjectType>()
  private val objectToId = mutableMapOf<ObjectType, IdType>()

  fun getIdBy(obj: ObjectType): IdType {
    val id = objectToId[obj]

    if (id != null) {
      return id
    }

    val newId = nextId
    nextId = incrementer(nextId)

    idToObject[newId] = obj
    objectToId[obj] = newId

    return newId
  }

  fun getObjectBy(id: IdType): ObjectType = requireNotNull(idToObject[id]) { "No id $id found. Available ids: $idToObject" }
}
