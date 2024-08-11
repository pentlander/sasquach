package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TMemberFunctionCall(TypedExpression structExpression, Id functionId,
                                  FunctionType functionType, List<TypedExpression> arguments,
                                  Type returnType, Range range) implements TFunctionCall {
  public UnqualifiedName name() {
    return functionId.name();
  }

  @Override
  public Type type() {
    return returnType;
  }
}
