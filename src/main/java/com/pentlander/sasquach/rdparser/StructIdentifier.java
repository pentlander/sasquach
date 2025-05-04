package com.pentlander.sasquach.rdparser;

import com.pentlander.sasquach.ast.typenode.NamedTypeNode;
import com.pentlander.sasquach.name.QualifiedModuleName;

sealed public interface StructIdentifier {
  None NONE = new None();

  record TypeNode(NamedTypeNode node) implements StructIdentifier {}

  record ModuleName(QualifiedModuleName name) implements StructIdentifier {}

  final class None implements StructIdentifier {
    private None() {
    }
  }
}
