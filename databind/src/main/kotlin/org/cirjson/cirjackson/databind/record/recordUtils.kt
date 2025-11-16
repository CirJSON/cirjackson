package org.cirjson.cirjackson.databind.record

import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.PotentialCreator
import org.cirjson.cirjackson.databind.util.NativeImageUtil
import org.cirjson.cirjackson.databind.util.name
import org.cirjson.cirjackson.databind.util.typeDescription
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

val KClass<*>.recordFieldNames: Array<String>?
    get() = RecordAccessor.getRecordFieldNames(this)

fun AnnotatedClass.findCanonicalRecordConstructor(config: MapperConfig<*>,
        constructors: List<PotentialCreator>): PotentialCreator? {
    val recordFields = RecordAccessor.getRecordFields(rawType) ?: return null

    val argumentCount = recordFields.size

    if (argumentCount == 0) {
        val defaultConstructor = defaultConstructor

        if (defaultConstructor != null) {
            return PotentialCreator(defaultConstructor, null)
        }
    }

    mainLoop@ for (constructor in constructors) {
        if (constructor.parameterCount() != argumentCount) {
            continue
        }

        for (i in 0..<argumentCount) {
            if (constructor.creator().getRawParameterType(i) != recordFields[i].rawType) {
                continue@mainLoop
            }
        }

        val implicits = Array(argumentCount) {
            PropertyName.construct(recordFields[it].name)
        }

        return constructor.introspectParameterNames(config, implicits)
    }

    throw IllegalArgumentException("Failed to find the canonical Record constructor of type ${type.typeDescription}")
}

internal object RecordAccessor {

    private val RECORD_GET_RECORD_COMPONENTS: KFunction<*>

    private val RECORD_COMPONENT_GET_NAME: KFunction<*>

    private val RECORD_COMPONENT_GET_TYPE: KFunction<*>

    init {
        try {
            RECORD_GET_RECORD_COMPONENTS = Class::class.java.getMethod("getRecordComponents").kotlinFunction!!
            val c = Class.forName("java.lang.reflect.RecordComponent")
            RECORD_COMPONENT_GET_NAME = c.getMethod("getName").kotlinFunction!!
            RECORD_COMPONENT_GET_TYPE = c.getMethod("getType").kotlinFunction!!
        } catch (e: Exception) {
            throw RuntimeException(
                    "Failed to access Methods needed to support `java.lang.Record`: (${e::class.qualifiedName}) ${e.message}",
                    e)
        }
    }

    @Throws(IllegalAccessException::class)
    fun getRecordFieldNames(recordType: KClass<*>): Array<String>? {
        val components = recordComponents(recordType) ?: return null

        return Array(components.size) {
            try {
                RECORD_COMPONENT_GET_NAME.call(components[it]) as String
            } catch (e: Exception) {
                throw IllegalArgumentException(
                        "Failed to access name of field #$it (of ${components.size}) of Record type ${recordType.name}",
                        e)
            }
        }
    }

    @Throws(IllegalAccessException::class)
    fun getRecordFields(recordType: KClass<*>): Array<RawTypeName>? {
        val components = recordComponents(recordType) ?: return null

        return Array(components.size) {
            val name = try {
                RECORD_COMPONENT_GET_NAME.call(components[it]) as String
            } catch (e: Exception) {
                throw IllegalArgumentException(
                        "Failed to access name of field #$it (of ${components.size}) of Record type ${recordType.name}",
                        e)
            }

            val type = try {
                (RECORD_COMPONENT_GET_TYPE.call(components[it]) as Class<*>).kotlin
            } catch (e: Exception) {
                throw IllegalArgumentException(
                        "Failed to access type of field #$it (of ${components.size}) of Record type ${recordType.name}",
                        e)
            }

            RawTypeName(type, name)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(IllegalAccessException::class)
    private fun recordComponents(recordType: KClass<*>): Array<Any>? {
        try {
            return RECORD_GET_RECORD_COMPONENTS.call(recordType) as Array<Any>
        } catch (e: Exception) {
            if (NativeImageUtil.isUnsupportedFeatureError(e)) {
                return null
            }

            throw IllegalArgumentException("Failed to access RecordComponents of type ${recordType.name}")
        }
    }

}

internal data class RawTypeName(val rawType: KClass<*>, val name: String)