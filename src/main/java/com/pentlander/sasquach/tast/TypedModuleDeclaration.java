package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.tast.expression.TypedStruct;
import com.pentlander.sasquach.type.Type;

public record TypedModuleDeclaration(QualifiedIdentifier id, TypedStruct struct,
                                     Range range) implements TypedNode {
  @Override
  public Type type() {
    return null;
  }
}
