package org.cirjson.cirjackson.databind.util

import kotlin.reflect.KClass

/**
 * Helper class used for checking whether a property is visible in the active view
 */
open class ViewMatcher {

    open fun isVisibleForView(activeView: KClass<*>): Boolean {
        return false
    }

    /*
     *******************************************************************************************************************
     * Concrete subclasses
     *******************************************************************************************************************
     */

    private class Single(private val myView: KClass<*>) : ViewMatcher() {

        override fun isVisibleForView(activeView: KClass<*>): Boolean {
            return myView == activeView || myView.isAssignableFrom(activeView)
        }

    }

    private class Multiple(private val myViews: Array<KClass<*>>) : ViewMatcher() {

        override fun isVisibleForView(activeView: KClass<*>): Boolean {
            for (view in myViews) {
                if (view == activeView || view.isAssignableFrom(activeView)) {
                    return true
                }
            }

            return false
        }

    }

    companion object {

        val EMPTY = ViewMatcher()

        fun construct(views: Array<KClass<*>>?): ViewMatcher {
            views ?: return EMPTY

            return when (views.size) {
                0 -> EMPTY
                1 -> Single(views[0])
                else -> Multiple(views)
            }
        }

    }

}