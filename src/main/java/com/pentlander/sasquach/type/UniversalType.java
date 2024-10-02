package com.pentlander.sasquach.type;

import com.pentlander.sasquach.name.UnqualifiedTypeName;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

// It exists as a type variable for function and type
// definitions. If `TypeVariable` was used in those cases, once a function was called it could only
// ever be called again using that type. E.g. foo = [T](bar: T) -> String called with foo(10)
// would generate a function where `bar: String` instead of `Object`
public record UniversalType(String name) implements Type, TypeNester {
  @Override
  public String typeNameStr() {
    return name();
  }

  public UnqualifiedTypeName typeName() {
    return new UnqualifiedTypeName(name);
  }

  @Override
  public ClassDesc classDesc() {
    return ConstantDescs.CD_Object;
  }

  @Override
  public String internalName() {
    return Object.class.getCanonicalName().replace('.', '/');
  }

}
