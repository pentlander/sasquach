package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import java.util.ArrayList;

public record PipeOperator(Expression expression, FunctionCall functionCall,
                           Range range) implements Expression {
  @Override
  public String toPrettyString() {
    return expression().toPrettyString() + " |> " + functionCall().toPrettyString();
  }

  public FunctionCall toFunctionCall() {
    var id = functionCall().functionId();
    var range = functionCall().range();
    // Prepend the arg provided to the operator to the args list
    var args = new ArrayList<Expression>();
    args.add(expression());
    args.addAll(functionCall().arguments());
    return switch (functionCall()) {
      case LocalFunctionCall ignored -> new LocalFunctionCall(id, args, range);
      case ForeignFunctionCall call -> new ForeignFunctionCall(call.classAlias(), id, args, range);
      case MemberFunctionCall call -> new MemberFunctionCall(call.structExpression(),
          id,
          args,
          range);
    };
  }
}
