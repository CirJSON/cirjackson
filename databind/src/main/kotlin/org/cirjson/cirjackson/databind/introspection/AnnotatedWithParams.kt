package org.cirjson.cirjackson.databind.introspection

abstract class AnnotatedWithParams : AnnotatedMember {

    protected constructor(context: TypeResolutionContext, annotations: AnnotationMap?,
            paramAnnotations: Array<AnnotationMap>?) : super(context, annotations) {
    }

}