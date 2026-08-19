package org.cirjson.cirjackson.databind.module

import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.deserialization.ValueDeserializerModifier
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.serialization.ValueSerializerModifier
import org.cirjson.cirjackson.databind.util.UniqueId
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Vanilla [CirJacksonModule] implementation that allows registration of serializers and deserializers, bean serializer
 * and deserializer modifiers, registration of subtypes and mix-ins as well as some other commonly needed aspects
 * (addition of custom [AbstractTypeResolvers][AbstractTypeResolver], [ValueInstantiators][ValueInstantiator]).
 * 
 * NOTE: that (de)serializers are registered as "default" (de)serializers. As a result, they will have lower priority
 * than the ones indicated through annotations on both Class and property-associated annotations -- for example,
 * [org.cirjson.cirjackson.databind.annotation.CirJsonDeserialize].
 * 
 * In cases where both module-based (de)serializers and annotation-based (de)serializers are registered, the
 * (de)serializer specified by the annotation will take precedence.
 * 
 * NOTE: although it is not expected that subtypes should need to override [setupModule] method, if they choose to do so
 * they MUST call `super.setupModule(context)` to ensure that registration works as expected.
 * 
 * WARNING: when registering [ValueSerializers][ValueSerializer] and [ValueDeserializers][ValueDeserializer], only type
 * erased `KClass` is compared: this means that usually you should NOT use this implementation for registering
 * structured types such as [Collections][Collection] or [Maps][Map]: this because parametric type information will not
 * be considered, and you may end up having "wrong" handler for your type. What you need to do, instead, is to implement
 * [org.cirjson.cirjackson.databind.deserialization.Deserializers] and/or
 * [org.cirjson.cirjackson.databind.serialization.Serializers] callbacks to match full type signatures (with
 * [KotlinType]).
 */
open class SimpleModule(name: String?, protected val myVersion: Version, registrationId: Any?) : CirJacksonModule() {

    protected val myName: String

    /**
     * Unique id generated to avoid instances from ever matching so all registrations succeed.
     * 
     * NOTE! If serialization of SimpleModule instance needed, should be [java.io.Serializable].
     */
    protected val myId: Any

    protected var mySerializers: SimpleSerializers? = null

    protected var myDeserializers: SimpleDeserializers? = null

    protected var myKeySerializers: SimpleSerializers? = null

    protected var myKeyDeserializers: SimpleKeyDeserializers? = null

    protected var myDefaultNullValueSerializer: ValueSerializer<*>? = null

    protected var myDefaultNullKeySerializer: ValueSerializer<*>? = null

    /**
     * Lazily-constructed resolver used for storing mappings from abstract classes to more specific implementing classes
     * (which may be abstract or concrete)
     */
    protected var myAbstractTypeResolver: SimpleAbstractTypeResolver? = null

    /**
     * Lazily-constructed resolver used for storing mappings from abstract classes to more specific implementing classes
     * (which may be abstract or concrete)
     */
    protected var myValueInstantiators: SimpleValueInstantiators? = null

    protected var mySerializerModifier: ValueSerializerModifier? = null

    protected var myDeserializerModifier: ValueDeserializerModifier? = null

    /**
     * Lazily-constructed map that contains mix-in definitions, indexed by target class, value being mix-in to apply.
     */
    protected var myMixins: HashMap<KClass<*>, KClass<*>>? = null

    /**
     * Set of subtypes to register, if any.
     */
    protected var mySubtypes: LinkedHashSet<NamedType>? = null

    protected var myNamingStrategy: PropertyNamingStrategy? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Constructors that should only be used for non-reusable convenience modules used by app code: "real" modules
     * should use actual name and version number information.
     */
    constructor() : this(null, Version.unknownVersion())

    /**
     * Convenience constructor that will default version to [Version.unknownVersion].
     */
    constructor(name: String?) : this(name, Version.unknownVersion())

    /**
     * Convenience constructor that will use specified Version, including name from [Version.artifactId]
     */
    constructor(version: Version) : this(version.artifactId, version)

    /**
     * Constructor to use for actual reusable modules. ObjectMapper may use name as identifier to notice attempts for
     * multiple registrations of the same module (although it does not have to).
     *
     * @param name Unique name of the module
     * 
     * @param version Version of the module
     */
    constructor(name: String?, version: Version) : this(name, version, null)

    init {
        var realName = name
        var realRegistrationId = registrationId

        if (realName == null) {
            if (this::class == SimpleModule::class) {
                if (realRegistrationId == null) {
                    realRegistrationId = UniqueId.create("SimpleModule-")
                }

                realName = "SimpleModule-$realRegistrationId"
            } else {
                realName = this::class.jvmName
            }
        }

        if (realRegistrationId == null) {
            realRegistrationId = realName
        }

        myName = realName
        myId = realRegistrationId
    }

    /*
     *******************************************************************************************************************
     * Simple accessors
     *******************************************************************************************************************
     */

    override fun version(): Version {
        return myVersion
    }

    /**
     * Since instances are likely to be custom, implementation returns `null` if (but only if!) this class is directly
     * instantiated; but class name (default impl) for subclasses.
     */
    override val registrationId: Any
        get() = myId

    /*
     *******************************************************************************************************************
     * Simple setters to allow overriding
     *******************************************************************************************************************
     */

    open fun setSerializers(serializers: SimpleSerializers?): SimpleModule {
        mySerializers = serializers
        return this
    }

    open fun setDeserializers(deserializers: SimpleDeserializers?): SimpleModule {
        myDeserializers = deserializers
        return this
    }

    open fun setKeySerializers(keySerializers: SimpleSerializers?): SimpleModule {
        myKeySerializers = keySerializers
        return this
    }

    open fun setKeyDeserializers(keyDeserializers: SimpleKeyDeserializers?): SimpleModule {
        myKeyDeserializers = keyDeserializers
        return this
    }

    open fun setDefaultNullValueSerializer(defaultNullValueSerializer: ValueSerializer<*>?): SimpleModule {
        myDefaultNullValueSerializer = defaultNullValueSerializer
        return this
    }

    open fun setDefaultNullKeySerializer(defaultNullKeySerializer: ValueSerializer<*>?): SimpleModule {
        myDefaultNullKeySerializer = defaultNullKeySerializer
        return this
    }

    open fun setAbstractTypes(abstractTypes: SimpleAbstractTypeResolver?): SimpleModule {
        myAbstractTypeResolver = abstractTypes
        return this
    }

    open fun setValueInstantiators(valueInstantiators: SimpleValueInstantiators?): SimpleModule {
        myValueInstantiators = valueInstantiators
        return this
    }

    open fun setSerializerModifier(serializerModifier: ValueSerializerModifier?): SimpleModule {
        mySerializerModifier = serializerModifier
        return this
    }

    open fun setDeserializerModifier(deserializerModifier: ValueDeserializerModifier?): SimpleModule {
        myDeserializerModifier = deserializerModifier
        return this
    }

    open fun setMixins(mixins: HashMap<KClass<*>, KClass<*>>?): SimpleModule {
        myMixins = mixins
        return this
    }

    open fun setSubtypes(subtypes: LinkedHashSet<NamedType>?): SimpleModule {
        mySubtypes = subtypes
        return this
    }

    open fun setNamingStrategy(namingStrategy: PropertyNamingStrategy?): SimpleModule {
        myNamingStrategy = namingStrategy
        return this
    }

    /*
     *******************************************************************************************************************
     * Configuration methods, adding serializers
     *******************************************************************************************************************
     */

    /**
     * Method for adding serializer to handle type that the serializer claims to handle
     * (see [ValueSerializer.handledType]).
     * 
     * WARNING! Type matching only uses type-erased `KClass` and should NOT be used when registering serializers for
     * generic types like [Collection] and [Map].
     * 
     * WARNING! "Last one wins" rule is applied. Possible earlier addition of a serializer for a given KClass will be
     * replaced.
     * 
     * NOTE: This method registers "default" serializers only. See a note on precedence in class doc.
     */
    open fun addSerializer(serializer: ValueSerializer<*>): SimpleModule {
        val serializers = mySerializers ?: SimpleSerializers().also { mySerializers = it }
        serializers.addSerializer(serializer)
        return this
    }

    /**
     * Method for adding serializer to handle values of specific type.
     * 
     * WARNING! Type matching only uses type-erased `KClass` and should NOT be used when registering serializers for
     * generic types like [Collection] and [Map].
     * 
     * WARNING! "Last one wins" rule is applied. Possible earlier addition of a serializer for a given KClass will be
     * replaced.
     * 
     * NOTE: This method registers "default" serializers only. See a note on precedence in class doc.
     */
    open fun <T : Any> addSerializer(type: KClass<out T>, serializer: ValueSerializer<T>): SimpleModule {
        val serializers = mySerializers ?: SimpleSerializers().also { mySerializers = it }
        serializers.addSerializer(type, serializer)
        return this
    }

    /**
     * NOTE: This method registers "default" serializers only. See a note on precedence in class doc.
     */
    open fun <T : Any> addKeySerializer(type: KClass<out T>, serializer: ValueSerializer<T>): SimpleModule {
        val serializers = myKeySerializers ?: SimpleSerializers().also { myKeySerializers = it }
        serializers.addSerializer(type, serializer)
        return this
    }

    /*
     *******************************************************************************************************************
     * Configuration methods, adding deserializers
     *******************************************************************************************************************
     */

    /**
     * Method for adding deserializer to handle specified type.
     * 
     * WARNING! Type matching only uses type-erased `KClass` and should NOT be used when registering serializers for
     * generic types like [Collection] and [Map].
     * 
     * WARNING! "Last one wins" rule is applied. Possible earlier addition of a serializer for a given Class will be
     * replaced.
     * 
     * NOTE: This method registers "default" deserializers only. See a note on precedence in class doc.
     */
    open fun <T : Any> addDeserializer(forType: KClass<T>, deserializer: ValueDeserializer<out T>): SimpleModule {
        val deserializers = myDeserializers ?: SimpleDeserializers().also { myDeserializers = it }
        deserializers.addDeserializer(forType, deserializer)
        return this
    }

    /**
     * NOTE: This method registers "default" (de)serializers only. See a note on precedence in class doc.
     */
    open fun addKeyDeserializer(forType: KClass<*>, keyDeserializer: KeyDeserializer): SimpleModule {
        val keySerializers = myKeyDeserializers ?: SimpleKeyDeserializers().also { myKeyDeserializers = it }
        keySerializers.addKeyDeserializer(forType, keyDeserializer)
        return this
    }

    /*
     *******************************************************************************************************************
     * Configuration methods, adding type mapping
     *******************************************************************************************************************
     */

    /**
     * Lazily-constructed resolver used for storing mappings from abstract classes to more specific implementing classes
     * (which may be abstract or concrete)
     */
    open fun <T : Any> addAbstractTypeMapping(supertype: KClass<T>, subtype: KClass<out T>): SimpleModule {
        val abstractTypes = myAbstractTypeResolver ?: SimpleAbstractTypeResolver().also { myAbstractTypeResolver = it }
        abstractTypes.addMapping(supertype, subtype)
        return this
    }

    /**
     * Method for adding set of subtypes to be registered with [ObjectMapper] this is an alternative to using
     * annotations in super type to indicate subtypes.
     */
    open fun registerSubtypes(vararg subtypes: KClass<*>): SimpleModule {
        val currentSubtypes = mySubtypes ?: LinkedHashSet<NamedType>().also { mySubtypes = it }
        currentSubtypes.addAll(subtypes.map { NamedType(it) })
        return this
    }

    /**
     * Method for adding set of subtypes (along with type name to use) to be registered with [ObjectMapper] this is an
     * alternative to using annotations in super type to indicate subtypes.
     */
    open fun registerSubtypes(vararg subtypes: NamedType): SimpleModule {
        val currentSubtypes = mySubtypes ?: LinkedHashSet<NamedType>().also { mySubtypes = it }
        currentSubtypes.addAll(subtypes)
        return this
    }

    /**
     * Method for adding set of subtypes (along with type name to use) to be registered with [ObjectMapper] this is an
     * alternative to using annotations in super type to indicate subtypes.
     */
    open fun registerSubtypes(subtypes: Collection<KClass<*>>): SimpleModule {
        val currentSubtypes = mySubtypes ?: LinkedHashSet<NamedType>().also { mySubtypes = it }
        currentSubtypes.addAll(subtypes.map { NamedType(it) })
        return this
    }

    /*
     *******************************************************************************************************************
     * Configuration methods, adding other handlers
     *******************************************************************************************************************
     */

    /**
     * Method for registering [ValueInstantiator] to use when deserializing instances of type `beanType`.
     *
     * Instantiator is registered when module is registered for `ObjectMapper`.
     */
    open fun addValueInstantiator(forType: KClass<*>, instantiator: ValueInstantiator): SimpleModule {
        val valueInstantiators = myValueInstantiators ?: SimpleValueInstantiators().also { myValueInstantiators = it }
        valueInstantiators.addValueInstantiator(forType, instantiator)
        return this
    }

    open fun setMixInAnnotation(targetType: KClass<*>, mixinClass: KClass<*>): SimpleModule {
        val mixins = myMixins ?: HashMap<KClass<*>, KClass<*>>().also { myMixins = it }
        mixins[targetType] = mixinClass
        return this
    }

    /*
     *******************************************************************************************************************
     * CirJacksonModule implementation
     *******************************************************************************************************************
     */

    override val moduleName: String?
        get() = myName

    /**
     * Standard implementation handles registration of all configured customizations: it is important that subclasses
     * call this implementation (usually before additional custom logic) if they choose to override it; otherwise
     * customizations will not be registered.
     */
    override fun setupModule(context: SetupContext) {
        mySerializers?.also { context.addSerializers(it) }
        myDeserializers?.also { context.addDeserializers(it) }
        myKeySerializers?.also { context.addKeySerializers(it) }
        myKeyDeserializers?.also { context.addKeyDeserializers(it) }
        myAbstractTypeResolver?.also { context.addAbstractTypeResolver(it) }
        myValueInstantiators?.also { context.addValueInstantiators(it) }
        mySerializerModifier?.also { context.addSerializerModifier(it) }
        myDeserializerModifier?.also { context.addDeserializerModifier(it) }
        myDefaultNullValueSerializer?.also { context.overrideDefaultNullValueSerializer(it) }
        myDefaultNullKeySerializer?.also { context.overrideDefaultNullKeySerializer(it) }

        mySubtypes?.takeUnless { it.isEmpty() }?.also { context.registerSubtypes(*it.toTypedArray()) }

        myMixins?.also {
            for ((target, mixinSource) in it) {
                context.setMixIn(target, mixinSource)
            }
        }
    }

}