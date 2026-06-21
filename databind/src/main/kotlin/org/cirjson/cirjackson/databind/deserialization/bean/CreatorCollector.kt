package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.standard.StandardValueInstantiator
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedWithParams
import org.cirjson.cirjackson.databind.util.*

/**
 * Container class for storing information on creators (based on annotations, visibility), to be able to build actual
 * [ValueInstantiator] later on.
 *
 * @property myBeanDescription Type of bean being created
 */
open class CreatorCollector(protected val myBeanDescription: BeanDescription, config: MapperConfig<*>) {

    protected val myCanFixAccess = config.canOverrideAccessModifiers()

    protected val myForceAccess = config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS)

    /**
     * Set of creators we have collected so far
     */
    val myCreators = arrayOfNulls<AnnotatedWithParams>(11)

    /**
     * Bitmask of creators that were explicitly marked as creators; false for auto-detected (ones included base on
     * naming and/or visibility, not annotation)
     */
    protected var myExplicitCreators = 0

    protected var myHasNonDefaultCreator = false

    protected var myDelegateArguments: Array<SettableBeanProperty?>? = null

    protected var myArrayDelegateArguments: Array<SettableBeanProperty?>? = null

    protected var myPropertyBasedArguments: Array<SettableBeanProperty>? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    open fun constructValueInstantiator(context: DeserializationContext): ValueInstantiator {
        val config = context.config
        val delegateType = computeDelegateType(context, myCreators[CREATOR_DELEGATE], myDelegateArguments)
        val arrayDelegateType =
                computeDelegateType(context, myCreators[CREATOR_ARRAY_DELEGATE], myArrayDelegateArguments)
        val type = myBeanDescription.type

        return StandardValueInstantiator(config, type).apply {
            configureFromObjectSettings(myCreators[CREATOR_DEFAULT], myCreators[CREATOR_DELEGATE], delegateType,
                    myDelegateArguments, myCreators[CREATOR_PROPERTIES], myPropertyBasedArguments)
            configureFromArraySettings(myCreators[CREATOR_ARRAY_DELEGATE], arrayDelegateType, myArrayDelegateArguments)
            configureFromStringCreator(myCreators[CREATOR_STRING])
            configureFromIntCreator(myCreators[CREATOR_INT])
            configureFromLongCreator(myCreators[CREATOR_LONG])
            configureFromBigIntegerCreator(myCreators[CREATOR_BIG_INTEGER])
            configureFromDoubleCreator(myCreators[CREATOR_DOUBLE])
            configureFromBigDecimalCreator(myCreators[CREATOR_BIG_DECIMAL])
            configureFromBooleanCreator(myCreators[CREATOR_BOOLEAN])
        }
    }

    /*
     *******************************************************************************************************************
     * Setters
     *******************************************************************************************************************
     */

    /**
     * Setter called to indicate the default creator: no-arguments constructor or factory method that is called to
     * instantiate a value before populating it with data. Default creator is only used if no other creators are
     * indicated.
     *
     * The value passed is the creator method; no-arguments constructor or static factory method.
     */
    open var defaultCreator: AnnotatedWithParams?
        get() = throw UnsupportedOperationException("This is only a setter")
        set(value) {
            myCreators[CREATOR_DELEGATE] = fixAccess(value)
        }

    open fun addStringCreator(creator: AnnotatedWithParams, explicit: Boolean) {
        verifyNonDuplicate(creator, CREATOR_STRING, explicit)
    }

    open fun addIntCreator(creator: AnnotatedWithParams, explicit: Boolean) {
        verifyNonDuplicate(creator, CREATOR_INT, explicit)
    }

    open fun addLongCreator(creator: AnnotatedWithParams, explicit: Boolean) {
        verifyNonDuplicate(creator, CREATOR_LONG, explicit)
    }

    open fun addBigIntegerCreator(creator: AnnotatedWithParams, explicit: Boolean) {
        verifyNonDuplicate(creator, CREATOR_BIG_INTEGER, explicit)
    }

    open fun addDoubleCreator(creator: AnnotatedWithParams, explicit: Boolean) {
        verifyNonDuplicate(creator, CREATOR_DOUBLE, explicit)
    }

    open fun addBigDecimalCreator(creator: AnnotatedWithParams, explicit: Boolean) {
        verifyNonDuplicate(creator, CREATOR_BIG_DECIMAL, explicit)
    }

    open fun addBooleanCreator(creator: AnnotatedWithParams, explicit: Boolean) {
        verifyNonDuplicate(creator, CREATOR_BOOLEAN, explicit)
    }

    open fun addDelegatingCreator(creator: AnnotatedWithParams, explicit: Boolean,
            injectables: Array<SettableBeanProperty?>, delegateeIndex: Int) {
        if (creator.getParameterType(delegateeIndex)!!.isCollectionLikeType) {
            if (verifyNonDuplicate(creator, CREATOR_ARRAY_DELEGATE, explicit)) {
                myArrayDelegateArguments = injectables
            }
        } else if (verifyNonDuplicate(creator, CREATOR_DELEGATE, explicit)) {
            myDelegateArguments = injectables
        }
    }

    open fun addPropertyCreator(creator: AnnotatedWithParams, explicit: Boolean,
            properties: Array<SettableBeanProperty>) {
        if (!verifyNonDuplicate(creator, CREATOR_PROPERTIES, explicit)) {
            return
        } else if (properties.size <= 1) {
            myPropertyBasedArguments = properties
            return
        }

        val names = HashMap<String, Int>()

        for ((i, property) in properties.withIndex()) {
            val name = property.name

            if (name.isEmpty() && property.injectableValueId != null) {
                continue
            }

            val old = names.put(name, i) ?: continue
            throw IllegalArgumentException(
                    "Duplicate creator property \"$name\" (index $old vs $i) for type ${myBeanDescription.beanClass.name}")
        }

        myPropertyBasedArguments = properties
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open fun hasDefaultCreator(): Boolean {
        return myCreators[CREATOR_DEFAULT] != null
    }

    open fun hasDelegatingCreator(): Boolean {
        return myCreators[CREATOR_DELEGATE] != null
    }

    open fun hasPropertyBasedCreator(): Boolean {
        return myCreators[CREATOR_PROPERTIES] != null
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    private fun computeDelegateType(context: DeserializationContext, creator: AnnotatedWithParams?,
            delegateArguments: Array<SettableBeanProperty?>?): KotlinType? {
        if (!myHasNonDefaultCreator || creator == null) {
            return null
        }

        var index = 0

        if (delegateArguments != null) {
            for (i in delegateArguments.indices) {
                if (delegateArguments[i] == null) {
                    index = i
                    break
                }
            }
        }

        val config = context.config

        val baseType = creator.getParameterType(index)!!
        val introspector = config.annotationIntrospector ?: return baseType
        val delegate = creator.getParameter(index)

        val deserializerDefinition = introspector.findDeserializer(config, delegate)

        return deserializerDefinition?.let {
            val deserializer = context.deserializerInstance(delegate, deserializerDefinition)
            baseType.withValueHandler(deserializer)
        } ?: introspector.refineDeserializationType(config, delegate, baseType)
    }

    private fun <T : AnnotatedMember> fixAccess(member: T?): T? {
        member ?: return null

        if (myCanFixAccess) {
            (member.member!!.checkAndFixAccess(myForceAccess))
        }

        return member
    }

    /**
     * @return `true` if specified Creator is to be used
     */
    protected open fun verifyNonDuplicate(newOne: AnnotatedWithParams, typeIndex: Int, explicit: Boolean): Boolean {
        val mask = 1 shl typeIndex
        myHasNonDefaultCreator = true

        val oldOne = myCreators[typeIndex] ?: let {
            if (explicit) {
                myExplicitCreators = myExplicitCreators or mask
            }

            myCreators[typeIndex] = fixAccess(newOne)
            return true
        }

        val verify = if (myExplicitCreators and mask != 0) {
            if (!explicit) {
                return false
            }

            true
        } else {
            !explicit
        }

        if (!verify || oldOne::class != newOne::class) {
            if (explicit) {
                myExplicitCreators = myExplicitCreators or mask
            }

            myCreators[typeIndex] = fixAccess(newOne)
            return true
        }

        val oldType = oldOne.getRawParameterType(0)!!
        val newType = newOne.getRawParameterType(0)!!

        if (oldType == newType) {
            if (isEnumValueOf(newOne)) {
                return false
            } else if (!isEnumValueOf(oldOne)) {
                reportDuplicateCreator(typeIndex, explicit, oldOne, newOne)
            }
        } else if (newType.isAssignableFrom(oldType)) {
            return false
        } else if (!oldType.isAssignableFrom(newType)) {
            if (oldType.isPrimitive != newType.isPrimitive) {
                if (oldType.isPrimitive) {
                    return false
                }
            } else {
                reportDuplicateCreator(typeIndex, explicit, oldOne, newOne)
            }
        }

        if (explicit) {
            myExplicitCreators = myExplicitCreators or mask
        }

        myCreators[typeIndex] = fixAccess(newOne)
        return true
    }

    protected open fun reportDuplicateCreator(typeIndex: Int, explicit: Boolean, oldOne: AnnotatedWithParams,
            newOne: AnnotatedWithParams): Nothing {
        throw IllegalArgumentException(
                "Conflicting ${TYPE_DESCRIPTIONS[typeIndex]} creators: already had ${if (explicit) "explicitly marked" else "implicitly discovered"} creator $oldOne, encountered another: $newOne")
    }

    /**
     * Helper method for recognizing `Enum.valueOf()` factory method
     */
    protected open fun isEnumValueOf(creator: AnnotatedWithParams): Boolean {
        return creator.declaringClass.isEnumType && creator.name == "valueOf"
    }

    companion object {

        const val CREATOR_DEFAULT = 0

        const val CREATOR_STRING = 1

        const val CREATOR_INT = 2

        const val CREATOR_LONG = 3

        const val CREATOR_BIG_INTEGER = 4

        const val CREATOR_DOUBLE = 5

        const val CREATOR_BIG_DECIMAL = 6

        const val CREATOR_BOOLEAN = 7

        const val CREATOR_DELEGATE = 8

        const val CREATOR_PROPERTIES = 9

        const val CREATOR_ARRAY_DELEGATE = 10

        val TYPE_DESCRIPTIONS =
                arrayOf("default", "from-String", "from-int", "from-long", "from-big-integer", "from-double",
                        "from-big-decimal", "from-boolean", "delegate", "property-based", "array-delegate")

    }

}