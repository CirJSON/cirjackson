package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.type.TypeBindings
import org.cirjson.cirjackson.databind.type.TypeFactory
import java.lang.reflect.*

/**
 * Internal utility functionality to handle type resolution for method type variables based on the requested result
 * type: this is needed to work around the problem of static factory methods not being able to use class type variable
 * bindings.
 */
internal object MethodGenericTypeResolver {

    fun narrowMethodTypeParameters(candidate: Method, requestedType: KotlinType, typeFactory: TypeFactory,
            emptyTypeResolutionContext: TypeResolutionContext): TypeResolutionContext {
        val newTypeBindings = bindMethodTypeParameters(candidate, requestedType, emptyTypeResolutionContext)
                ?: return emptyTypeResolutionContext
        return TypeResolutionContext.Basic(typeFactory, newTypeBindings)
    }

    /**
     * Returns [TypeBindings] with additional type information based on `requestedType` if possible, otherwise `null`.
     */
    @Suppress("UNCHECKED_CAST")
    fun bindMethodTypeParameters(candidate: Method, requestedType: KotlinType,
            emptyTypeResolutionContext: TypeResolutionContext): TypeBindings? {
        val methodTypeParameters: Array<TypeVariable<Method>> = candidate.typeParameters

        if (methodTypeParameters.isEmpty() || requestedType.bindings.isEmpty()) {
            return null
        }

        val genericReturnType = candidate.genericReturnType

        if (genericReturnType !is ParameterizedType) {
            return null
        }

        if (requestedType.rawClass != genericReturnType.rawType) {
            return null
        }

        val methodReturnTypeArguments = genericReturnType.actualTypeArguments
        val names = ArrayList<String>(methodTypeParameters.size)
        val types = ArrayList<KotlinType>(methodTypeParameters.size)

        for (i in methodTypeParameters.indices) {
            val methodReturnTypeArgument = methodReturnTypeArguments[i]

            val typeVariable = maybeGetTypeVariable(methodReturnTypeArgument) ?: continue
            val typeParameterName = typeVariable.name ?: return null
            val bindTarget = requestedType.bindings.getBoundTypeOrNull(i) ?: return null
            val methodTypeVariable =
                    findByName(methodTypeParameters as Array<TypeVariable<*>>?, typeParameterName) ?: return null

            if (!pessimisticallyValidateBounds(emptyTypeResolutionContext, bindTarget, methodTypeVariable.bounds)) {
                continue
            }

            val existingIndex = names.indexOf(typeParameterName)

            if (existingIndex == -1) {
                names.add(typeVariable.name)
                types.add(bindTarget)
                continue
            }

            val existingBindTarget = types[existingIndex]

            if (bindTarget == existingBindTarget) {
                continue
            }

            val existingIsSubtype = existingBindTarget.isTypeOrSubTypeOf(bindTarget.rawClass)
            val newIsSubtype = bindTarget.isTypeOrSubTypeOf(existingBindTarget.rawClass)

            if (!existingIsSubtype && !newIsSubtype) {
                return null
            }

            if (newIsSubtype && !existingIsSubtype) {
                types[existingIndex] = bindTarget
            }
        }

        if (names.isEmpty()) {
            return null
        }

        return TypeBindings.create(names, types)
    }

    private fun maybeGetTypeVariable(type: Type): TypeVariable<*>? {
        if (type is TypeVariable<*>) {
            return type
        }

        if (type !is WildcardType) {
            return null
        }

        if (type.lowerBounds.isNotEmpty()) {
            return null
        }

        val upperBounds = type.upperBounds

        if (upperBounds.size == 1) {
            return maybeGetTypeVariable(upperBounds[0])
        }

        return null
    }

    private fun maybeGetParameterizedType(type: Type): ParameterizedType? {
        if (type is ParameterizedType) {
            return type
        }

        if (type !is WildcardType) {
            return null
        }

        if (type.lowerBounds.isNotEmpty()) {
            return null
        }

        val upperBounds = type.upperBounds

        if (upperBounds.size == 1) {
            return maybeGetParameterizedType(upperBounds[0])
        }

        return null
    }

    private fun pessimisticallyValidateBounds(context: TypeResolutionContext, boundType: KotlinType,
            upperBound: Array<Type>): Boolean {
        for (type in upperBound) {
            if (!pessimisticallyValidateBound(context, boundType, type)) {
                return false
            }
        }

        return true
    }

    private fun pessimisticallyValidateBound(context: TypeResolutionContext, boundType: KotlinType,
            type: Type): Boolean {
        if (!boundType.isTypeOrSubTypeOf(context.resolveType(type).rawClass)) {
            return false
        }

        val parameterized = maybeGetParameterizedType(type) ?: return true

        if (boundType.rawClass != parameterized.rawType) {
            return true
        }

        val typeArguments = parameterized.actualTypeArguments
        val bindings = boundType.bindings

        if (bindings.size != typeArguments.size) {
            return false
        }

        for (i in 0 until bindings.size) {
            val boundTypeBound = bindings.getBoundType(i)!!
            val typeArgument = typeArguments[i]

            if (!pessimisticallyValidateBound(context, boundTypeBound, typeArgument)) {
                return false
            }
        }

        return true
    }

    private fun findByName(typeVariables: Array<TypeVariable<*>>?, name: String?): TypeVariable<*>? {
        typeVariables ?: return null
        name ?: return null

        for (typeVariable in typeVariables) {
            if (name == typeVariable.name) {
                return typeVariable
            }
        }

        return null
    }

}