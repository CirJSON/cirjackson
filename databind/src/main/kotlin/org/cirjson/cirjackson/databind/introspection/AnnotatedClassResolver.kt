package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.type.TypeBindings
import org.cirjson.cirjackson.databind.util.Annotations
import org.cirjson.cirjackson.databind.util.findSuperClasses
import org.cirjson.cirjackson.databind.util.isArray
import org.cirjson.cirjackson.databind.util.isJdkClass
import kotlin.reflect.KClass

open class AnnotatedClassResolver {

    private val myConfig: MapperConfig<*>

    private val myIntrospector: AnnotationIntrospector?

    private val myMixInResolver: MixInResolver?

    private val myBindings: TypeBindings

    private val myType: KotlinType?

    private val myClass: KClass<*>

    private val myPrimaryMixin: KClass<*>?

    private val myCollectAnnotations: Boolean

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    internal constructor(config: MapperConfig<*>, type: KotlinType, resolver: MixInResolver?) {
        myConfig = config
        myType = type
        myClass = type.rawClass
        myMixInResolver = resolver
        myBindings = type.bindings
        myIntrospector = config.takeIf { it.isAnnotationProcessingEnabled }?.annotationIntrospector
        myPrimaryMixin = resolver?.findMixInClassFor(myClass)

        myCollectAnnotations = myIntrospector != null && (myClass.isJdkClass || !myType.isContainerType)
    }

    internal constructor(config: MapperConfig<*>, clazz: KClass<*>, resolver: MixInResolver?) {
        myConfig = config
        myType = null
        myClass = clazz
        myMixInResolver = resolver
        myBindings = TypeBindings.EMPTY
        myIntrospector = config.takeIf { it.isAnnotationProcessingEnabled }?.annotationIntrospector
        myPrimaryMixin = resolver?.findMixInClassFor(myClass)

        myCollectAnnotations = myIntrospector != null
    }

    /*
     *******************************************************************************************************************
     * Main resolution methods
     *******************************************************************************************************************
     */

    internal fun resolveFully(): AnnotatedClass {
        val superTypes: List<KotlinType>

        if (!myCollectAnnotations && (myClass == CLASS_LIST || myClass == CLASS_MAP)) {
            superTypes = emptyList()
        } else {
            superTypes = ArrayList(8)

            if (myType!!.isInterface) {
                addSuperInterfaces(myType, superTypes, false)
            } else if (!myType.hasRawClass(CLASS_OBJECT)) {
                addSuperTypes(myType, superTypes, false)
            }
        }

        return AnnotatedClass(myConfig, myType, myClass, superTypes, myPrimaryMixin,
                resolveClassAnnotations(superTypes), myBindings, myMixInResolver, myCollectAnnotations)
    }

    internal fun resolveWithoutSuperTypes(): AnnotatedClass {
        val superTypes = emptyList<KotlinType>()
        return AnnotatedClass(myConfig, null, myClass, superTypes, myPrimaryMixin, resolveClassAnnotations(superTypes),
                myBindings, myMixInResolver, myCollectAnnotations)
    }

    /*
     *******************************************************************************************************************
     * Class annotation resolution
     *******************************************************************************************************************
     */

    /**
     * Initialization method that will recursively collect Jackson annotations for this class and all super classes and
     * interfaces.
     */
    private fun resolveClassAnnotations(superTypes: List<KotlinType>): Annotations {
        myIntrospector ?: return EMPTY_ANNOTATIONS

        val checkMixIns = myMixInResolver?.hasMixIns() ?: false

        if (!checkMixIns && !myCollectAnnotations) {
            return EMPTY_ANNOTATIONS
        }

        var resolvedCollector = AnnotationCollector.EMPTY_COLLECTOR

        if (myPrimaryMixin != null) {
            resolvedCollector = addClassMixIns(resolvedCollector, myClass, myPrimaryMixin)
        }

        if (myCollectAnnotations) {
            resolvedCollector = addAnnotationsIfNotPresent(resolvedCollector, findClassAnnotations(myClass))
        }

        for (type in superTypes) {
            if (checkMixIns) {
                val clazz = type.rawClass
                resolvedCollector = addClassMixIns(resolvedCollector, clazz, myMixInResolver.findMixInClassFor(clazz))
            }

            if (myCollectAnnotations) {
                resolvedCollector = addAnnotationsIfNotPresent(resolvedCollector, findClassAnnotations(type.rawClass))
            }
        }

        if (checkMixIns) {
            resolvedCollector =
                    addClassMixIns(resolvedCollector, CLASS_OBJECT, myMixInResolver.findMixInClassFor(CLASS_OBJECT))
        }

        return resolvedCollector.asAnnotations()
    }

    private fun addClassMixIns(collector: AnnotationCollector, target: KClass<*>,
            mixIn: KClass<*>?): AnnotationCollector {
        mixIn ?: return collector

        var realCollector = addAnnotationsIfNotPresent(collector, findClassAnnotations(mixIn))

        for (parent in mixIn.findSuperClasses(target, false)) {
            realCollector = addAnnotationsIfNotPresent(realCollector, findClassAnnotations(parent))
        }

        return realCollector
    }

    private fun addAnnotationsIfNotPresent(collector: AnnotationCollector,
            annotations: List<Annotation>?): AnnotationCollector {
        annotations ?: return collector

        var realCollector = collector

        for (annotation in annotations) {
            if (!realCollector.isPresent(annotation)) {
                realCollector = realCollector.addOrOverride(annotation)

                if (myIntrospector!!.isAnnotationBundle(annotation)) {
                    realCollector = addFromBundleIfNotPresent(realCollector, annotation)
                }
            }
        }

        return realCollector
    }

    private fun addFromBundleIfNotPresent(collector: AnnotationCollector, bundle: Annotation): AnnotationCollector {
        var realCollector = collector

        for (annotation in findClassAnnotations(bundle.annotationClass)) {
            if (annotation is Target || annotation is Retention) {
                continue
            }

            if (!realCollector.isPresent(annotation)) {
                realCollector = realCollector.addOrOverride(annotation)

                if (myIntrospector!!.isAnnotationBundle(annotation)) {
                    realCollector = addFromBundleIfNotPresent(realCollector, annotation)
                }
            }
        }

        return realCollector
    }

    private fun findClassAnnotations(clazz: KClass<*>): List<Annotation> {
        if (clazz == CLASS_OBJECT) {
            return NO_ANNOTATIONS
        }

        return clazz.annotations
    }

    companion object {

        private val NO_ANNOTATIONS = emptyList<Annotation>()

        private val EMPTY_ANNOTATIONS = AnnotationCollector.EMPTY_ANNOTATIONS

        private val CLASS_OBJECT = Any::class

        private val CLASS_ENUM = Enum::class

        private val CLASS_LIST = List::class

        private val CLASS_MAP = Map::class

        /*
         ***************************************************************************************************************
         * Public static API
         ***************************************************************************************************************
         */

        fun resolve(config: MapperConfig<*>?, forType: KotlinType, resolver: MixInResolver?): AnnotatedClass {
            if (forType.isArrayType && skippableArray(config, forType.rawClass)) {
                return createArrayType(config, forType.rawClass)
            }

            return AnnotatedClassResolver(config!!, forType, resolver).resolveFully()
        }

        fun resolveWithoutSuperTypes(config: MapperConfig<*>?, forType: KClass<*>): AnnotatedClass {
            return resolveWithoutSuperTypes(config, forType, config)
        }

        fun resolveWithoutSuperTypes(config: MapperConfig<*>?, forType: KotlinType,
                resolver: MixInResolver?): AnnotatedClass {
            if (forType.isArrayType && skippableArray(config, forType.rawClass)) {
                return createArrayType(config, forType.rawClass)
            }

            return AnnotatedClassResolver(config!!, forType, resolver).resolveWithoutSuperTypes()
        }

        fun resolveWithoutSuperTypes(config: MapperConfig<*>?, forType: KClass<*>,
                resolver: MixInResolver?): AnnotatedClass {
            if (forType.isArray && skippableArray(config, forType)) {
                return createArrayType(config, forType)
            }

            return AnnotatedClassResolver(config!!, forType, resolver).resolveWithoutSuperTypes()
        }

        private fun skippableArray(config: MapperConfig<*>?, type: KClass<*>): Boolean {
            return config?.findMixInClassFor(type) == null
        }

        /**
         * Internal helper method used for resolving array types, unless they happen to have associated mix-in to apply.
         */
        @Suppress("unused")
        internal fun createArrayType(config: MapperConfig<*>?, raw: KClass<*>): AnnotatedClass {
            return AnnotatedClass(raw)
        }

        /*
         ***************************************************************************************************************
         * Main resolution methods
         ***************************************************************************************************************
         */

        private fun addSuperTypes(type: KotlinType?, result: MutableList<KotlinType>, addClassItself: Boolean) {
            type ?: return

            val clazz = type.rawClass

            if (clazz == CLASS_OBJECT || clazz == CLASS_ENUM) {
                return
            }

            if (addClassItself) {
                if (contains(result, clazz)) {
                    return
                }

                result.add(type)
            }

            for (interfaceClasses in type.interfaces) {
                addSuperInterfaces(interfaceClasses, result, true)
            }

            addSuperTypes(type.superClass, result, true)
        }

        private fun addSuperInterfaces(type: KotlinType, result: MutableList<KotlinType>, addClassItself: Boolean) {
            val clazz = type.rawClass

            if (addClassItself) {
                if (contains(result, clazz)) {
                    return
                }

                result.add(type)

                if (clazz == CLASS_OBJECT || clazz == CLASS_ENUM) {
                    return
                }
            }

            for (interfaceClasses in type.interfaces) {
                addSuperInterfaces(interfaceClasses, result, true)
            }
        }

        private fun contains(found: MutableList<KotlinType>, raw: KClass<*>): Boolean {
            for (kotlinType in found) {
                if (kotlinType.rawClass == raw) {
                    return true
                }
            }

            return false
        }

    }

}