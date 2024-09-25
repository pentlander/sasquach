package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.tast.expression.TLocalVariable;
import com.pentlander.sasquach.type.Type;

public record TPatternVariable(Id id, Type type) implements TLocalVariable {
  @Override
  public Range range() {
    return id.range();
  }
}
