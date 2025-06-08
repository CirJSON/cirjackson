package org.cirjson.cirjackson.databind.introspection

abstract class AnnotatedWithParams : AnnotatedMember {

    /*
     *******************************************************************************************************************
     * Life-cycle
     *******************************************************************************************************************
     */

    protected constructor(context: TypeResolutionContext, annotations: AnnotationMap?,
            paramAnnotations: Array<AnnotationMap>?) : super(context, annotations) {
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    abstract val parameterCount: Int

}