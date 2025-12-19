package org.cirjson.cirjackson.databind.cirjson

import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.cirjson.CirJsonWriteFeature
import org.cirjson.cirjackson.databind.ObjectMapper
import org.cirjson.cirjackson.databind.cirjson.CirJsonMapper.Companion.shared
import org.cirjson.cirjackson.databind.configuration.MapperBuilder
import org.cirjson.cirjackson.databind.configuration.MapperBuilderState
import org.cirjson.cirjackson.databind.configuration.PackageVersion

/**
 * CirJSON-specific [ObjectMapper] implementation.
 */
open class CirJsonMapper(builder: Builder) : ObjectMapper(builder) {

    /*
     *******************************************************************************************************************
     * Lifecycle, constructors
     *******************************************************************************************************************
     */

    constructor() : this(CirJsonFactory())

    constructor(factory: CirJsonFactory) : this(Builder(factory))

    /*
     *******************************************************************************************************************
     * Lifecycle, builders
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override fun <M : ObjectMapper, B : MapperBuilder<M, B>> rebuild(): MapperBuilder<M, B> {
        return Builder(mySavedBuilderState as Builder.StateImplementation) as MapperBuilder<M, B>
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */
    override fun version(): Version {
        return PackageVersion.VERSION
    }

    override fun tokenStreamFactory(): CirJsonFactory {
        return myStreamFactory as CirJsonFactory
    }

    /*
     *******************************************************************************************************************
     * Format-specific
     *******************************************************************************************************************
     */

    open fun isEnabled(feature: CirJsonReadFeature): Boolean {
        return myDeserializationConfig.hasFormatFeature(feature)
    }

    open fun isEnabled(feature: CirJsonWriteFeature): Boolean {
        return mySerializationConfig.hasFormatFeature(feature)
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Base implementation for "Vanilla" [ObjectMapper], used with CirJSON dataformat backend.
     */
    open class Builder : MapperBuilder<CirJsonMapper, Builder> {

        constructor(factory: CirJsonFactory) : super(factory)

        constructor(state: StateImplementation) : super(state)

        override fun build(): CirJsonMapper {
            return CirJsonMapper(this)
        }

        override fun saveState(): MapperBuilderState {
            return StateImplementation(this)
        }

        /*
         ***************************************************************************************************************
         * Format features
         ***************************************************************************************************************
         */

        open fun enable(vararg features: CirJsonReadFeature): Builder {
            for (feature in features) {
                myFormatReadFeatures = myFormatReadFeatures or feature.mask
            }

            return this
        }

        open fun disable(vararg features: CirJsonReadFeature): Builder {
            for (feature in features) {
                myFormatReadFeatures = myFormatReadFeatures and feature.mask.inv()
            }

            return this
        }

        open fun configure(feature: CirJsonReadFeature, state: Boolean): Builder {
            myFormatReadFeatures = if (state) {
                myFormatReadFeatures or feature.mask
            } else {
                myFormatReadFeatures and feature.mask.inv()
            }

            return this
        }

        open fun enable(vararg features: CirJsonWriteFeature): Builder {
            for (feature in features) {
                myFormatWriteFeatures = myFormatWriteFeatures or feature.mask
            }

            return this
        }

        open fun disable(vararg features: CirJsonWriteFeature): Builder {
            for (feature in features) {
                myFormatWriteFeatures = myFormatWriteFeatures and feature.mask.inv()
            }

            return this
        }

        open fun configure(feature: CirJsonWriteFeature, state: Boolean): Builder {
            myFormatWriteFeatures = if (state) {
                myFormatWriteFeatures or feature.mask
            } else {
                myFormatWriteFeatures and feature.mask.inv()
            }

            return this
        }

        class StateImplementation(builder: Builder) : MapperBuilderState(builder)

    }

    /**
     * Helper class to contain dynamically constructed "shared" instance of mapper, should one be needed via [shared].
     */
    private class SharedWrapper {

        companion object {

            val WRAPPED_MAPPER = builder().build()

        }

    }

    companion object {

        /*
         ***************************************************************************************************************
         * Lifecycle, builders
         ***************************************************************************************************************
         */

        fun builder(): Builder {
            return Builder(CirJsonFactory())
        }

        fun builder(factory: CirJsonFactory): Builder {
            return Builder(factory)
        }

        /*
         ***************************************************************************************************************
         * Lifecycle, shared "vanilla" (default configuration) instance
         ***************************************************************************************************************
         */

        /**
         * Accessor method for getting globally shared "default" [CirJsonMapper] instance: one that has default
         * configuration, no modules registered, no config overrides. Usable mostly when dealing "untyped" or Tree-style
         * content reading and writing.
         */
        fun shared(): CirJsonMapper {
            return SharedWrapper.WRAPPED_MAPPER
        }

    }

}