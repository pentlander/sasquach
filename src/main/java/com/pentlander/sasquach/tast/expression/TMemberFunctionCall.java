package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TMemberFunctionCall(TypedExpression structExpression, Identifier functionId,
                                  List<TypedExpression> arguments, Type returnType,
                                  Range range) implements TFunctionCall {
  @Override
  public Type type() {
    return returnType;
  }
}
