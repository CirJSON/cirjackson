package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.util.*
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * Helper class used to contain details of how Creators (annotated constructors and static methods) are discovered to be
 * accessed by and via [AnnotatedClass].
 */
class AnnotatedCreatorCollector private constructor(config: MapperConfig<*>?, private val myPrimaryType: KotlinType,
        private val myTypeContext: TypeResolutionContext, private val myCollectAnnotations: Boolean) :
        CollectorBase(config) {

    private var myDefaultConstructor: AnnotatedConstructor? = null

    private fun collect(primaryMixIn: KClass<*>?): AnnotatedClass.Creators {
        val constructors = findPotentialConstructors(myPrimaryType, primaryMixIn)
        val factories = findPotentialFactories(myPrimaryType, primaryMixIn)

        if (!myCollectAnnotations) {
            return AnnotatedClass.Creators(myDefaultConstructor, constructors, factories)
        }

        if (myDefaultConstructor != null) {
            if (myIntrospector!!.hasIgnoreMarker(myConfig!!, myDefaultConstructor!!)) {
                myDefaultConstructor = null
            }
        }

        for (i in constructors.indices.reversed()) {
            if (myIntrospector!!.hasIgnoreMarker(myConfig!!, constructors[i])) {
                constructors.removeAt(i)
            }
        }

        for (i in factories.indices.reversed()) {
            if (myIntrospector!!.hasIgnoreMarker(myConfig!!, factories[i])) {
                factories.removeAt(i)
            }
        }

        return AnnotatedClass.Creators(myDefaultConstructor, constructors, factories)
    }

    /**
     * Helper method for locating constructors (and matching mix-in overrides) we might want to use; this is needed in
     * order to mix information between the two and construct resulting [AnnotatedConstructors][AnnotatedConstructor].
     */
    private fun findPotentialConstructors(type: KotlinType,
            primaryMixIn: KClass<*>?): MutableList<AnnotatedConstructor> {
        var defaultCtor: Ctor? = null
        var ctors: MutableList<Ctor>? = null

        if (!type.isEnumType) {
            val declaredCtors = type.rawClass.ctors

            for (ctor in declaredCtors) {
                if (!isIncludableConstructor(ctor.constructor)) {
                    continue
                }

                if (ctor.parameterCount == 0) {
                    defaultCtor = ctor
                } else {
                    if (ctors == null) {
                        ctors = ArrayList()
                    }

                    ctors.add(ctor)
                }
            }
        }

        val result: MutableList<AnnotatedConstructor?>
        val ctorCount: Int

        if (ctors == null) {
            result = ArrayList()
            defaultCtor ?: return ArrayList()
            ctorCount = 0
        } else {
            ctorCount = ctors.size
            result = ArrayList(ctorCount)

            for (i in 0 until ctorCount) {
                result.add(null)
            }
        }

        if (primaryMixIn != null) {
            var ctorKeys: Array<MemberKey>? = null

            for (mixinCtor in primaryMixIn.ctors) {
                if (mixinCtor.parameterCount == 0) {
                    if (defaultCtor != null) {
                        myDefaultConstructor = constructDefaultConstructor(defaultCtor, mixinCtor)
                        defaultCtor = null
                    }

                    continue
                }

                if (ctors == null) {
                    continue
                }

                if (ctorKeys == null) {
                    ctorKeys = Array(ctorCount) { MemberKey(ctors[it].constructor) }
                }

                val key = MemberKey(mixinCtor.constructor)

                for (i in 0 until ctorCount) {
                    if (key == ctorKeys[i]) {
                        result[i] = constructNonDefaultConstructor(ctors[i], mixinCtor)
                        break
                    }
                }
            }
        }

        if (defaultCtor != null) {
            myDefaultConstructor = constructDefaultConstructor(defaultCtor, null)
        }

        for (i in 0 until ctorCount) {
            val ctor = result[i]

            if (ctor == null) {
                result[i] = constructNonDefaultConstructor(ctors!![i], null)
            }
        }

        return result.filterNotNull().toMutableList()
    }

    private fun findPotentialFactories(type: KotlinType, primaryMixIn: KClass<*>?): MutableList<AnnotatedMethod> {
        var candidates: MutableList<Method>? = null

        for (method in type.rawClass.classMethods) {
            if (!isIncludableFactoryMethod(method)) {
                continue
            }

            if (candidates == null) {
                candidates = ArrayList()
            }

            candidates.add(method)
        }

        candidates ?: return ArrayList()

        val initialTypeResolutionContext = myTypeContext

        val factoryCount = candidates.size
        val result = ArrayList<AnnotatedMethod?>(factoryCount)

        for (i in 0 until factoryCount) {
            result.add(null)
        }

        if (primaryMixIn != null) {
            var methodKeys: Array<MemberKey>? = null

            for (mixinFactory in primaryMixIn.declaredFunctions.mapNotNull { it.javaMethod }) {
                if (!isIncludableFactoryMethod(mixinFactory)) {
                    continue
                }

                if (methodKeys == null) {
                    methodKeys = Array(factoryCount) { MemberKey(candidates[it]) }
                }

                val key = MemberKey(mixinFactory)

                for (i in 0 until factoryCount) {
                    if (key == methodKeys[i]) {
                        result[i] = constructFactoryCreator(candidates[i], initialTypeResolutionContext, mixinFactory)
                        break
                    }
                }
            }
        }

        for (i in 0 until factoryCount) {
            val factory = result[i]

            if (factory != null) {
                continue
            }

            val candidate = candidates[i]
            val typeResolutionContext =
                    MethodGenericTypeResolver.narrowMethodTypeParameters(candidate, type, myConfig!!.typeFactory,
                            initialTypeResolutionContext)
            result[i] = constructFactoryCreator(candidate, typeResolutionContext, null)
        }

        return result.filterNotNull().toMutableList()
    }

    private fun constructDefaultConstructor(ctor: Ctor, mixin: Ctor?): AnnotatedConstructor {
        return AnnotatedConstructor(myTypeContext, ctor.constructor.kotlinFunction!!, collectAnnotations(ctor, mixin),
                NO_ANNOTATION_MAPS.nullable())
    }

    private fun constructNonDefaultConstructor(ctor: Ctor, mixin: Ctor?): AnnotatedConstructor {
        val paramCount = ctor.parameterCount

        if (!myCollectAnnotations) {
            return AnnotatedConstructor(myTypeContext, ctor.constructor.kotlinFunction!!, emptyAnnotationMap(),
                    emptyAnnotationMaps(paramCount).nullable())
        }

        if (paramCount == 0) {
            return AnnotatedConstructor(myTypeContext, ctor.constructor.kotlinFunction!!,
                    collectAnnotations(ctor, mixin), NO_ANNOTATION_MAPS.nullable())
        }

        var resolvedAnnotations: Array<AnnotationMap>? = null
        var paramAnnotations = ctor.parameterAnnotations

        if (paramCount != paramAnnotations.size) {
            val declaringClass = ctor.declaringClass

            if (declaringClass.isEnumType && paramCount == paramAnnotations.size + 2) {
                val old = paramAnnotations
                paramAnnotations = Array(old.size + 2) { emptyArray() }
                old.copyInto(paramAnnotations, 2)
                resolvedAnnotations = collectAnnotations(paramAnnotations, null)
            } else if (declaringClass.java.isMemberClass && paramCount == paramAnnotations.size + 1) {
                val old = paramAnnotations
                paramAnnotations = Array(old.size + 1) { emptyArray() }
                old.copyInto(paramAnnotations, 1)
                paramAnnotations[0] = NO_ANNOTATIONS
                resolvedAnnotations = collectAnnotations(paramAnnotations, null)
            }

            resolvedAnnotations ?: throw IllegalStateException(
                    "Internal error: constructor for $declaringClass has mismatch: $paramCount parameters; ${paramAnnotations.size} sets of annotations")
        } else {
            resolvedAnnotations = collectAnnotations(paramAnnotations, mixin?.parameterAnnotations)
        }

        return AnnotatedConstructor(myTypeContext, ctor.constructor.kotlinFunction!!, collectAnnotations(ctor, mixin),
                resolvedAnnotations.nullable())
    }

    private fun constructFactoryCreator(method: Method, typeResolutionContext: TypeResolutionContext,
            mixin: Method?): AnnotatedMethod {
        val paramCount = method.parameterCount

        if (!myCollectAnnotations) {
            return AnnotatedMethod(typeResolutionContext, method.kotlinFunction!!, emptyAnnotationMap(),
                    emptyAnnotationMaps(paramCount).nullable())
        }

        if (paramCount == 0) {
            return AnnotatedMethod(typeResolutionContext, method.kotlinFunction!!,
                    collectAnnotations(method.kotlinFunction!!, mixin?.kotlinFunction), NO_ANNOTATION_MAPS.nullable())
        }

        return AnnotatedMethod(typeResolutionContext, method.kotlinFunction!!,
                collectAnnotations(method.kotlinFunction!!, mixin?.kotlinFunction),
                collectAnnotations(collectAnnotations(method)!!, collectAnnotations(mixin)).nullable())
    }

    private fun collectAnnotations(mainAnnotations: Array<Array<Annotation>>,
            mixinAnnotations: Array<Array<Annotation>>?): Array<AnnotationMap> {
        if (!myCollectAnnotations) {
            return NO_ANNOTATION_MAPS
        }

        val count = mainAnnotations.size
        return Array(count) {
            var collector = collectAnnotations(AnnotationCollector.EMPTY_COLLECTOR, mainAnnotations[it])

            if (mixinAnnotations != null) {
                collector = collectAnnotations(collector, mixinAnnotations[it])
            }

            collector.asAnnotationMap()
        }
    }

    private fun collectAnnotations(main: Ctor, mixin: Ctor?): AnnotationMap {
        if (!myCollectAnnotations) {
            return emptyAnnotationMap()
        }

        var collector = collectAnnotations(main.declaredAnnotations)

        if (mixin != null) {
            collector = collectAnnotations(collector, mixin.declaredAnnotations)
        }

        return collector.asAnnotationMap()
    }

    private fun collectAnnotations(main: KAnnotatedElement, mixin: KAnnotatedElement?): AnnotationMap {
        var collector = collectAnnotations(main.annotations.toTypedArray())

        if (mixin != null) {
            collector = collectAnnotations(collector, mixin.annotations.toTypedArray())
        }

        return collector.asAnnotationMap()
    }

    companion object {

        fun collectCreators(config: MapperConfig<*>?, context: TypeResolutionContext, type: KotlinType,
                primaryMixIn: KClass<*>?, collectAnnotations: Boolean): AnnotatedClass.Creators {
            return AnnotatedCreatorCollector(config, type, context, collectAnnotations || primaryMixIn != null).collect(
                    primaryMixIn)
        }

        private fun isIncludableFactoryMethod(method: Method): Boolean {
            if (!Modifier.isStatic(method.modifiers)) {
                return false
            }

            return !method.isSynthetic
        }

        private fun isIncludableConstructor(constructor: Constructor<*>): Boolean {
            return !constructor.isSynthetic
        }

        private fun collectAnnotations(method: Method?): Array<Array<Annotation>>? {
            return method?.kotlinFunction?.parameters?.map { it.annotations.toTypedArray() }?.toTypedArray()
        }

    }

}