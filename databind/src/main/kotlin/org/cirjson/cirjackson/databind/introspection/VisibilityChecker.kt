package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonAutoDetect
import org.cirjson.cirjackson.annotations.CirJsonAutoDetect.Visibility
import org.cirjson.cirjackson.annotations.PropertyAccessor

/**
 * Interface for object used for determine which property elements (methods, fields, constructors) can be auto-detected,
 * with respect to their visibility modifiers.
 */
open class VisibilityChecker {

    protected val myFieldMinLevel: Visibility

    protected val myGetterMinLevel: Visibility

    protected val myIsGetterMinLevel: Visibility

    protected val mySetterMinLevel: Visibility

    protected val myCreatorMinLevel: Visibility

    protected val myScalarConstructorMinLevel: Visibility

    /**
     * Constructor used for building instance that has minimum visibility levels as indicated by given annotation
     * instance
     *
     * @param annotation Annotations to use for determining minimum visibility levels
     */
    constructor(annotation: CirJsonAutoDetect) {
        myFieldMinLevel = annotation.fieldVisibility
        myGetterMinLevel = annotation.getterVisibility
        myIsGetterMinLevel = annotation.isGetterVisibility
        mySetterMinLevel = annotation.setterVisibility
        myCreatorMinLevel = annotation.creatorVisibility
        myScalarConstructorMinLevel = annotation.scalarConstructorVisibility
    }

    /**
     * Constructor that allows directly specifying minimum visibility levels to use
     */
    constructor(field: Visibility, getter: Visibility, isGetter: Visibility, setter: Visibility, creator: Visibility,
            scalarConstructor: Visibility) {
        myFieldMinLevel = field
        myGetterMinLevel = getter
        myIsGetterMinLevel = isGetter
        mySetterMinLevel = setter
        myCreatorMinLevel = creator
        myScalarConstructorMinLevel = scalarConstructor
    }

    /**
     * Constructor that will assign given visibility value for all properties.
     *
     * @param visibility level to use for all property types
     */
    constructor(visibility: Visibility) {
        if (visibility == Visibility.DEFAULT) {
            myFieldMinLevel = DEFAULT.myFieldMinLevel
            myGetterMinLevel = DEFAULT.myGetterMinLevel
            myIsGetterMinLevel = DEFAULT.myIsGetterMinLevel
            mySetterMinLevel = DEFAULT.mySetterMinLevel
            myCreatorMinLevel = DEFAULT.myCreatorMinLevel
            myScalarConstructorMinLevel = DEFAULT.myScalarConstructorMinLevel
        } else {
            myFieldMinLevel = visibility
            myGetterMinLevel = visibility
            myIsGetterMinLevel = visibility
            mySetterMinLevel = visibility
            myCreatorMinLevel = visibility
            myScalarConstructorMinLevel = visibility
        }
    }

    /*
     *******************************************************************************************************************
     * Mutant factories
     *******************************************************************************************************************
     */

    open fun withOverrides(visibility: CirJsonAutoDetect.Value?): VisibilityChecker {
        visibility ?: return this
        return with(defaultOrOverride(myFieldMinLevel, visibility.fieldVisibility),
                defaultOrOverride(myGetterMinLevel, visibility.getterVisibility),
                defaultOrOverride(myIsGetterMinLevel, visibility.isGetterVisibility),
                defaultOrOverride(mySetterMinLevel, visibility.setterVisibility),
                defaultOrOverride(myCreatorMinLevel, visibility.creatorVisibility),
                defaultOrOverride(myScalarConstructorMinLevel, visibility.scalarConstructorVisibility))
    }

    private fun defaultOrOverride(defaults: Visibility, overrides: Visibility): Visibility {
        if (overrides == Visibility.DEFAULT) {
            return defaults
        }

        return overrides
    }

    /**
     * Builder method that will create and return an instance that has specified [Visibility] value to use for all
     * property elements. Typical usage would be something like:
     * ```
     *  mapper.visibilityChecker = mapper.visibilityChecker.with(Visibility.NONE)
     * ```
     * (which would basically disable all auto-detection)
     */
    open fun with(visibility: Visibility): VisibilityChecker {
        if (visibility == Visibility.DEFAULT) {
            return DEFAULT
        }

        return VisibilityChecker(visibility)
    }

    /**
     * Builder method that will create and return an instance that has specified [Visibility] value to use for specified
     * property. Typical usage would be:
     * ```
     *  mapper.visibilityChecker = mapper.visibilityChecker.withVisibility(PropertyAccessor.FIELD, Visibility.ANY)
     * ```
     * (which would basically enable auto-detection for all member fields)
     */
    open fun withVisibility(method: PropertyAccessor, visibility: Visibility): VisibilityChecker {
        return when (method) {
            PropertyAccessor.GETTER -> withGetterVisibility(visibility)
            PropertyAccessor.SETTER -> withSetterVisibility(visibility)
            PropertyAccessor.CREATOR -> withCreatorVisibility(visibility)
            PropertyAccessor.FIELD -> withFieldVisibility(visibility)
            PropertyAccessor.IS_GETTER -> withIsGetterVisibility(visibility)
            PropertyAccessor.ALL -> with(visibility)
            else -> this
        }
    }

    /**
     * Builder method that will return a checker instance that has specified minimum visibility level for fields.
     */
    open fun withFieldVisibility(visibility: Visibility): VisibilityChecker {
        val realVisibility = visibility.takeUnless { it == Visibility.DEFAULT } ?: DEFAULT.myFieldMinLevel

        if (myFieldMinLevel == realVisibility) {
            return this
        }

        return VisibilityChecker(realVisibility, myGetterMinLevel, myIsGetterMinLevel, mySetterMinLevel,
                myCreatorMinLevel, myScalarConstructorMinLevel)
    }

    /**
     * Builder method that will return a checker instance that has specified minimum visibility level for regular
     * ("getXxx") getters.
     */
    open fun withGetterVisibility(visibility: Visibility): VisibilityChecker {
        val realVisibility = visibility.takeUnless { it == Visibility.DEFAULT } ?: DEFAULT.myGetterMinLevel

        if (myGetterMinLevel == realVisibility) {
            return this
        }

        return VisibilityChecker(myFieldMinLevel, realVisibility, myIsGetterMinLevel, mySetterMinLevel,
                myCreatorMinLevel, myScalarConstructorMinLevel)
    }

    /**
     * Builder method that will return a checker instance that has specified minimum visibility level for "is-getters"
     * ("isXxx").
     */
    open fun withIsGetterVisibility(visibility: Visibility): VisibilityChecker {
        val realVisibility = visibility.takeUnless { it == Visibility.DEFAULT } ?: DEFAULT.myIsGetterMinLevel

        if (myIsGetterMinLevel == realVisibility) {
            return this
        }

        return VisibilityChecker(myFieldMinLevel, myGetterMinLevel, realVisibility, mySetterMinLevel, myCreatorMinLevel,
                myScalarConstructorMinLevel)
    }

    /**
     * Builder method that will return a checker instance that has specified minimum visibility level for setters.
     */
    open fun withSetterVisibility(visibility: Visibility): VisibilityChecker {
        val realVisibility = visibility.takeUnless { it == Visibility.DEFAULT } ?: DEFAULT.mySetterMinLevel

        if (mySetterMinLevel == realVisibility) {
            return this
        }

        return VisibilityChecker(myFieldMinLevel, myGetterMinLevel, myIsGetterMinLevel, realVisibility,
                myCreatorMinLevel, myScalarConstructorMinLevel)
    }

    /**
     * Builder method that will return a checker instance that has specified minimum visibility level for creator
     * methods (constructors, factory methods)
     */
    open fun withCreatorVisibility(visibility: Visibility): VisibilityChecker {
        val realVisibility = visibility.takeUnless { it == Visibility.DEFAULT } ?: DEFAULT.myCreatorMinLevel

        if (myCreatorMinLevel == realVisibility) {
            return this
        }

        return VisibilityChecker(myFieldMinLevel, myGetterMinLevel, myIsGetterMinLevel, mySetterMinLevel,
                realVisibility, myScalarConstructorMinLevel)
    }

    open fun withScalarConstructorVisibility(visibility: Visibility): VisibilityChecker {
        val realVisibility = visibility.takeUnless { it == Visibility.DEFAULT } ?: DEFAULT.myScalarConstructorMinLevel

        if (myScalarConstructorMinLevel == realVisibility) {
            return this
        }

        return VisibilityChecker(myFieldMinLevel, myGetterMinLevel, myIsGetterMinLevel, mySetterMinLevel,
                myCreatorMinLevel, realVisibility)
    }

    /**
     * Method that can be used for merging default values from `this` instance with specified overrides; and either
     * return `this` if overrides had no effect (that is, result would be equal), or a new instance with merged
     * visibility settings.
     */
    protected open fun with(field: Visibility, getter: Visibility, isGetter: Visibility, setter: Visibility,
            creator: Visibility, scalarConstructor: Visibility): VisibilityChecker {
        if (myFieldMinLevel == field && myGetterMinLevel == getter && myIsGetterMinLevel == isGetter &&
                mySetterMinLevel == setter && myCreatorMinLevel == creator &&
                myScalarConstructorMinLevel == scalarConstructor) {
            return this
        }

        return VisibilityChecker(field, getter, isGetter, setter, creator, scalarConstructor)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    /**
     * Method for checking whether given field is auto-detectable as property, with respect to its visibility (not
     * considering method signature or name, just visibility)
     */
    open fun isFieldVisible(method: AnnotatedField): Boolean {
        return myFieldMinLevel.isVisible(method.annotated)
    }

    /**
     * Method for checking whether given method is auto-detectable as regular getter, with respect to its visibility
     * (not considering method signature or name, just visibility)
     */
    open fun isGetterVisible(method: AnnotatedMethod): Boolean {
        return myGetterMinLevel.isVisible(method.annotated)
    }

    /**
     * Method for checking whether given method is auto-detectable as is-getter, with respect to its visibility (not
     * considering method signature or name, just visibility)
     */
    open fun isIsGetterVisible(method: AnnotatedMethod): Boolean {
        return myIsGetterMinLevel.isVisible(method.annotated)
    }

    /**
     * Method for checking whether given method is auto-detectable as setter, with respect to its visibility (not
     * considering method signature or name, just visibility)
     */
    open fun isSetterVisible(method: AnnotatedMethod): Boolean {
        return mySetterMinLevel.isVisible(method.annotated)
    }

    /**
     * Method for checking whether given creator (other than "scalar constructor", see [isScalarConstructorVisible]) is
     * auto-detectable as Creator, with respect to its visibility (not considering signature, just visibility)
     */
    open fun isCreatorVisible(method: AnnotatedMethod): Boolean {
        return myCreatorMinLevel.isVisible(method.annotated)
    }

    /**
     * Method for checking whether given single-scalar-argument constructor is auto-detectable as delegating Creator,
     * with respect to its visibility (not considering signature, just visibility)
     */
    open fun isScalarConstructorVisible(method: AnnotatedMethod): Boolean {
        return myScalarConstructorMinLevel.isVisible(method.annotated)
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[Visibility: field=$myFieldMinLevel,getter=$myGetterMinLevel,isGetter=$myIsGetterMinLevel,setter=$mySetterMinLevel,creator=$myCreatorMinLevel,scalarConstructor=$myScalarConstructorMinLevel]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is VisibilityChecker) {
            return false
        }

        return myFieldMinLevel == other.myFieldMinLevel && myGetterMinLevel == other.myGetterMinLevel &&
                myIsGetterMinLevel == other.myIsGetterMinLevel && mySetterMinLevel == other.mySetterMinLevel &&
                myCreatorMinLevel == other.myCreatorMinLevel &&
                myScalarConstructorMinLevel == other.myScalarConstructorMinLevel
    }

    override fun hashCode(): Int {
        var result = myFieldMinLevel.hashCode()
        result = (result shl 1) + myGetterMinLevel.hashCode()
        result = (result shl 1) + myIsGetterMinLevel.hashCode()
        result = (result shl 1) + mySetterMinLevel.hashCode()
        result = (result shl 1) + myCreatorMinLevel.hashCode()
        result = (result shl 1) + myScalarConstructorMinLevel.hashCode()
        return result
    }

    companion object {

        /**
         * This is the canonical base instance, configured with default visibility values
         */
        val DEFAULT = VisibilityChecker(Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY,
                Visibility.ANY, Visibility.PUBLIC_ONLY, Visibility.NON_PRIVATE)

        val ALL_PUBLIC = VisibilityChecker(Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY,
                Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY)

        /**
         * Visibility settings needed to support auto-discovery of non-private Records.
         */
        val ALL_PUBLIC_EXCEPT_CREATORS =
                VisibilityChecker(Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY,
                        Visibility.PUBLIC_ONLY, Visibility.ANY, Visibility.ANY)

        fun construct(visibility: CirJsonAutoDetect.Value?): VisibilityChecker {
            return DEFAULT.withOverrides(visibility)
        }

    }

}