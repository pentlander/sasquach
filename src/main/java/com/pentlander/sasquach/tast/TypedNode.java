package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public interface TypedNode {
  Type type();
  Range range();

  default String toPrettyString() {
    return toString();
  }
}
