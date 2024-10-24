# Todo List
* Unify FunctionCall and ForeignFunctionCall
* Symbol table for names
* Preconditions built into type signature
* Implement default parameters

## Right Now
* Add sugar for importing multiple modules from the same package, e.g. `use std/{Eq, Result, Option},`
* Add a list of import preludes for commonly used std modules, e.g. `Option`, `Result`, etc.
* Expand imports of `std/**` to `sasquach/std/**` at compile time and change the base directory that the stdlib files come from. All the files *should* be namespaced by sasquach, but requiring users to type it out every time would be a PITA
* Figure out how to automatically derive a small set of functions for a struct at compile time
  * Keep the set small to make it simpler
  * toString, serialize/deserialize, hash, equals, compare
  * Maybe not that simple since the struct object itself needs to implement them
* Need some way to express `equals` and `hashCode` on structs for Java interop
* Infer struct types in lambdas before the concrete struct type is found. If an empty TypeVariable is found where a struct is expected, infer the inner type as a struct with an unnamed row variable
* Only create `TypeVariable`s via a factory that keeps track of all the variables that get created. At the end of a named function, throw if any of the variables are unresolved
* Separate TypeNode into UnresolvedTypeNode and TypeNode where the former does not have a `type` method. `NamedTypeResolver` handles the conversion and is able to do things like create type variables for missing parameters and generate struct names
