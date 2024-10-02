package com.pentlander.sasquach.type;

import com.pentlander.sasquach.name.UnqualifiedTypeName;

public record TypeParameter(UnqualifiedTypeName name) {
  public UniversalType toUniversal() {
    return new UniversalType(name().toString());
  }

  public TypeVariable toTypeVariable(int level) {
    return new TypeVariable(name().toString(), level);
  }

  public String toPrettyString() {
    return name().toString();
  }
}
