package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import java.util.List;

public sealed interface FunctionCall extends Expression permits ForeignFunctionCall,
    LocalFunctionCall, MemberFunctionCall {
  Identifier functionId();

  default String name() {
    return functionId().name();
  }

  List<Expression> arguments();

  default int argumentCount() {
    return arguments().size();
  }

  Range range();
}
