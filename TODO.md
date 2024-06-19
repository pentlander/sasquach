# Todo List
* Fix type resolution where there is a "more specific" option
* Unify FunctionCall and ForeignFunctionCall
* Avoid generation of duplicate struct classes
* Remove typeClass from Type interface

## Right Now
* Add row modifier to struct type
* Update the `isAssignableFrom` for struct type to only require a subset of fields instead of an exact match
* Update codegen to create an interface instead of a class
* Name resolve named row variable to either the type arg to create a row variable or the decl of another struct to include all the fields in that struct
* Narrow named row variable to a struct type
* Update unifier to adopt the extra fields into the row variable
  * The row variable acts as a sort of struct type var that needs to get unified
* Need to create a generic way to spread the fields of a struct into a new literal struct
  * A function that returns a row struct with a spread in it cannot know what constructor needs to be called at runtime. We know this at compile time for type checking reasons, however functions are not monomorphized so a specific constructor cannot be used.
  * In order to do this, the function call needs to provide additional information to the method via indy. Either the struct needs to be generated at compile time and the struct name supplied in the func call, or the class needs to be generated at runtime. The latter is not feasible because the new struct needs to have the methods from the origin class copied over in addition to the fields. Actually we might be able to reflect over all the methods and convert them to methodhandles that get executed in the body. However this would obfuscate what's actually happening and probably make debugging more difficult
  * To implement the spread operator, the indy operation needs to know what fields are already passed into the spread to avoid including those in the new object. Then it needs to reflect over the object being passed in to discover all of the fields that aren't passed in
  * The callsite needs to be replaced with a methodhandle where the spread is also an indy. T
