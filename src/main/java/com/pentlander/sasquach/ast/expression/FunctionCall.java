package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;
import java.util.List;
import java.util.stream.Collectors;

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

  default String argumentsToPrettyString() {
    return arguments().stream().map(Node::toPrettyString)
        .collect(Collectors.joining(", ", "(", ")"));
  }
}
