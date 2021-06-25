# To Investigate

## Indy for lazy anonymous struct creation
Use invokedynamic to create hidden classes when they're actually used for the first time instead
of eagerly creating classfiles for every anon struct. Maybe ast analysis could dedupe anon 
structs if they show up enough in code?

# Open Questions

## Java Interop
Current thinking is to basically a Java class as a module, where there's a constructor for the 
object and all of the methods are functions on the module. E.g. 
```
let f = File("foo")
File.setExecutable(f, true)
Object.setField(f, "bar")
```

Not sure if this code should be run in a special block. Maybe regular Java types shouldn't exist
in a meaningful way inside sasq code? Possibly need to wrap it in a struct or opaque type. Also
need to figure out how imports work here. 

## Mutability
Should mutability be allowed in structs? Leaning towards no, but might need it for practicality.
As least would want some sort of marker on fields and functions that mutate a struct. That could
possibly tie into the Java interop to make mutable OO code more sane to deal with. 

## Private Fields/Functions in Struct
How do we handle private in structs? It might only make sense to allow it in modules, since I 
can't think of a usecase for private fields/funcs in anon structs. Should test this out more. 
Maybe modules are still structs, but slightly special due to this. 

## Compiler Pipeline
ANTLR -> AST -> AstValidator -> TypeResolver -> BytecodeGeneration
Should the AstValidator and TypeResolver be flipped? E.g. TypeResolver cannot resolve a VarReference 
to a variable or function that hasn't been declared yet, so current order seems to make sense.

# Example Future Code

## Result Type
```
mod Result {
  // Traditional variant types are represented as tuples or structs with atoms in the same
  // position or field name. Possible to create sugar for this later.
  type Ok[V] = (:ok, V),
  type Err[E] = (:err, E),
  
  // Type with same name as module can be deduped, i.e. Result.Result -> Result
  type Result[V, E] = Ok[V] | Err[E],
  
  // Constructor-like functions start with caps and match the type name conventionally
  Ok = (value: V): Ok[V] -> (:ok, value),
  Err = (error: E): Err[E] -> (:err, error),
}
```
