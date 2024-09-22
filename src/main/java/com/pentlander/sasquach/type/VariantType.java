package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.StructName;
import java.lang.constant.ClassDesc;

public sealed interface VariantType extends Type permits SingletonType, StructType {
  StructName name();

  ClassDesc internalClassDesc();

  FunctionType constructorType(ParameterizedType returnType);
}
