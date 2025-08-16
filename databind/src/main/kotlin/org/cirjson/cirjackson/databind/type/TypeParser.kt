package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.throwIfRuntimeException
import java.util.*
import kotlin.reflect.KClass

/**
 * Simple recursive-descent parser for parsing canonical [KotlinType] representations and constructing type instances.
 */
open class TypeParser {

    @Throws(IllegalArgumentException::class)
    open fun parse(typeFactory: TypeFactory, canonical: String): KotlinType {
        if (canonical.length > MAX_TYPE_LENGTH) {
            throw IllegalArgumentException("Failed to parse type ${
                quoteTruncated(canonical)
            }: too long (${canonical.length} characters), maximum length allowed: $MAX_TYPE_LENGTH")
        }

        val tokens = MyTokenizer(canonical.trim())
        val type = parseType(typeFactory, tokens, MAX_TYPE_NESTING)

        if (tokens.hasMoreTokens()) {
            throw problem(tokens, "Unexpected tokens after complete type")
        }

        return type
    }

    @Throws(IllegalArgumentException::class)
    protected open fun parseType(typeFactory: TypeFactory, tokens: MyTokenizer, nestingAllowed: Int): KotlinType {
        if (!tokens.hasMoreTokens()) {
            throw problem(tokens, "Unexpected end-of-string")
        }

        val base = findClass(typeFactory, tokens.nextToken(), tokens)

        if (tokens.hasMoreTokens()) {
            val token = tokens.nextToken()

            if (token == "<") {
                val parameterTypes = parseTypes(typeFactory, tokens, nestingAllowed - 1)
                val bindings = TypeBindings.create(base, parameterTypes)
                return typeFactory.fromClass(null, base, bindings)
            }

            tokens.pushback(token)
        }

        return typeFactory.fromClass(null, base, TypeBindings.EMPTY)
    }

    @Throws(IllegalArgumentException::class)
    protected open fun parseTypes(typeFactory: TypeFactory, tokens: MyTokenizer,
            nestingAllowed: Int): List<KotlinType> {
        if (nestingAllowed < 0) {
            throw problem(tokens, "too deeply nested; exceeds maximum of $MAX_TYPE_NESTING nesting levels")
        }

        val types = ArrayList<KotlinType>()

        while (tokens.hasMoreTokens()) {
            types.add(parseType(typeFactory, tokens, nestingAllowed))

            if (!tokens.hasMoreTokens()) {
                break
            }

            val token = tokens.nextToken()

            if (token == ">") {
                return types
            }

            if (token != ",") {
                throw problem(tokens, "Unexpected token '$token', expected ',' or '>')")
            }
        }

        throw problem(tokens, "Unexpected end-of-string")
    }

    protected open fun findClass(typeFactory: TypeFactory, className: String, tokens: MyTokenizer): KClass<*> {
        try {
            return typeFactory.findClass(className)
        } catch (e: Exception) {
            e.throwIfRuntimeException()
            throw problem(tokens, "Cannot locate class '$className', problem: ${e.message}")
        }
    }

    protected open fun problem(tokens: MyTokenizer, message: String): IllegalArgumentException {
        return IllegalArgumentException("Failed to parse type ${quoteTruncated(tokens.allInput)} (remaining: ${
            quoteTruncated(tokens.remainingInput)
        }): $message")
    }

    private fun quoteTruncated(string: String): String {
        if (string.length < 1000) {
            return "'$string'"
        }

        return "'${string.take(1000)}...'[truncated ${string.length - 1000} characters]"
    }

    protected class MyTokenizer(val allInput: String) : StringTokenizer(allInput, "<,>", true) {

        var myIndex = 0

        var myPushbackToken: String? = null

        override fun hasMoreTokens(): Boolean {
            return myPushbackToken != null || super.hasMoreTokens()
        }

        override fun nextToken(): String {
            return if (myPushbackToken != null) {
                val token = myPushbackToken!!
                myPushbackToken = null
                token
            } else {
                val token = super.nextToken()!!
                myIndex += token.length
                token.trim()
            }
        }

        fun pushback(token: String) {
            myPushbackToken = token
        }

        val remainingInput: String
            get() = allInput.substring(myIndex)

    }

    companion object {

        /**
         * Maximum length of canonical type definition we will try to parse. Used as protection for malformed generic
         * type declarations.
         */
        const val MAX_TYPE_LENGTH = 64_000

        /**
         * Maximum levels of nesting allowed for parameterized types. Used as protection for malformed generic type
         * declarations.
         */
        const val MAX_TYPE_NESTING = 1000

        val INSTANCE = TypeParser()

    }

}