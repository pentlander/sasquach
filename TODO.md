# Todo List
* Fix type resolution where there is a "more specific" option
* Unify FunctionCall and ForeignFunctionCall
* Avoid generation of duplicate struct classes
* Remove typeClass from Type interface

## Right Now
* See why None isn't being resolved to the alias variant
* Codegen for the match expresssion. Just loop over the possible classes to see if it's one of the variant types. If in doubt, check the JVM class switch bytecode
* Parse named struct literals, e.g. `Foo { bar = "string" }`
* Get rid of `ExistentialType`. I think the nomenclature is wrong and we have `TypeVariable` already
