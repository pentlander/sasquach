package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;

import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.id.Id;
import java.util.List;

public record LocalFunctionCall(Id functionId, List<Argument> arguments,
                                Range range) implements FunctionCall {
  @Override
  public String toPrettyString() {
    return functionId.name() + argumentsToPrettyString();
  }
}
