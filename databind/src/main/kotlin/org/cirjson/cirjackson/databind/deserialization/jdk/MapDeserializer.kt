package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KeyDeserializer
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.bean.PropertyBasedCreator
import org.cirjson.cirjackson.databind.deserialization.standard.ContainerDeserializerBase
import org.cirjson.cirjackson.databind.util.IgnorePropertiesUtil

/**
 * Basic deserializer that can take JSON "Object" structure and construct a [Map] instance, with typed contents.
 * 
 * Note: for untyped content (one indicated by passing `Any::class` as the type), [UntypedObjectDeserializer] is used
 * instead. It can also construct [Maps][Map], but not with specific POJO types, only other containers and
 * primitives/wrappers.
 */
@CirJacksonStandardImplementation
open class MapDeserializer : ContainerDeserializerBase<MutableMap<Any?, Any?>> {

    /**
     * Key deserializer to use; either passed via constructor (when indicated by annotations), or resolved when
     * [resolve] is called;
     */
    protected val myKeyDeserializer: KeyDeserializer?

    /**
     * Flag set to indicate that the key type is [String] (or [Any], for which String is acceptable), **and** that the
     * default CirJackson key deserializer would be used. If both are true, can optimize handling.
     */
    protected val myStandardStringKey: Boolean

    /**
     * Value deserializer.
     */
    protected val myValueDeserializer: ValueDeserializer<Any>?

    /**
     * If value instances have polymorphic type information, this is the type deserializer that can handle it
     */
    protected val myValueTypeDeserializer: TypeDeserializer?

    protected val myValueInstantiator: ValueInstantiator?

    /**
     * Deserializer that is used iff delegate-based creator is to be used for deserializing from CirJSON Object.
     */
    protected var myDelegateDeserializer: ValueDeserializer<Any>?

    /**
     * If the Map is to be instantiated using non-default constructor or factory method that takes one or more named
     * properties as argument(s), this creator is used for instantiation.
     */
    protected var myPropertyBasedCreator: PropertyBasedCreator?

    protected val myHasDefaultCreator: Boolean

    protected var myIgnorableProperties: Set<String>?

    protected var myIncludableProperties: Set<String>?

    /**
     * Helper object used for name-based filtering
     */
    protected var myInclusionChecker: IgnorePropertiesUtil.Checker?

    /**
     * Flag used to check, whether the
     * [StreamReadCapability.DUPLICATE_PROPERTIES][org.cirjson.cirjackson.core.StreamReadCapability.DUPLICATE_PROPERTIES]
     * can be applied, because the Map has declared value type of `Any`.
     */
    protected var myCheckDuplicateSquash: Boolean

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(mapType: KotlinType, valueInstantiator: ValueInstantiator, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<Any>?, valueTypeDeserializer: TypeDeserializer?) : super(mapType, null,
            null) {
        myKeyDeserializer = keyDeserializer
        myStandardStringKey = isStandardKeyDeserializer(mapType, keyDeserializer)
        myValueDeserializer = valueDeserializer
        myValueTypeDeserializer = valueTypeDeserializer
        myValueInstantiator = valueInstantiator
        myDelegateDeserializer = null
        myPropertyBasedCreator = null
        myHasDefaultCreator = valueInstantiator.canCreateUsingDefault()
        myIgnorableProperties = null
        myIncludableProperties = null
        myInclusionChecker = null
        myCheckDuplicateSquash = mapType.contentType!!.hasRawClass(Any::class)
    }

    protected constructor(source: MapDeserializer) : super(source) {
        myKeyDeserializer = source.myKeyDeserializer
        myStandardStringKey = source.myStandardStringKey
        myValueDeserializer = source.myValueDeserializer
        myValueTypeDeserializer = source.myValueTypeDeserializer
        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myHasDefaultCreator = source.myHasDefaultCreator
        myIgnorableProperties = source.myIgnorableProperties
        myIncludableProperties = source.myIncludableProperties
        myInclusionChecker = source.myInclusionChecker
        myCheckDuplicateSquash = source.myCheckDuplicateSquash
    }

    protected constructor(source: MapDeserializer, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<Any>?, valueTypeDeserializer: TypeDeserializer?,
            nullValueProvider: NullValueProvider?, ignorableProperties: Set<String>?) : this(source, keyDeserializer,
            valueDeserializer, valueTypeDeserializer, nullValueProvider, ignorableProperties, null)

    protected constructor(source: MapDeserializer, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<Any>?, valueTypeDeserializer: TypeDeserializer?,
            nullValueProvider: NullValueProvider?, ignorableProperties: Set<String>?,
            includableProperties: Set<String>?) : super(source, nullValueProvider, null) {
        myKeyDeserializer = keyDeserializer
        myStandardStringKey = isStandardKeyDeserializer(myContainerType, keyDeserializer)
        myValueDeserializer = valueDeserializer
        myValueTypeDeserializer = valueTypeDeserializer
        myValueInstantiator = source.myValueInstantiator
        myDelegateDeserializer = source.myDelegateDeserializer
        myPropertyBasedCreator = source.myPropertyBasedCreator
        myHasDefaultCreator = source.myHasDefaultCreator
        myIgnorableProperties = ignorableProperties
        myIncludableProperties = includableProperties
        myInclusionChecker = IgnorePropertiesUtil.buildCheckerIfNeeded(includableProperties, includableProperties)
        myCheckDuplicateSquash = source.myCheckDuplicateSquash
    }

    /**
     * Fluent factory method used to create a copy with slightly different settings. When subclassing, MUST be
     * overridden.
     */
    protected open fun withResolved(keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, nullValueProvider: NullValueProvider?,
            ignorableProperties: Set<String>?): MapDeserializer {
        return withResolved(keyDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider,
                ignorableProperties, myIgnorableProperties)
    }

    /**
     * Fluent factory method used to create a copy with slightly different settings. When subclassing, MUST be
     * overridden.
     */
    protected open fun withResolved(keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, nullValueProvider: NullValueProvider?,
            ignorableProperties: Set<String>?, includableProperties: Set<String>?): MapDeserializer {
        if (myKeyDeserializer === keyDeserializer && myValueDeserializer === valueDeserializer &&
                myValueTypeDeserializer === valueTypeDeserializer && myNullProvider === nullValueProvider &&
                myIgnorableProperties === ignorableProperties && myIncludableProperties === includableProperties) {
            return this
        }

        return MapDeserializer(this, keyDeserializer, valueDeserializer, valueTypeDeserializer, nullValueProvider,
                ignorableProperties, includableProperties)
    }

    /**
     * Helper method used to check whether we can just use the default key deserialization, where CirJSON String becomes
     * Kotlin String.
     */
    protected fun isStandardKeyDeserializer(mapType: KotlinType, keyDeserializer: KeyDeserializer?): Boolean {
        keyDeserializer ?: return true
        val keyType = mapType.keyType ?: return true
        val rawKeyType = keyType.rawClass
        return (rawKeyType == String::class || rawKeyType == Any::class) && isDefaultKeySerializer(keyDeserializer)
    }

    open var ignorableProperties: Set<String>?
        get() = throw UnsupportedOperationException()
        set(ignorableProperties) {
            myIgnorableProperties = ignorableProperties?.takeUnless { it.isEmpty() }
            myInclusionChecker =
                    IgnorePropertiesUtil.buildCheckerIfNeeded(myIgnorableProperties, myIncludableProperties)
        }

    open var includableProperties: Set<String>?
        get() = throw UnsupportedOperationException()
        set(includableProperties) {
            myIncludableProperties = includableProperties?.takeUnless { it.isEmpty() }
            myInclusionChecker =
                    IgnorePropertiesUtil.buildCheckerIfNeeded(myIgnorableProperties, myIncludableProperties)
        }

    /*
     *******************************************************************************************************************
     * ContainerDeserializerBase implementation
     *******************************************************************************************************************
     */

    override val contentDeserializer: ValueDeserializer<Any>?
        get() = myValueDeserializer

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): MutableMap<Any?, Any?>? {
        TODO("Not yet implemented")
    }

}