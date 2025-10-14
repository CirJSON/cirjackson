package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.util.classMethods
import org.cirjson.cirjackson.databind.util.findRawSuperTypes
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

class AnnotatedMethodCollector private constructor(config: MapperConfig<*>?, mixInResolver: MixInResolver?,
        private val myCollectAnnotations: Boolean) : CollectorBase(config) {

    private val myMixInResolver: MixInResolver? = myIntrospector?.let { mixInResolver }

    private fun collect(typeResolutionContext: TypeResolutionContext, mainType: KotlinType,
            superTypes: List<KotlinType>, primaryMixIn: KClass<*>?): AnnotatedMethodMap {
        val methods = LinkedHashMap<MemberKey, MethodBuilder>()

        addMemberMethods(typeResolutionContext, mainType.rawClass, methods, primaryMixIn)

        for (type in superTypes) {
            val mixin = myMixInResolver?.findMixInClassFor(type.rawClass)
            addMemberMethods(TypeResolutionContext.Basic(myConfig!!.typeFactory, type.bindings), type.rawClass, methods,
                    mixin)
        }

        var checkObject = false

        if (myMixInResolver != null) {
            val mixin = myMixInResolver.findMixInClassFor(Any::class)

            if (mixin != null) {
                addMethodMixIns(typeResolutionContext, mainType.rawClass, methods, mixin)
                checkObject = true
            }
        }

        if (checkObject && myIntrospector != null && methods.isNotEmpty()) {
            for (entry in methods) {
                val key = entry.key

                if (key.name != "hashCode" || key.argumentCount != 0) {
                    continue
                }

                try {
                    val method = Any::class.java.getDeclaredMethod(key.name)
                    val builder = entry.value
                    builder.annotations = collectDefaultAnnotations(builder.annotations,
                            method.kotlinFunction!!.annotations.toTypedArray())
                    builder.method = method.kotlinFunction!!
                } catch (_: Exception) {
                }
            }
        }

        if (methods.isEmpty()) {
            return AnnotatedMethodMap()
        }

        val actual = LinkedHashMap<MemberKey, AnnotatedMethod>(methods.size)

        for (entry in methods) {
            entry.value.build()?.let { actual[entry.key] = it }
        }

        return AnnotatedMethodMap(actual)
    }

    private fun addMemberMethods(typeResolutionContext: TypeResolutionContext, clazz: KClass<*>,
            methods: MutableMap<MemberKey, MethodBuilder>, mixInClass: KClass<*>?) {
        if (mixInClass != null) {
            addMethodMixIns(typeResolutionContext, clazz, methods, mixInClass)
        }

        for (method in clazz.classMethods) {
            if (!isIncludableMemberMethod(method)) {
                continue
            }

            val key = MemberKey(method)
            val builder = methods[key]

            if (builder == null) {
                val collector =
                        myIntrospector?.let { collectAnnotations(method.kotlinFunction!!.annotations.toTypedArray()) }
                                ?: AnnotationCollector.EMPTY_COLLECTOR
                methods[key] = MethodBuilder(typeResolutionContext, method.kotlinFunction, collector)
                continue
            }

            if (myCollectAnnotations) {
                builder.annotations =
                        collectAnnotations(builder.annotations, method.kotlinFunction!!.annotations.toTypedArray())
            }

            val old = builder.method

            if (old == null) {
                builder.method = method.kotlinFunction
            } else if (Modifier.isAbstract(old.javaMethod!!.modifiers) && !Modifier.isAbstract(method.modifiers)) {
                builder.method = method.kotlinFunction
                builder.typeResolutionContext = typeResolutionContext
            }
        }
    }

    private fun addMethodMixIns(typeResolutionContext: TypeResolutionContext, targetClass: KClass<*>,
            methods: MutableMap<MemberKey, MethodBuilder>, mixInClass: KClass<*>?) {
        myIntrospector ?: return

        for (mixin in mixInClass.findRawSuperTypes(targetClass, true)) {
            for (method in mixin.declaredMemberFunctions) {
                if (!isIncludableMemberMethod(method.javaMethod!!)) {
                    continue
                }

                val key = MemberKey(method.javaMethod!!)
                val builder = methods[key]
                val annotations = method.annotations.toTypedArray()

                if (builder == null) {
                    methods[key] = MethodBuilder(typeResolutionContext, null, collectAnnotations(annotations))
                } else {
                    builder.annotations = collectDefaultAnnotations(builder.annotations, annotations)
                }
            }
        }
    }

    private class MethodBuilder(var typeResolutionContext: TypeResolutionContext, var method: KFunction<*>?,
            var annotations: AnnotationCollector) {

        fun build(): AnnotatedMethod? {
            method ?: return null
            return AnnotatedMethod(typeResolutionContext, method!!, annotations.asAnnotationMap(), null)
        }

    }

    companion object {

        fun collectMethods(config: MapperConfig<*>?, context: TypeResolutionContext, mixins: MixInResolver?,
                type: KotlinType, superTypes: List<KotlinType>, primaryMixIn: KClass<*>?,
                collectAnnotations: Boolean): AnnotatedMethodMap {
            return AnnotatedMethodCollector(config, mixins, collectAnnotations).collect(context, type, superTypes,
                    primaryMixIn)
        }

        private fun isIncludableMemberMethod(method: Method): Boolean {
            if (Modifier.isStatic(method.modifiers) || method.isSynthetic || method.isBridge) {
                return false
            }

            return method.parameterCount <= 2
        }

    }

}