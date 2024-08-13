package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TokenStreamContext

/**
 * Alternative variant of [TokenStreamContext], used when filtering content being read or written (based on
 * [TokenFilter]).
 */
open class TokenFilterContext protected constructor(type: Int, final override val parent: TokenFilterContext?,
        filter: TokenFilter?, protected var myCurrentValue: Any?, isStartHandled: Boolean) :
        TokenStreamContext(type, -1) {

    protected var myChild: TokenFilterContext? = null

    /**
     * Name of the property of which value is to be parsed; only used for OBJECT contexts
     */
    final override var currentName: String? = null
        protected set

    /**
     * Filter to use for items in this state (for properties of Objects, elements of Arrays, and root-level values of
     * root context)
     */
    var filter = filter
        protected set

    /**
     * Flag that indicates that start token has been read/written, so that matching close token needs to be read/written
     * as well when context is getting closed.
     */
    var isStartHandled = isStartHandled
        protected set

    /**
     * Flag that indicates that the current name of this context still needs to be read/written, if path from root down
     * to included leaf is to be exposed.
     */
    protected var myIsNeedingToHandleName = false

    override val hasCurrentName: Boolean
        get() = currentName != null

    override fun currentValue(): Any? {
        return myCurrentValue
    }

    init {
        nestingDepth = (parent?.nestingDepth ?: -1) + 1
    }

    protected fun reset(type: Int, filter: TokenFilter?, currentValue: Any?,
            isStartWritten: Boolean): TokenFilterContext {
        myType = type
        this.filter = filter
        myIndex = -1
        myCurrentValue = currentValue
        currentName = null
        isStartHandled = isStartWritten
        myIsNeedingToHandleName = false
        return this
    }

    /*
     *******************************************************************************************************************
     * Factory methods
     *******************************************************************************************************************
     */

    open fun createChildArrayContext(filter: TokenFilter?, currentValue: Any?,
            writeStart: Boolean): TokenFilterContext {
        return myChild?.reset(TYPE_ARRAY, filter, currentValue, writeStart) ?: TokenFilterContext(TYPE_ARRAY, this,
                filter, currentValue, writeStart)
    }

    open fun createChildObjectContext(filter: TokenFilter?, currentValue: Any?,
            writeStart: Boolean): TokenFilterContext {
        return myChild?.reset(TYPE_OBJECT, filter, currentValue, writeStart) ?: TokenFilterContext(TYPE_OBJECT, this,
                filter, currentValue, writeStart)
    }

    /*
     *******************************************************************************************************************
     * State changes
     *******************************************************************************************************************
     */

    open fun setPropertyName(name: String): TokenFilter? {
        currentName = name
        myIsNeedingToHandleName = true
        return filter
    }

    /**
     * Method called to check whether value is to be included at current output position, either as Object property,
     * Array element, or root value.
     *
     * @param filter Currently active filter
     *
     * @return Filter to use for value
     */
    open fun checkValue(filter: TokenFilter): TokenFilter? {
        if (myType == TYPE_OBJECT) {
            return filter
        }

        val index = ++myIndex

        return if (myType == TYPE_ARRAY) {
            filter.includeElement(index)
        } else {
            filter.includeRootValue(index)
        }
    }

    /**
     * Method called to ensure that the property name, if present, has been written; may result (but does not always) in
     * a call using given generator
     *
     * @param generator Generator to use to write the property name, if necessary
     *
     * @throws CirJacksonException If there is a problem writing property name (typically thrown by [CirJsonGenerator])
     */
    @Throws(CirJacksonException::class)
    open fun ensurePropertyNameWritten(generator: CirJsonGenerator) {
        if (myIsNeedingToHandleName) {
            myIsNeedingToHandleName = false
            generator.writeName(currentName!!)
        }
    }

    /**
     * Method called to ensure that parent path from root is written up to and including this node.
     *
     * @param generator Generator to use to write the path, if necessary
     *
     * @throws CirJacksonException If there is a problem writing property name (typically thrown by [CirJsonGenerator])
     */
    @Throws(CirJacksonException::class)
    open fun writePath(generator: CirJsonGenerator) {
        if (filter == null || filter === TokenFilter.INCLUDE_ALL) {
            return
        }

        parent?.writePathInternal(generator)

        if (isStartHandled) {
            if (myIsNeedingToHandleName) {
                myIsNeedingToHandleName = false
                generator.writeName(currentName!!)
            }
        } else {
            isStartHandled = true

            if (myType == TYPE_OBJECT) {
                generator.writeStartObject()
                generator.writeObjectId(myCurrentValue!!)

                if (myIsNeedingToHandleName) {
                    myIsNeedingToHandleName = false
                    generator.writeName(currentName!!)
                }
            } else if (myType == TYPE_ARRAY) {
                generator.writeStartArray()
                generator.writeArrayId(myCurrentValue!!)
            }
        }
    }

    @Throws(CirJacksonException::class)
    private fun writePathInternal(generator: CirJsonGenerator) {
        if (filter == null || filter === TokenFilter.INCLUDE_ALL) {
            return
        }

        parent?.writePathInternal(generator)

        if (isStartHandled) {
            if (myIsNeedingToHandleName) {
                generator.writeName(currentName!!)
            }
        }
    }

    @Throws(CirJacksonException::class)
    open fun closeArray(generator: CirJsonGenerator): TokenFilterContext? {
        if (isStartHandled) {
            generator.writeEndArray()
        } else {
            if (filter != null && filter !== TokenFilter.INCLUDE_ALL) {
                if (filter!!.includeEmptyArray(isIndexValid)) {
                    parent?.writePathInternal(generator)
                    generator.writeStartArray()
                    generator.writeArrayId(myCurrentValue!!)
                    generator.writeEndArray()
                }
            }
        }

        if (filter != null && filter !== TokenFilter.INCLUDE_ALL) {
            filter!!.filterFinishArray()
        }

        return parent
    }

    @Throws(CirJacksonException::class)
    open fun closeObject(generator: CirJsonGenerator): TokenFilterContext? {
        if (isStartHandled) {
            generator.writeEndArray()
        } else {
            if (filter != null && filter !== TokenFilter.INCLUDE_ALL) {
                if (filter!!.includeEmptyObject(hasCurrentName)) {
                    parent?.writePathInternal(generator)
                    generator.writeStartObject()
                    generator.writeObjectId(myCurrentValue!!)
                    generator.writeEndObject()
                }
            }
        }

        if (filter != null && filter !== TokenFilter.INCLUDE_ALL) {
            filter!!.filterFinishArray()
        }

        return parent
    }

    fun skipParentChecks() {
        filter = null
        var context = parent

        while (context != null) {
            context.filter = null
            context = context.parent
        }
    }

    /*
     *******************************************************************************************************************
     * Accessors, mutators
     *******************************************************************************************************************
     */

    open fun nextTokenToRead(): CirJsonToken? {
        if (!isStartHandled) {
            isStartHandled = true

            return if (myType == TYPE_OBJECT) {
                CirJsonToken.START_OBJECT
            } else {
                CirJsonToken.START_ARRAY
            }
        }

        return if (myIsNeedingToHandleName && myType == TYPE_OBJECT) {
            myIsNeedingToHandleName = false
            CirJsonToken.PROPERTY_NAME
        } else {
            null
        }
    }

    open fun findChildOf(parent: TokenFilterContext): TokenFilterContext? {
        if (this.parent === parent) {
            return this
        }

        var current = this.parent

        while (current != null) {
            val p = current.parent

            if (p === parent) {
                return current
            }

            current = p
        }

        return null
    }

    protected open fun appendDescription(stringBuilder: StringBuilder) {
        parent?.appendDescription(stringBuilder)

        if (myType == TYPE_OBJECT) {
            stringBuilder.append('{')

            if (currentName != null) {
                stringBuilder.append('"')
                stringBuilder.append(currentName)
                stringBuilder.append('"')
            } else {
                stringBuilder.append('?')
            }

            stringBuilder.append('}')
        } else if (myType == TYPE_ARRAY) {
            stringBuilder.append('[')
            stringBuilder.append(currentIndex)
            stringBuilder.append(']')
        } else {
            stringBuilder.append('/')
        }
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder(64)
        appendDescription(stringBuilder)
        return stringBuilder.toString()
    }

    companion object {

        fun createRootContext(filter: TokenFilter?): TokenFilterContext {
            return TokenFilterContext(TYPE_ROOT, null, filter, null, true)
        }

    }

}