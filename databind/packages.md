# Module cirjackson-databind

The databinding module of CirJackson

# Package org.cirjson.cirjackson.databind

# Package org.cirjson.cirjackson.databind.annotation

Annotations that directly depend on classes in databinding bundle (not just CirJackson core) and cannot be included in
CirJackson core annotations package (because it cannot have any external dependencies).

# Package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

Classes used for exposing logical structure of POJOs as Jackson sees it, and exposed via
[org.cirjson.cirjackson.databind.ObjectMapper.acceptCirJsonFormatVisitor]methods.

The main entrypoint for code, then, is
[org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper] and other types are recursively
needed during traversal.