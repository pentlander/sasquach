package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.name.UnqualifiedName;
import org.jspecify.annotations.Nullable;

public interface Labeled {
  @Nullable UnqualifiedName label();
}
