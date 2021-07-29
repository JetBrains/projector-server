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
package org.jetbrains.projector.agent

import javassist.ByteArrayClassPath
import javassist.ClassPool
import javassist.CtClass
import javassist.bytecode.AccessFlag
import org.jetbrains.projector.util.logging.Logger
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.IllegalClassFormatException
import java.security.ProtectionDomain


internal class GraphicsTransformer : ClassFileTransformer {
  @Throws(IllegalClassFormatException::class)
  override fun transform(
    loader: ClassLoader?,
    className: String,
    classBeingRedefined: Class<*>?,
    protectionDomain: ProtectionDomain?,
    classfileBuffer: ByteArray,
  ): ByteArray? {
    return transformClass(className, classfileBuffer)
  }

  // For Java >= 9
  @Throws(IllegalClassFormatException::class)
  override fun transform(
    module: Module?,
    loader: ClassLoader?,
    className: String,
    classBeingRedefined: Class<*>?,
    protectionDomain: ProtectionDomain?,
    classfileBuffer: ByteArray,
  ): ByteArray? {
    return transformClass(className, classfileBuffer)
  }

  init {
    if (System.getProperty("awt.nativeDoubleBuffering") == "true") {
      logger.error { "awt.nativeDoubleBuffering enabled. Please disable awt.nativeDoubleBuffering." }
    }
  }

  private fun transformClass(className: String, classfileBuffer: ByteArray): ByteArray? {
    return try {
      when (className) {
        "sun/java2d/SunGraphics2D" -> transformSunGraphics2D(className, classfileBuffer)
        "sun/awt/image/SunVolatileImage" -> transformSunVolatileImage(className, classfileBuffer)
        "java/awt/image/BufferedImage" -> transformBufferedImage(className, classfileBuffer)
        "java/awt/Component" -> transformComponent(className, classfileBuffer)
        "javax/swing/JComponent" -> transformJComponent(className, classfileBuffer)
        "com/intellij/ui/BalloonImpl\$MyComponent" -> transformBalloonImpl(className, classfileBuffer)
        else -> classfileBuffer
      }
    }
    catch (e: Exception) {
      logger.error(e) { "Class transform error" }
      null
    }
  }

  private fun transformSunGraphics2D(
    classPath: String,
    classfileBuffer: ByteArray,
  ): ByteArray {
    logger.debug { "Loading SunGraphics2D..." }
    val clazz = getClassFromClassfileBuffer(classPath, classfileBuffer)
    clazz.defrost()
    clazz.declaredBehaviors.forEach {
      if (CommandsHandler.isSupportedCommand(it.longName)) {
        if ((it.methodInfo.accessFlags and AccessFlag.STATIC) > 0) {
          return@forEach
        }
        it.insertBefore("""
            $DRAW_HANDLER_CLASS_LOADING
            clazz
              .getMethod("handleGraphics2D", new Class[] {String.class, Object[].class, java.awt.Graphics.class})
              .invoke(null, new Object[] {"${it.longName}", $JAVASSIST_ARGS, $JAVASSIST_THIS});
          """.trimIndent())
      }
    }

    return clazz.toBytecode()
  }

  private fun transformSunVolatileImage(
    classPath: String,
    classfileBuffer: ByteArray,
  ): ByteArray {
    logger.debug { "Loading SunVolatileImage..." }
    val clazz = getClassFromClassfileBuffer(classPath, classfileBuffer)
    clazz.defrost()
    val createGraphicsMethod = clazz.getDeclaredMethod("createGraphics")
    createGraphicsMethod.insertBefore("""
      $DRAW_HANDLER_CLASS_LOADING
      clazz
        .getMethod("sunVolatileImageCreateGraphics", new Class[0])
        .invoke(null, new Object[0]);
    """.trimIndent())

    return clazz.toBytecode()
  }

  private fun transformBufferedImage(
    classPath: String,
    classfileBuffer: ByteArray,
  ): ByteArray {
    logger.debug { "Loading BufferedImage..." }
    val clazz = getClassFromClassfileBuffer(classPath, classfileBuffer)
    clazz.defrost()
    val createGraphicsMethod = clazz.getDeclaredMethod("createGraphics")
    createGraphicsMethod.insertBefore("""
      $DRAW_HANDLER_CLASS_LOADING
      clazz
        .getMethod("bufferedImageCreateGraphics", new Class[0])
        .invoke(null, new Object[0]);
    """.trimIndent())

    return clazz.toBytecode()
  }

  private fun transformBalloonImpl(
    classPath: String,
    classfileBuffer: ByteArray,
  ): ByteArray {
    logger.debug { "Loading BalloonImpl..." }
    val clazz = getClassFromClassfileBuffer(classPath, classfileBuffer)
    clazz.defrost()
    println(clazz)
    val initImage = clazz.getDeclaredMethod("initComponentImage")
    initImage.insertBefore("""
          $DRAW_HANDLER_CLASS_LOADING
          clazz
            .getMethod("startInitBalloonImage", new Class[0])
            .invoke(null, new Object[0]);
        """.trimIndent())

    initImage.insertAfter("""
          $DRAW_HANDLER_CLASS_LOADING
          clazz
            .getMethod("endInitBalloonImage", new Class[0])
            .invoke(null, new Object[0]);
        """.trimIndent())

    return clazz.toBytecode()
  }

  private fun transformComponent(
    classPath: String,
    classfileBuffer: ByteArray,
  ): ByteArray {
    logger.debug { "Loading Component..." }
    val clazz = getClassFromClassfileBuffer(classPath, classfileBuffer)
    clazz.defrost()
    val updateCursorImmediatelyMethod = clazz.getDeclaredMethod("updateCursorImmediately")
    updateCursorImmediatelyMethod.insertAfter("""
      $DRAW_HANDLER_CLASS_LOADING
      clazz
        .getMethod("handleUpdateCursorImmediately", new Class[] {java.awt.Component.class})
        .invoke(null, new Object[] {$JAVASSIST_THIS});
    """.trimIndent())

    return clazz.toBytecode()
  }

  private fun transformJComponent(
    classPath: String,
    classfileBuffer: ByteArray,
  ): ByteArray {
    logger.debug { "Loading JComponent..." }
    val clazz = getClassFromClassfileBuffer(classPath, classfileBuffer)
    clazz.defrost()
    val paintToOffscreenMethod = clazz.getDeclaredMethod("paintToOffscreen")
    paintToOffscreenMethod.insertBefore("""
          $DRAW_HANDLER_CLASS_LOADING
          clazz
            .getMethod("beginPaintToOffscreen", new Class[] {
                javax.swing.JComponent.class,
                Integer.class,
                Integer.class,
                Integer.class,
                Integer.class
              })
            .invoke(null, new Object[] {$JAVASSIST_THIS, new Integer(x), new Integer(y), new Integer(w), new Integer(h)});
        """.trimIndent())
    paintToOffscreenMethod.insertAfter("""
          $DRAW_HANDLER_CLASS_LOADING
          clazz
            .getMethod("endPaintToOffscreen", new Class[0])
            .invoke(null, new Object[0]);
        """.trimIndent())

    return clazz.toBytecode()
  }

  private fun getClassFromClassfileBuffer(
    className: String,
    classfileBuffer: ByteArray,
  ): CtClass {
    val pool = ClassPool.getDefault()
    val classPath = className.replace("/", ".")
    pool.insertClassPath(ByteArrayClassPath(classPath, classfileBuffer))
    return pool.get(classPath)
  }

  companion object {
    private val logger = Logger<GraphicsTransformer>()

    val DRAW_HANDLER_CLASS = GraphicsInterceptor::class.qualifiedName!!

    // language=java prefix="import " suffix=;
    private const val PROJECTOR_CLASS_LOADER_NAME = "org.jetbrains.projector.util.loading.ProjectorClassLoader"

    @Suppress("rawtypes", "unchecked")
    // language=java prefix="class Dummy { void dummy() {" suffix=}}
    private val DRAW_HANDLER_CLASS_LOADING = """
      Class prjClassLoaderClazz = ClassLoader.getSystemClassLoader().loadClass("$PROJECTOR_CLASS_LOADER_NAME");
      ClassLoader loader = (ClassLoader) prjClassLoaderClazz.getDeclaredMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
      Class clazz = loader.loadClass("$DRAW_HANDLER_CLASS");
    """.trimIndent()

    private const val JAVASSIST_ARGS = "${'$'}args"
    private const val JAVASSIST_THIS = "${'$'}0"
  }
}
