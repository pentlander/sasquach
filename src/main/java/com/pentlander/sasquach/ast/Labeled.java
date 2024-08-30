package com.pentlander.sasquach.ast;

import org.jspecify.annotations.Nullable;

public interface Labeled {
  @Nullable UnqualifiedName label();
}
