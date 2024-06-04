# Module cirjackson-core

Jackson but for CirJSON

# Package org.cirjson.cirjackson.core

Main public API classes of the core streaming CirJSON  processor: most importantly
[org.cirjson.cirjackson.core.CirJsonFactory] used for constructing CirJSON parser
([org.cirjson.cirjackson.CirJsonParser]) and generator ([org.cirjson.cirjackson.CirJsonGenerator]) instances.

Public API of the higher-level mapping interfaces ("Mapping API") is found from the "cirjackson-databind" bundle, except
for following base interfaces that are defined here:

* [org.cirjson.cirjackson.core.TreeNode] is included within Streaming API to support integration of the Tree Model
(which is based on `CirJsonNode`) with the basic parsers and generators (if and only if using mapping-supporting factory: which is part of Mapping API, not core)

* [org.cirjson.cirjackson.ObjectCodec] is included so that reference to the object capable of serializing/deserializing
Objects to/from CirJSON (usually, `org.cirjson.cirjackson.databind.ObjectMapper`) can be exposed, without adding direct
dependency to implementation.

# Package org.cirjson.cirjackson.core.util

Utility classes used by CirJackson Core functionality.
