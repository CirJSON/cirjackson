package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.databind.configuration.CoercionConfigs
import org.cirjson.cirjackson.databind.configuration.ConfigOverrides
import org.cirjson.cirjackson.databind.configuration.MapperBuilder
import org.cirjson.cirjackson.databind.type.TypeFactory
import java.util.concurrent.atomic.AtomicReference

open class ObjectMapper protected constructor(builder: MapperBuilder<*, *>) : TreeCodec, Versioned {

    /*
     *******************************************************************************************************************
     * Configuration settings, shared
     *******************************************************************************************************************
     */

    protected val myStreamFactory = builder.streamFactory()

    protected val myTypeFactory: TypeFactory = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Configuration settings, serialization
     *******************************************************************************************************************
     */

    protected val mySerializationConfig: SerializationConfig = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Configuration settings, deserialization
     *******************************************************************************************************************
     */

    protected val myDeserializationConfig: DeserializationConfig = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Saved state to allow rebuilding
     *******************************************************************************************************************
     */

    protected val mySavedBuilderState = builder.saveStateApplyModules()

    /*
     *******************************************************************************************************************
     * Lifecycle: builder-style construction
     *******************************************************************************************************************
     */

    init {
        val configOverrides = let {
            val ref = AtomicReference<ConfigOverrides>()
            builder.withAllConfigOverrides { ref.set(it) }
            ref.get()?.snapshot()
        }
        val coercionConfigs = let {
            val ref = AtomicReference<CoercionConfigs>()
            builder.withAllCoercionConfigs { ref.set(it) }
            ref.get()?.snapshot()
        }
//        myTypeFactory = builder.typeFactory().snapshot()
        TODO("Not yet implemented")
    }

    open fun <M : ObjectMapper, B : MapperBuilder<M, B>> rebuild(): MapperBuilder<M, B> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Versioned implementation
     *******************************************************************************************************************
     */

    override fun version(): Version {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Configuration: main config object access
     *******************************************************************************************************************
     */

    open fun tokenStreamFactory(): TokenStreamFactory {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * TreeCodec implementation
     *******************************************************************************************************************
     */

    override fun createObjectNode(): ObjectTreeNode {
        TODO("Not yet implemented")
    }

    override fun createArrayNode(): ArrayTreeNode {
        TODO("Not yet implemented")
    }

    override fun booleanNode(boolean: Boolean): TreeNode {
        TODO("Not yet implemented")
    }

    override fun stringNode(text: String?): TreeNode {
        TODO("Not yet implemented")
    }

    override fun missingNode(): TreeNode {
        TODO("Not yet implemented")
    }

    override fun nullNode(): TreeNode {
        TODO("Not yet implemented")
    }

    override fun treeAsTokens(node: TreeNode): CirJsonParser {
        TODO("Not yet implemented")
    }

    override fun <T : TreeNode> readTree(parser: CirJsonParser): T? {
        TODO("Not yet implemented")
    }

    override fun writeTree(generator: CirJsonGenerator, tree: TreeNode) {
        TODO("Not yet implemented")
    }

}