# Design Choices

## Name Resolution
Any lookup of names, whether that be local functions, foreign funcitons, module functions, local or 
imported type aliases, etc. should be performed at the name resolution step. Any step that occurs after 
name resolution should assume that the necessary names exist and throw an exception if they don't.
The name resolution step should provide friendly compiler error messages if a name cannot resolve.

## Module functions require types
Top level functions on modules require type annotations. The main reasoning is that it greatly 
simplifies the function compilation and linkage process. If the parameters and return types are 
inferred, it means that a minor change in the code can alter a parameter type or the return type
while the user is writing code even if they mean for it to be the same. This can cascade to other
code dependent on the function, altering their types and rippling out further. Having a stable 
type signature ensures those rippling effects only occur when the signature is actually changed,
thus making the compiler friendlier to incremental changes and IDEs. It also makes type 
resolution more parallelizable since there is no need to recursively resolve the types of other 
functions being called.

## Type aliases
Type aliases provide another name to a struct or union. Creating an alias should not create a new 
classfile, all structs with equivalent structure (same field names/types) should be bound to a 
single classfile to reduce the number of classfiles. This means that an alias only exists at compile time. May revisit this choice if it makes debugging stacktraces more difficult. Foreign classes cannot be aliased, doing so should generate an error.

Regarding the above, you will be able to create a single classfile since other libraries have the ability to create their own literal structs. This means that you can't guarantee that the struct is using the same classfile as the one you're aliasing. In order to do this, you would need to dedupe the struct literals in the final jar during compilation. I'm not sure that this could work though, as the struct internal names need to reference the package they're in. Can you have no package name? Or rather you can have a reserved package name for structs.

## Visibility
We want some form of information hiding in structs. For example, the `List` type should not expose the inner Java `List` that it wraps. One way to handle that is to say that a type goes from an alias to a named type if it has any private fields:

```
// Implicitly a named type since other modules wouldn't be able to create an implementation since they don't know about the private field
type List[T] = { priv inner: java.List[T] }
```

I'm not a fan of this implicit behavior though. What might be better is to add some sort of modifier to the `type` keyword or to the struct itself to make it more explicit that it's creating a type that no other module can create. I think the modifier would have to be on the `type` keyword, since making a literal struct with private values doesn't seem terribly useful. On the other hand, Java allows local anonymous structs, though that's to implement some interface or class in the local scope. I don't think we need that since "interfaces" are implemented either structurally or by supplying a module with the necessary functions.

### Interaction with Row Polymorphism
Should structs with private fields/methods work with row polymorphism? I think so, the question is how to do this. A new class needs to be created when adding another field to the struct. We can't copy over the private members. The sanest thing to do is to create a new struct that contains an instance of the base struct and the fields being added. Then when member dispatch happens on the outer struct, do a recursive search on the inner delegates to find the member in question. It could also make sense to create a separate MethodHandle lookup table in the constant pool of the outer class to speed up dispatch.

In the future we could avoid this overhead in two ways:
1. Compile time evaluation when applied to modules. This means only one classfile is created with all the final fields instead of creating all the intermediate ones.
2. Create a single output class when we detect that several rows are added in a row. E.g. when you have a number of http middleware functions adding fields in a row, don't create the intermediate classes.

```
RequestState {
    type T[A] = { 
      priv connection: Connection,
      url: Url, 
      method: Method,
      headers: Headers,
      context: { ..A },
    },
}

...

addAuth = [A](reqState: RequestState.T[A]): RequestState.T[{ authToken: Option.T[String], ..A }] -> {
  match Headers.get(reqState.headers, "authtoken") {
    Some(token) -> RequestState.withCtx(reqState, ctx -> { authToken: token, ..ctx },
    None -> reqState,
  }
}
```

## Higher Order Functions
Should there be a separate function class for member vs lambda functions? The main difference is that the types on the lambda functions are optional, while they are not on member funcs. Code generation for a module member function will always generate a method in the classfile, while the code generation for a struct varies. If there are no captures and the function is defined within the struct literal, then it can be generated as a method. If it is defined within the struct literal and has captures, the captures can be placed as fields within the struct. Would need to ensure that those captured fields do not interfere with typechecking and are inaccesible outside that function. If the function is defined outside of the struct without captures, it could possibly be generated as a method on the struct. Otherwise if it's defined outside the struct and has captures, it has to be generated as a separate class. This class could be a struct with a single method named `_invoke`. When looking up a function call, local variables also need to be included. The variable's type must match the aformentioned struct. 

For now, it's probably simplest to just consider two cases:
1. Function defined within the struct (or module) without any captures. This is compiled as a method
2. All other cases create a struct with a function `_invoke` that gets assigned to a field

The one subtle thing is figuring out how typecheck a module with a method against a struct with a func struct. This implies that the transformation from Function to Struct must only occur at the codegen level, otherwise the typechecking code gets more complicated. The struct method dispatch must also be adjusted to account for this. 

## Sum types
When defining a sum type, you are actually adding multiple functions to the module that each create a variant of the sum type. Otherwise, you would have to do `Foo.T.Bar(...)` which looks a bit ugly. I'm not sure how this would work exactly if a user wanted to directly refer to one of the variants. It would still have to look like the statement above.

## AST
I was debating whether to create a separate typed ast or just add a nullable field to the existing AST and call it a day. The typed AST give us a chance to update the AST with resolved names as well as adding type information. The nullable field could just be a generic context object that lives in every expression, but it seems difficult to do that while retaining strong typing.

# To Investigate

## Indy for lazy anonymous struct creation
Use invokedynamic to create hidden classes when they're actually used for the first time instead
of eagerly creating classfiles for every anon struct. Maybe ast analysis could dedupe anon 
structs if they show up enough in code?

# Open Questions

## Multi-file resolution
Resolving names across multiple files is hard. A fork-join task needs to be started for every 
module and joined on when another module encounters an import of that module. Errors with ranges
also need to be adapted to handle the fact that the range could be on any file, so the actual 
file (compunit?) needs to be tracked within the error as well. 

## Java Interop
Current thinking is to basically a Java class as a module, where there's a constructor for the object and all the methods are functions on the module. E.g. 
```
let f = File("foo")
File.setExecutable(f, true)
Object.setField(f, "bar")
```

Not sure if this code should be run in a special block. Maybe regular Java types shouldn't exist
in a meaningful way inside sasq code? Possibly need to wrap it in a struct or opaque type. Also
need to figure out how imports work here. 

### Dealing with Null
Code has to somehow deal with null values that come from foreign functions. There are a few options on how to handle this:
1. Do nothing, foreign code is the wild west and you have to be careful when integrating. Definitely not doing this.
2. Integrate null into the type system somehow. Also don't want to do this as Sasquach code is expected to use the Option type instead.
3. Add null checks to every return value from a foreign function that is not a primitive. Could also perform analysis so that a check is not generated when the return value isn't used. Users can opt out of the null check by adding a `!` to the end of the method name.
4. Make every foreign function that returns a non-primitive value return an `Option`. Users can opt out by adding an `!` to return the bare value with a null check and a `!!` for the bare values without a null check. Would still want to do code analysis to not wrap calls where the return value isn't used.

The last option seems like the best right now. It involves the least extra typing when integrating with Java code.

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

## Affine types
Essentially meant variable that can be used at most once. Might instead want vars that can be used exactly once. These types are useful for concurrency and IO management, e.g. once a File is passed into a function it can't be used again in the current scope.

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

# References

## Types
* https://lobste.rs/s/9rrxbh/on_types#c_qanywm
* https://old.reddit.com/r/ProgrammingLanguages/comments/nqm6rf/on_the_merits_of_low_hanging_fruit/h0cqvuy/
* https://www.oilshell.org/blog/2020/04/release-0.8.pre4.html#dependency-inversion-leads-to-pure-interpreters
* Row Polymorphism - https://news.ycombinator.com/item?id=13047934
* Row inference - https://gilmi.me/blog/post/2021/04/10/giml-typing-records
* Reading lang papers - https://blog.acolyer.org/2018/01/26/a-practitioners-guide-to-reading-programming-languages-papers/

### Bidirectional Typing
* Complete and easy - https://arxiv.org/pdf/1306.6032.pdf
* Ocaml impl - https://gist.github.com/mb64/87ac275c327ea923a8d587df7863d8c7#file-tychk-ml
* Rust impl - https://github.com/JDemler/BidirectionalTypechecking/blob/master/src/original.rs

## IDE
* https://rust-analyzer.github.io/blog/2020/07/20/three-architectures-for-responsive-ide.html
* https://www.youtube.com/watch?v=wSdV1M7n4gQ
* https://www.youtube.com/watch?v=N6b44kMS6OM
* https://www.youtube.com/watch?v=lubc8udiP_8&list=PLX8CzqL3ArzVnxC6PYxMlngEMv3W1pIkn&index=8
* https://github.com/pikelet-lang/pikelet/issues/103

## JVM Bytecode
* Compiling for the JVM - https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-3.html
* Classfile format - https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html
* JVM instruction set - https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-6.html
* https://wuciawe.github.io/jvm/2017/03/04/notes-on-jvm-bytecode.html

### invokedynamic
* https://blogs.oracle.com/javamagazine/understanding-java-method-invocation-with-invokedynamic
* https://blogs.oracle.com/javamagazine/behind-the-scenes-how-do-lambda-expressions-really-work-in-java
* https://silo.tips/download/structural-typing-on-the-java-virtual-machine-with-invokedynamic
* https://github.com/bpd/kale/blob/master/src/kale/runtime/Bootstrap.java
* https://docs.oracle.com/en/java/javase/16/docs/api/jdk.dynalink/module-summary.html
* https://github.com/fangyuchen86/jsr292-cookbook
* http://wiki.jvmlangsummit.com/images/9/93/2011_Forax.pdf

### Type Switch
* https://betterprogramming.pub/java-20-pattern-matching-for-switch-whats-under-the-hood-223a109f5e2f

## Performance
* https://blog.nelhage.com/post/reflections-on-performance/
* https://blog.nelhage.com/post/why-sorbet-is-fast/
* https://shipilev.net/blog/2015/black-magic-method-dispatch/

## Error Messages
* https://news.ycombinator.com/item?id=9808317
* https://elm-lang.org/news/compilers-as-assistants
* https://git.sr.ht/~awsmith/hudson/tree/master/item/src/prelude/reporting.m
