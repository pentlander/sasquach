package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.name.StructName;

public sealed interface StructWithName extends Struct permits ModuleStruct, NamedStruct {
  StructName name();
}
