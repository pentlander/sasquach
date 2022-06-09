package com.pentlander.sasquach.type;

// It exists as a type variable for function and type
// definitions. If `TypeVariable` was used in those cases, once a function was called it could only
// ever be called again using that type. E.g. foo = [T](bar: T) -> String called with foo(10)
// would generate a function where `bar: String` instead of `Object`
public record UniversalType(String name, int level) implements Type, ParameterizedType {
  @Override
  public String typeName() {
    return name();
  }

  @Override
  public Class<?> typeClass() {
    return Object.class;
  }

  @Override
  public String descriptor() {
    return Object.class.descriptorString();
  }

  @Override
  public String internalName() {
    return Object.class.getCanonicalName().replace('.', '/');
  }
}
