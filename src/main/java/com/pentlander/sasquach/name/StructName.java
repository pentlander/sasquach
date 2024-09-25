package com.pentlander.sasquach.name;

import com.pentlander.sasquach.name.StructName.SyntheticName;

public sealed interface StructName extends Name permits QualifiedTypeName,
    SyntheticName, UnqualifiedTypeName {
  UnqualifiedTypeName simpleName();
  record SyntheticName(StructName innerName) implements StructName {
    public static SyntheticName unqualified(String name) {
      return new SyntheticName(new UnqualifiedTypeName(name));
    }

    @Override
    public UnqualifiedTypeName simpleName() {
      return innerName.simpleName();
    }

    @Override
    public String toString() {
      return innerName.toString();
    }
  }
}
