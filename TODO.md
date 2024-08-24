# Todo List
* Fix type resolution where there is a "more specific" option
* Unify FunctionCall and ForeignFunctionCall
* Avoid generation of duplicate struct classes

## Right Now
* Figure out how to automatically derive a small set of functions for a struct at compile time
  * Keep the set small to make it simpler
  * toString, serialize/deserialize, hash, equals, compare
  * Maybe not that simple since the struct object itself needs to implement them
* Don't require a type signature for local types
* Support named function params
* Create a `ConstructorType` and change `SumType::types` to be a list of those
* Update struct names for literal structs to describe where they're declared
