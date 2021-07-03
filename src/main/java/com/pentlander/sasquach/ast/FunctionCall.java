package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.List;

public interface FunctionCall extends Expression {
  Identifier functionId();

  default String name() {
    return functionId().name();
  }

  List<Expression> arguments();

  Range range();
}
