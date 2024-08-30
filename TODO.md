# Todo List
* Fix type resolution where there is a "more specific" option
* Unify FunctionCall and ForeignFunctionCall
* Avoid generation of duplicate struct classes
* Symbol table for names

## Right Now
* Figure out how to automatically derive a small set of functions for a struct at compile time
  * Keep the set small to make it simpler
  * toString, serialize/deserialize, hash, equals, compare
  * Maybe not that simple since the struct object itself needs to implement them
* Support named function params
  * Function type needs to have labels or the type checker needs to look up the original func object
    * If the func type has labels, it also needs to know if there's a default expression, though not necessarily what the value of it is
    * In order to implement defaults, method overloads need to be generated for all the permutations of the default parameters
    * When a param has a type annotation, check that the default matches the type param
    * When it doesn't, infer the type of the default and set the type of the param to the inferred type
* Create a `ConstructorType` and change `SumType::types` to be a list of those
* Update struct names for literal structs to describe where they're declared
