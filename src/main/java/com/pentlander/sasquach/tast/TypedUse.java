package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.type.Type;

public sealed interface TypedUse extends TypedNode {
  QualifiedIdentifier id();

  Identifier alias();
  Type type();

  record Module(QualifiedIdentifier id, Identifier alias, Type type, Range range) implements TypedUse {}
  record Foreign(QualifiedIdentifier id, Identifier alias, Type type, Range range) implements TypedUse {}

}
