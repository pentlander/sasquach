# Todo List
* Fix type resolution where there is a "more specific" option
* Unify FunctionCall and ForeignFunctionCall
* Avoid generation of duplicate struct classes

## Right Now
* Generate first class functions as MethodHandles instead of structs
  * They are only generated as classes in Java because they need to implement the functional interfaces. Also, the codegen can be unified for functions with and without captures using dynalink
