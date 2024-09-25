package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.id.QualifiedModuleId;
import com.pentlander.sasquach.type.Type;

public sealed interface TypedUse extends TypedNode {
  QualifiedModuleId id();

  Id alias();

  Type type();

  record Module(QualifiedModuleId id, Id alias, Type type, Range range) implements
      TypedUse {}

  record Foreign(QualifiedModuleId id, Id alias, Type type, Range range) implements
      TypedUse {}

}
