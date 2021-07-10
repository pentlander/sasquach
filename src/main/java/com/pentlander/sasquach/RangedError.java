package com.pentlander.sasquach;

import java.util.List;

/**
 * Error that refers to a range of source code.
 */
public interface RangedError extends Error {
  Range range();

  default List<? extends Range> ranges() {
    return List.of(range());
  }
}
