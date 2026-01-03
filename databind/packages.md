# Module cirjackson-databind

The databinding module of CirJackson

# Package org.cirjson.cirjackson.databind

# Package org.cirjson.cirjackson.databind.annotation

Annotations that directly depend on classes in databinding bundle (not just CirJackson core) and cannot be included in
CirJackson core annotations package (because it cannot have any external dependencies).

# Package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

Classes used for exposing logical structure of POJOs as Jackson sees it, and exposed via
[org.cirjson.cirjackson.databind.ObjectMapper.acceptCirJsonFormatVisitor] methods.

The main entrypoint for code, then, is
[org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper] and other types are recursively
needed during traversal.

# Package org.cirjson.cirjackson.databind.cirjsontype

Package that contains interfaces that define how to implement functionality for dynamically resolving type during
deserialization. This is needed for complete handling of polymorphic types, where actual type cannot be determined
statically (declared type is a supertype of actual polymorphic serialized types).

# Package org.cirjson.cirjackson.databind.cirjsontype.implementation

Package that contains standard implementations for [org.cirjson.cirjackson.databind.cirjsontype.TypeResolverBuilder] and
[org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver].

# Package org.cirjson.cirjackson.databind.configuration

Package that contains most of configuration-related classes; exception being couple of most-commonly used configuration
things (like Feature enumerations) that are at the main level (` org.cirjson.cirjackson.databind`).

# Package org.cirjson.cirjackson.databind.introspect

Functionality needed for Bean introspection, required for detecting accessors and mutators for Beans, as well as
locating and handling method annotations.

Beyond collecting annotations, additional "method annotation inheritance"is also supported: whereas regular classes do
not add annotations from overridden methods in any situation. But code in this package does. Similarly,
class-annotations are inherited properly from interfaces, in addition to abstract and concrete classes.

# Package org.cirjson.cirjackson.databind.node

Contains concrete [org.cirjson.cirjackson.databind.CirJsonNode] implementations CirJackson uses for the Tree model.
These classes are public, since concrete type will be needed for most operations that modify node trees. For read-only
access, concrete types are usually not needed.

# Package org.cirjson.cirjackson.databind.type

Package that contains concrete implementations of [org.cirjson.cirjackson.databind.KotlinType], as well as the factory
([org.cirjson.cirjackson.databind.type.TypeFactory]) for constructing instances from various input data types (like
[KClass], [java.lang.reflect.Type]) and programmatically (for structured types, arrays, [Lists][List] and [Maps][Map]).

# Package org.cirjson.cirjackson.databind.util

Utility classes for Databind package.

# Package org.cirjson.cirjackson.databind.util.internal

This package contains an implementation of a bounded [java.util.concurrent.ConcurrentMap] data structure.

This package is intended only for use internally by Jackson libraries and has
missing features compared to the full http://code.google.com/p/concurrentlinkedhashmap/ implementation.

The [org.cirjson.cirjackson.databind.util.internal.PrivateMaxEntriesMap] class supplies an efficient, scalable,
thread-safe, bounded map. As with the`Java Collections Framework` the "Concurrent" prefix is used to indicate that the
map is not governed by a single exclusion lock.
