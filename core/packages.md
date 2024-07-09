# Module cirjackson-core

Jackson but for CirJSON

# Package org.cirjson.cirjackson.core

Main public API classes of the core streaming CirJSON  processor: most importantly
[org.cirjson.cirjackson.core.CirJsonFactory] used for constructing CirJSON parser
([org.cirjson.cirjackson.core.CirJsonParser]) and generator ([org.cirjson.cirjackson.core.CirJsonGenerator]) instances.

Public API of the higher-level mapping interfaces ("Mapping API") is found from the "cirjackson-databind" bundle, except
for following base interfaces that are defined here:

* [org.cirjson.cirjackson.core.TreeNode] is included within Streaming API to support integration of the Tree Model
  (which is based on `CirJsonNode`) with the basic parsers and generators (if and only if using mapping-supporting
  factory: which is part of Mapping API, not core)

* [org.cirjson.cirjackson.core.ObjectReadContext] is included so that reference to the object capable of deserializing
  Objects from token streams (usually, `org.cirjson.cirjackson.databind.ObjectMapper`) can be exposed, without adding
  direct dependency to implementation.

* [org.cirjson.cirjackson.core.ObjectWriteContext] is included so that reference to the object capable of serializing
  Objects from token streams (usually, `org.cirjson.cirjackson.databind.ObjectMapper`) can be exposed, without adding
  direct dependency to implementation.

# Package org.cirjson.cirjackson.core.async

Package that contains abstractions needed to support optional non-blocking decoding (parsing) functionality. Although
parsers are constructed normally via [org.cirjson.cirjackson.core.cirjson.CirJsonFactory] (and are, in fact, subtypes of
[org.cirjson.cirjackson.core.CirJsonParser]), the way input is provided differs.

# Package org.cirjson.cirjackson.core.base

Base classes used by concrete Parser and Generator implementations; contain functionality that is not specific to
CirJSON or input abstraction (byte vs char). Most formats extend these types, although it is also possible to directly
extend [org.cirjson.cirjackson.core.CirJsonParser] or [org.cirjson.cirjackson.core.CirJsonGenerator].

# Package org.cirjson.cirjackson.core.cirjson

CirJSON-specific parser and generator implementation classes that CirJackson defines and uses. Application code should
not (need to) use contents of this package; nor are these implementations likely to be of use for sub-classing.

# Package org.cirjson.cirjackson.core.cirjson.async

Non-blocking ("async") CirJSON parser implementation.

# Package org.cirjson.cirjackson.core.exception

Package for subtypes of [org.cirjson.cirjackson.core.CirJacksonException] defined and used by streaming API.

# Package org.cirjson.cirjackson.core.extensions

All the useful extension functions for CirJackson

# Package org.cirjson.cirjackson.core.filter

Package that contains abstractions needed to support content filtering.

# Package org.cirjson.cirjackson.core.io

Package that contains abstractions needed to support input and output.

# Package org.cirjson.cirjackson.core.io.schubfach

Internal package that contains the algorithms to convert `Doubles` and `Floats` into `String` the Schubfach way.

# Package org.cirjson.cirjackson.core.symbols

Internal implementation classes for efficient handling of symbols in CirJSON (Object property names)

# Package org.cirjson.cirjackson.core.tree

Package that contains abstractions that are used dealing with Tree Models (other than
[org.cirjson.cirjackson.core.TreeNode]).

# Package org.cirjson.cirjackson.core.type

Contains classes needed for type introspection, mostly used by data binding functionality. Most of this functionality is
needed to properly handled generic types, and to simplify and unify processing of things Jackson needs to determine how
contained types (of [Collection] and [Map] classes) are to be handled.

# Package org.cirjson.cirjackson.core.util

Utility classes used by CirJackson Core functionality.
