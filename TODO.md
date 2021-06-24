# Todo List
* Unify FunctionCall and ForeignFunctionCall
* Helper method for creating Identifier from fieldName and varReference contexts
* Update imports to use Identifier instead of string names
* Remove type() from Expression, move the type resolution into a separate class
* Create a separate TypeNode that wraps a type. It refers to any types explicitly written out
* Support multiple modules per file
* Enable imports from other modules
* Figure out how to dispatch methods on structs that have a superset of the fields required
