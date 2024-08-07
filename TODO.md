# Todo List
* Fix type resolution where there is a "more specific" option
* Unify FunctionCall and ForeignFunctionCall
* Avoid generation of duplicate struct classes

## Right Now
* Handle variable captures in functions in anon functions
* Figure out how to automatically derive a small set of functions for a struct at compile time
  * Keep the set small to make it simpler
  * toString, serialize/deserialize, hash, equals, compare
  * Maybe not that simple since the struct object itself needs to implement them
