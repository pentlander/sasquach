package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import java.util.List;

public sealed interface TFunctionCall extends TypedExpression permits TForeignFunctionCall,
    TLocalFunctionCall, TMemberFunctionCall {
  Identifier functionId();

  List<TypedExpression> arguments();

  Range range();
}
