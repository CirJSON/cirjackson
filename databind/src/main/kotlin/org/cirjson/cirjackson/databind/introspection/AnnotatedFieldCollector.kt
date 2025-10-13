package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.util.findSuperClasses
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

class AnnotatedFieldCollector private constructor(config: MapperConfig<*>?, private val myMixInResolver: MixInResolver?,
        private val myCollectAnnotations: Boolean) : CollectorBase(config) {

    private fun collect(typeResolutionContext: TypeResolutionContext, type: KotlinType,
            primaryMixIn: KClass<*>?): List<AnnotatedField> {
        val foundFields = findFields(typeResolutionContext, type, primaryMixIn, null) ?: return emptyList()
        val result = ArrayList<AnnotatedField>(foundFields.size)

        for (field in foundFields.values) {
            result.add(field.build())
        }

        return result
    }

    private fun findFields(typeResolutionContext: TypeResolutionContext, type: KotlinType, mixIn: KClass<*>?,
            fields: MutableMap<String, FieldBuilder>?): MutableMap<String, FieldBuilder>? {
        var realFields = fields

        val parentType = type.superClass ?: return realFields
        val parentMixin = myMixInResolver?.findMixInClassFor(parentType.rawClass)
        realFields = findFields(TypeResolutionContext.Basic(myConfig!!.typeFactory, parentType.bindings), parentType,
                parentMixin, realFields)
        val rawType = type.rawClass

        for (field in rawType.declaredMemberProperties) {
            if (!isIncludableField(field)) {
                continue
            }

            if (realFields == null) {
                realFields = LinkedHashMap()
            }

            val builder = FieldBuilder(typeResolutionContext, field)

            if (myCollectAnnotations) {
                builder.annotations = collectAnnotations(builder.annotations, field.annotations.toTypedArray())
            }

            realFields[field.name] = builder
        }

        if (realFields != null && mixIn != null) {
            addFieldMixIns(mixIn, rawType, realFields)
        }

        return realFields
    }

    /**
     * Method called to add field mix-ins from given mix-in class (and its fields) into already collected actual fields
     * (from introspected classes and their superclasses)
     */
    private fun addFieldMixIns(mixInClass: KClass<*>, targetClass: KClass<*>, fields: Map<String, FieldBuilder>) {
        val parents = mixInClass.findSuperClasses(targetClass, true)

        for (mixin in parents) {
            for (mixinField in mixin.declaredMemberProperties) {
                if (!isIncludableField(mixinField)) {
                    continue
                }

                val name = mixinField.name
                val builder = fields[name] ?: continue
                builder.annotations = collectAnnotations(builder.annotations, mixinField.annotations.toTypedArray())
            }
        }
    }

    private fun isIncludableField(field: KProperty<*>): Boolean {
        if (field.javaField!!.isEnumConstant) {
            return true
        }

        if (field.javaField!!.isSynthetic) {
            return false
        }

        return !Modifier.isStatic(field.javaField!!.modifiers)
    }

    private class FieldBuilder(val typeContext: TypeResolutionContext, val field: KProperty<*>) {

        var annotations = AnnotationCollector.EMPTY_COLLECTOR

        fun build(): AnnotatedField {
            return AnnotatedField(typeContext, field, annotations.asAnnotationMap())
        }

    }

    companion object {

        fun collectFields(config: MapperConfig<*>?, context: TypeResolutionContext, mixins: MixInResolver?,
                type: KotlinType, primaryMixIn: KClass<*>?, collectAnnotations: Boolean): List<AnnotatedField> {
            return AnnotatedFieldCollector(config, mixins, collectAnnotations).collect(context, type, primaryMixIn)
        }

    }

}