package com.pentlander.sasquach.ast.expression;

public sealed interface StructWithName extends Struct permits ModuleStruct, NamedStruct {
  String name();
}
