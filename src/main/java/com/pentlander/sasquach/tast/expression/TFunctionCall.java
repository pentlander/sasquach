package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public sealed interface TFunctionCall extends TypedExpression permits TConstructorCall,
    TForeignFunctionCall, TLocalFunctionCall, TMemberFunctionCall {
  UnqualifiedName name();

  List<TypedExpression> arguments();

  Type returnType();

  @Override
  default Type type() {
    return returnType();
  }

  Range range();
}
