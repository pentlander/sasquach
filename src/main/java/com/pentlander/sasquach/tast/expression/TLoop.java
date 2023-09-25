package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.TRecurPoint;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TLoop(List<TVariableDeclaration> varDeclarations, TypedExpression expression,
                    Range range) implements TypedExpression, TRecurPoint {
  @Override
  public Type type() {
    return expression.type();
  }
}
