package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Simple helper class used to keep track of 'call stack' for classes being referenced (as well as unbound variables)
 */
class ClassStack private constructor(private val myParent: ClassStack?, private val myCurrent: KClass<*>) {

    private val mySelfReferencesInitializer = lazy { ArrayList<ResolvedRecursiveType>() }

    private val mySelfReferences by mySelfReferencesInitializer

    constructor(rootType: KClass<*>) : this(null, rootType)

    /**
     * @return New stack frame
     */
    fun child(clazz: KClass<*>): ClassStack {
        return ClassStack(this, clazz)
    }

    /**
     * Method called to indicate that there is a self-reference from deeper down in stack pointing into type this stack
     * frame represents.
     */
    fun addSelfReference(reference: ResolvedRecursiveType) {
        mySelfReferences.add(reference)
    }

    /**
     * Method called when type that this stack frame represents is fully resolved, allowing self-references to be
     * completed (if there are any)
     */
    fun resolveSelfReferences(resolved: KotlinType) {
        if (!mySelfReferencesInitializer.isInitialized()) {
            return
        }

        for (reference in mySelfReferences) {
            reference.selfReferencedType = resolved
        }
    }

    fun find(clazz: KClass<*>): ClassStack? {
        if (myCurrent == clazz) {
            return this
        }

        var current = myParent

        while (current != null) {
            if (current.myCurrent == clazz) {
                return current
            }

            current = current.myParent
        }

        return null
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("[ClassStack (self-refs: ")
                .append(if (mySelfReferencesInitializer.isInitialized()) mySelfReferences.size.toString() else "0")
                .append(')')

        var current: ClassStack? = this

        while (current != null) {
            stringBuilder.append(' ').append(current.myCurrent.qualifiedName)
        }

        stringBuilder.append(']')
        return stringBuilder.toString()
    }

}