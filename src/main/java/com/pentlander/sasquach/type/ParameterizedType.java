package com.pentlander.sasquach.type;

import java.util.List;

public sealed interface ParameterizedType extends Type, TypeNester permits FunctionType, StructType,
    SumType {
  List<TypeParameter> typeParameters();
}
