package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.type.ForeignFunctionType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TForeignFunctionCall(Id classAlias, Id functionId,
                                   ForeignFunctionType foreignFunctionType,
                                   List<TypedExpression> arguments, Type returnType,
                                   Range range) implements TFunctionCall {
  @Override
  public Type type() {
    return returnType;
  }
}
