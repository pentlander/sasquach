package com.pentlander.sasquach;

import java.util.List;

public interface RangedError extends Error {
  Range range();

  default List<? extends Range> ranges() {
    return List.of(range());
  }
}
