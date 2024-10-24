package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.QualifiedModuleId;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.tast.expression.TStruct;
import com.pentlander.sasquach.type.Type;

public record TModuleDeclaration(QualifiedModuleId id, TStruct struct, Range range) implements
    TypedNode {
  public QualifiedModuleName moduleName() {
    return id.name();
  }

  @Override
  public Type type() {
    return struct.type();
  }
}
