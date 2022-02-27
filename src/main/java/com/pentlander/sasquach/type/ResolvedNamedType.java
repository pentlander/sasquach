package com.pentlander.sasquach.type;

import java.util.List;

public sealed interface ResolvedNamedType extends Type, ParameterizedType permits ResolvedLocalNamedType,
    ResolvedModuleNamedType {
  Type type();
  List<Type> typeArgs();
}
