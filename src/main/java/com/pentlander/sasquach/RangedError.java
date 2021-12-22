package com.pentlander.sasquach;

import java.util.List;

/**
 * Error that refers to a range of source code.
 */
public non-sealed interface RangedError extends Error {
  Range range();

  @Override
  default String toPrettyString(Sources sources) {
    return toPrettyString(sources.getSource(range().sourcePath()));
  }

  String toPrettyString(Source source);

  default List<? extends Range> ranges() {
    return List.of(range());
  }
}
