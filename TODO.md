# Todo List
* Fix type resolution where there is a "more specific" option
* Unify FunctionCall and ForeignFunctionCall
* Avoid generation of duplicate struct classes
* Symbol table for names
* Preconditions built into type signature

## Right Now
* Figure out how to automatically derive a small set of functions for a struct at compile time
  * Keep the set small to make it simpler
  * toString, serialize/deserialize, hash, equals, compare
  * Maybe not that simple since the struct object itself needs to implement them
* Implement default parameters
* Need some way to express `equals` and `hashCode` on structs for Java interop
* Infer struct types in lambdas before the concrete struct type is found. If an empty TypeVariable is found where a struct is expected, infer the inner type as a struct with an unnamed row variable
* Only create `TypeVariable`s via a factory that keeps track of all the variables that get created. At the end of a named function, throw if any of the variables are unresolved
* Separate TypeNode into UnresolvedTypeNode and TypeNode where the former does not have a `type` method. `NamedTypeResolver` handles the conversion and is able to do things like create type variables for missing parameters and generate struct names
