package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.LocalVariable;

public record PatternVariable(Id id) implements LocalVariable {
  @Override
  public Range range() {
    return id.range();
  }
}
