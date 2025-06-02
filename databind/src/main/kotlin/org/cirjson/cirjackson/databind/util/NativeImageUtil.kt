package org.cirjson.cirjackson.databind.util

import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass

/**
 * Utilities for graal native image support; mostly to improve error message handling
 * in case of missing information for native image.
 */
object NativeImageUtil {

    /**
     * Checks whether we're running in SubstrateVM native image **only by the presence** of
     * `"org.graalvm.nativeimage.imagecode"` system property, regardless of its value (buildtime or runtime). We are
     * irrespective of the build or runtime phase, because native-image can initialize static initializers at build
     * time.
     */
    val isInNativeImage = System.getProperty("org.graalvm.nativeimage.imagecode") != null

    /**
     * Check whether we're running in SubstrateVM native image and also in "runtime" mode. The "runtime" check cannot be
     * a constant because the static initializer may run early during build time
     *
     * As optimization, [isInNativeImage] is used to short-circuit on normal JVMs.
     */
    val isInNativeImageAndIsAtRuntime by lazy {
        isInNativeImage && System.getProperty("org.graalvm.nativeimage.imagecode") == "runtime"
    }

    /**
     * Check whether the given error is a SubstrateVM UnsupportedFeatureError
     */
    fun isUnsupportedFeatureError(throwable: Throwable): Boolean {
        if (!isInNativeImageAndIsAtRuntime) {
            return false
        }

        val e = (throwable as? InvocationTargetException)?.cause ?: throwable
        return e::class.qualifiedName == "com.oracle.svm.core.jdk.UnsupportedFeatureError"
    }

    /**
     * Check whether the given class is likely missing reflection configuration (running in native image, and no members
     * visible in reflection).
     */
    fun needsReflectionConfiguration(clazz: KClass<*>): Boolean {
        if (!isInNativeImageAndIsAtRuntime) {
            return false
        }

        return (clazz.java.declaredFields.isEmpty() || clazz.isRecordType) && clazz.java.declaredMethods.isEmpty() &&
                clazz.java.declaredConstructors.isEmpty()
    }

}