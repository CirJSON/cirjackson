package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.util.typeDescription
import java.util.*

/**
 * A [TypeDeserializer] capable of deducing polymorphic types based on the fields available. Deduction is limited to the
 * *names* of child properties (not their values or, consequently, any nested descendants). Exceptions will be thrown if
 * not enough unique information is present to select a single subtype.
 *
 * The current deduction process **does not** support pojo-hierarchies such that the absence of child fields infers a
 * parent type. That is, every deducible subtype MUST have some unique fields and the input data MUST contain said
 * unique fields to provide a *positive match*.
 */
open class AsDeductionTypeDeserializer : AsPropertyTypeDeserializer {

    private val myPropertyBitIndex: MutableMap<String, Int>

    private val mySubtypeFingerprints: Map<BitSet, String>

    constructor(context: DeserializationContext, baseType: KotlinType?, idResolver: TypeIdResolver,
            defaultImplementation: KotlinType?, subtypes: Collection<NamedType>) : super(baseType, idResolver, null,
            false, defaultImplementation, null, true) {
        myPropertyBitIndex = HashMap()
        mySubtypeFingerprints = buildFingerprints(context, subtypes)
    }

    constructor(source: AsDeductionTypeDeserializer, property: BeanProperty?) : super(source, property) {
        myPropertyBitIndex = source.myPropertyBitIndex
        mySubtypeFingerprints = source.mySubtypeFingerprints
    }

    override fun forProperty(property: BeanProperty?): TypeDeserializer {
        if (property === myProperty) {
            return this
        }

        return AsDeductionTypeDeserializer(this, property)
    }

    protected open fun buildFingerprints(context: DeserializationContext,
            subtypes: Collection<NamedType>): Map<BitSet, String> {
        val ignoreCase = context.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)

        var nextProperty = 0
        val fingerprints = HashMap<BitSet, String>()

        for (subtype in subtypes) {
            val subtyped = context.constructType(subtype.type)!!
            val properties = context.introspectBeanDescription(subtyped).findProperties()

            val fingerprint = BitSet(nextProperty + properties.size)

            for (property in properties) {
                var name = property.name

                if (ignoreCase) {
                    name = name.lowercase()
                }

                var bitIndex = myPropertyBitIndex[name]

                if (bitIndex == null) {
                    bitIndex = nextProperty
                    myPropertyBitIndex[name] = nextProperty++
                }

                for (alias in property.findAliases()) {
                    var simpleName = alias.simpleName

                    if (ignoreCase) {
                        simpleName = simpleName.lowercase()
                    }

                    myPropertyBitIndex.putIfAbsent(simpleName, bitIndex)
                }

                fingerprint.set(bitIndex)
            }

            val existingFingerprint = fingerprints.put(fingerprint, subtype.type.qualifiedName!!)

            if (existingFingerprint != null) {
                throw IllegalStateException(
                        "Subtypes $existingFingerprint and ${subtype.type.qualifiedName} have the same signature and cannot be uniquely deduced.")
            }
        }

        return fingerprints
    }

    override fun deserializeTypedFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        var token = parser.currentToken()

        if (token == CirJsonToken.START_OBJECT || token == CirJsonToken.CIRJSON_ID_PROPERTY_NAME) {
            if (token == CirJsonToken.START_OBJECT) {
                parser.nextToken()
            }

            parser.nextToken()
            token = parser.nextToken()
        } else if (token != CirJsonToken.PROPERTY_NAME) {
            return deserializeTypedUsingDefaultImplementation(parser, context, null, "Unexpected input")
        }

        if (token == CirJsonToken.END_OBJECT) {
            val emptySubtype = mySubtypeFingerprints[EMPTY_CLASS_FINGERPRINT]

            if (emptySubtype != null) {
                return deserializeTypedForId(parser, context, null, emptySubtype)
            }
        }

        val candidates = LinkedList(mySubtypeFingerprints.keys)

        val tokenBuffer = context.bufferForInputBuffering(parser)
        val ignoreCase = context.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)

        while (token == CirJsonToken.PROPERTY_NAME) {
            var name = parser.currentName()!!

            if (ignoreCase) {
                name = name.lowercase()
            }

            tokenBuffer.copyCurrentStructure(parser)

            val bit = myPropertyBitIndex[name]

            if (bit != null) {
                prune(candidates, bit)

                if (candidates.size == 1) {
                    return deserializeTypedForId(parser, context, tokenBuffer, mySubtypeFingerprints[candidates[0]]!!)
                }
            }

            token = parser.nextToken()
        }

        val message =
                "Cannot deduce unique subtype of ${myBaseType.typeDescription} (${candidates.size} candidates match)"
        return deserializeTypedUsingDefaultImplementation(parser, context, tokenBuffer, message)
    }

    companion object {

        private val EMPTY_CLASS_FINGERPRINT = BitSet(0)

        private fun prune(candidates: MutableList<BitSet>, bit: Int) {
            candidates.removeIf { !it[bit] }
        }

    }

}