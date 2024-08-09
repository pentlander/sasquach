# Decision Log
This is a log of all the decisions I made with some significance. They are all subject to change, but they justify why I thought something was right choice at the time.

## Decisions

### Literal Struct Member Function Captures
#### Example:
```
let capture = 5
let struct = { foo: () -> capture }
```
#### Description
For literal structs, the `TFunction` inside the `TStruct` properly has a capture variables associated with it. This means the class method gets created with a type sig that includes the capture variable on the literal struct's parent class. However, the struct's constructor doesn't include a `Func`, and the `Func` with the capture never gets created. This leaves us with some options in order to properly capture the variables: 
1. Add the capture variable to the constructor, then one of the following:
   1. Add a synthetic fields for the captured variables. Then generate the function normally, the function refers to the captures via the synthetic field rather than captured in a method handle
   2. Generate a synthetic static method inside the literal struct's class that includes the captures as method parameters. Inside the struct's constructor, init a field of type Func using the synthetic static method and the capture var. 
2. Generate a static method on the structs owner class and add a `Func` parameter to the constructor. Initialize the `Func` with the captures before constructor then pass it to the constructor. 

The advantage of 1i is that the bytecode itself is more clear. The struct constructor simply includes the capture and the methods are created like normal on the class. However, this ends up needing a bunch of special casing in the bytecode generation to accomplish. Option 2 already behaves the same as how standalone functions are generated, as well as variant structs. Variant structs cannot actually have functions generated within their classes, since their type definition does not and cannot include an implementation. Also since the struct that gets generated only contains fields, it's possible to generate it at runtime instead of at compile time. This can reduce compile times and runtime memory usage.
