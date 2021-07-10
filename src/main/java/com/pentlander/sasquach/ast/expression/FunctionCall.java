package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.Expression;
import java.util.List;

public interface FunctionCall extends Expression {
  Identifier functionId();

  default String name() {
    return functionId().name();
  }

  List<Expression> arguments();

  Range range();
}
