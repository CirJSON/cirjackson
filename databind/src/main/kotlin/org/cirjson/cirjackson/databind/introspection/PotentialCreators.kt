package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.configuration.MapperConfig

open class PotentialCreators {

    /**
     * Property-based Creator found, if any
     */
    var propertiesBased: PotentialCreator? = null

    private var myExplicitDelegating: MutableList<PotentialCreator>? = null

    private var myImplicitDelegatingConstructors: List<PotentialCreator>? = null

    private var myImplicitDelegatingFactories: List<PotentialCreator>? = null

    /*
     *******************************************************************************************************************
     * Accumulating candidates
     *******************************************************************************************************************
     */

    open fun setPropertiesBased(config: MapperConfig<*>, creator: PotentialCreator, mode: String) {
        if (propertiesBased != null) {
            throw IllegalArgumentException(
                    "Conflicting property-based creators: already had $mode creator ${propertiesBased!!.creator()}, encountered another: ${creator.creator()}")
        }

        propertiesBased = creator.introspectParameterNames(config)
    }

    open fun addExplicitDelegating(creator: PotentialCreator) {
        if (myExplicitDelegating == null) {
            myExplicitDelegating = ArrayList()
        }

        myExplicitDelegating!!.add(creator)
    }

    open fun setImplicitDelegating(implicitConstructors: List<PotentialCreator>,
            implicitFactories: List<PotentialCreator>) {
        myImplicitDelegatingConstructors = implicitConstructors
        myImplicitDelegatingFactories = implicitFactories
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open fun hasPropertiesBased(): Boolean {
        return propertiesBased != null
    }

    open fun hasPropertiesBasedOrDelegating(): Boolean {
        return propertiesBased != null || !myExplicitDelegating.isNullOrEmpty()
    }

    open val explicitDelegating: List<PotentialCreator>
        get() = myExplicitDelegating ?: emptyList()

    open val implicitDelegatingFactories: List<PotentialCreator>
        get() = myImplicitDelegatingFactories ?: emptyList()

    open val implicitDelegatingConstructors: List<PotentialCreator>
        get() = myImplicitDelegatingConstructors ?: emptyList()

}