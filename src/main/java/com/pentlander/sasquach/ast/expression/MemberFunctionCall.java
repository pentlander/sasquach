package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.Id;
import java.util.List;

public record MemberFunctionCall(Expression structExpression, Id functionId,
                                 List<Argument> arguments, Range range) implements FunctionCall {
  @Override
  public String toPrettyString() {
    return structExpression().toPrettyString() + "." + functionId().name() + argumentsToPrettyString();
  }

}
