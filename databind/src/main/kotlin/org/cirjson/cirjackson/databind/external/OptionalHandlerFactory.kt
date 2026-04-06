package org.cirjson.cirjackson.databind.external

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializationConfig
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.serialization.jdk.JavaUtilDateSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializer
import org.cirjson.cirjackson.databind.util.createInstance
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.superclass
import org.cirjson.cirjackson.databind.util.typeDescription
import org.w3c.dom.Document
import org.w3c.dom.Node
import javax.xml.datatype.Duration
import javax.xml.datatype.XMLGregorianCalendar
import javax.xml.namespace.QName
import kotlin.reflect.KClass

/**
 * Helper class used for isolating details of handling optional+external types (`javax.xml` classes) from standard
 * factories that offer them.
 */
open class OptionalHandlerFactory protected constructor() {

    protected val mySqlSerializers: MutableMap<String, Any> = HashMap<String, Any>().also {
        it[CLASS_NAME_JAVA_SQL_TIMESTAMP] = JavaUtilDateSerializer.INSTANCE
        it[CLASS_NAME_JAVA_SQL_DATE] = "org.cirjson.cirjackson.databind.external.sql.JavaSqlDateSerializer"
        it[CLASS_NAME_JAVA_SQL_TIME] = "org.cirjson.cirjackson.databind.external.sql.JavaSqlTimeSerializer"
        it[CLASS_NAME_JAVA_SQL_BLOB] = "org.cirjson.cirjackson.databind.external.sql.JavaSqlBlobSerializer"
        it[CLASS_NAME_JAVA_SQL_SERIAL_BLOB] = "org.cirjson.cirjackson.databind.external.sql.JavaSqlBlobSerializer"
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    open fun findSerializer(config: SerializationConfig, type: KotlinType): ValueSerializer<*>? {
        val rawType = type.rawClass

        if (isXofY(rawType, CLASS_DOM_NODE)) {
            return DOMSerializer()
        }

        val className = rawType.qualifiedName!!
        val sqlHandler = mySqlSerializers[className]

        if (sqlHandler != null) {
            return sqlHandler as? ValueSerializer<*> ?: instantiate(sqlHandler as String, type) as ValueSerializer<*>?
        }

        return if (!className.startsWith(PACKAGE_PREFIX_JAVAX_XML) && !hasSuperclassStartingWith(rawType,
                        PACKAGE_PREFIX_JAVAX_XML)) {
            null
        } else if (Duration::class.isAssignableFrom(rawType) || QName::class.isAssignableFrom(rawType)) {
            ToStringSerializer.INSTANCE
        } else if (XMLGregorianCalendar::class.isAssignableFrom(rawType)) {
            XMLGregorianCalendarSerializer.INSTANCE
        } else {
            null
        }
    }

    protected fun isXofY(valueType: KClass<*>, expectedType: KClass<*>?): Boolean {
        return expectedType?.isAssignableFrom(valueType) ?: false
    }

    /*
     *******************************************************************************************************************
     * Internal helper methods
     *******************************************************************************************************************
     */

    protected fun instantiate(className: String, valueType: KotlinType): Any? {
        try {
            return instantiate(Class.forName(className).kotlin, valueType)
        } catch (e: Exception) {
            throw IllegalStateException(
                    "Failed to find class `$className` for handling values of type ${valueType.typeDescription}, problem: (${e::class.qualifiedName}) ${e.message}")
        }
    }

    protected fun instantiate(handlerClass: KClass<*>, valueType: KotlinType): Any? {
        try {
            return handlerClass.createInstance(false)
        } catch (e: Exception) {
            throw IllegalStateException(
                    "Failed to create instance of `${handlerClass.qualifiedName}` for handling values of type ${valueType.typeDescription}, problem: (${e::class.qualifiedName}) ${e.message}")
        }
    }

    /**
     * We only need to check for class extension, as all implemented types are classes, not interfaces. This has
     * performance implications for some cases, as we do not need to go over interfaces implemented, just superclasses.
     */
    @Suppress("SameParameterValue")
    protected fun hasSuperclassStartingWith(rawType: KClass<*>, prefix: String): Boolean {
        var supertype = rawType.superclass

        while (supertype != null) {
            if (supertype == Any::class) {
                return false
            }

            if (supertype.qualifiedName!!.startsWith(prefix)) {
                return true
            }

            supertype = supertype.superclass
        }

        return false
    }

    companion object {

        const val PACKAGE_PREFIX_JAVAX_XML = "javax.xml."

        val CLASS_DOM_NODE = Node::class

        val CLASS_DOM_DOCUMENT = Document::class

        val INSTANCE = OptionalHandlerFactory()

        const val CLASS_NAME_JAVA_SQL_TIMESTAMP = "java.sql.Timestamp"

        const val CLASS_NAME_JAVA_SQL_DATE = "java.sql.Date"

        const val CLASS_NAME_JAVA_SQL_TIME = "java.sql.Time"

        const val CLASS_NAME_JAVA_SQL_BLOB = "java.sql.Blob"

        const val CLASS_NAME_JAVA_SQL_SERIAL_BLOB = "javax.sql.rowset.serial.SerialBlob"

    }

}