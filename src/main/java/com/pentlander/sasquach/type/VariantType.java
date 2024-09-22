package com.pentlander.sasquach.type;

import java.lang.constant.ClassDesc;

public sealed interface VariantType extends Type permits SingletonType, StructType {
  ClassDesc internalClassDesc();

  FunctionType constructorType(ParameterizedType returnType);
}
