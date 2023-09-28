# Todo List
* Fix type resolution where there is a "more specific" option
* Unify FunctionCall and ForeignFunctionCall
* Avoid generation of duplicate struct classes
* Remove typeClass from Type interface

## Right Now
* Update TypeResolutionResult to just contain errors and the TypedStructs that represent the modules. Then update the codegen to use the TAST.
  * Also need to figure out a way to deal with type variables. Currently, they're handled by replacing looking up all the types in the map and resolving them to replace any type vars. The two options are:
    1. Make TypeVariable mutable and directly update it to contain the new Type
    2. Update the TAST to replace all the TypeVariables with their actual types
    3. Use the TypeUnifier's map of TypeVariable -> Type and use that during bytecode gen
* Parse named struct literals, e.g. `Foo { bar = "string" }`
