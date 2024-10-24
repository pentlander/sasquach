package com.pentlander.sasquach.type;

import com.pentlander.sasquach.name.UnqualifiedTypeName;

public record TypeParameter(UnqualifiedTypeName name) {
  public UniversalType toUniversal() {
    return new UniversalType(name().toString());
  }

  public String toPrettyString() {
    return name().toString();
  }
}
