package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Argument;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.name.UnqualifiedName;
import java.util.List;
import java.util.stream.Collectors;

public sealed interface FunctionCall extends Expression permits ForeignFunctionCall,
    LocalFunctionCall, MemberFunctionCall {
  Id functionId();

  default UnqualifiedName name() {
    return functionId().name();
  }

  List<Argument> arguments();

  Range range();

  default String argumentsToPrettyString() {
    return arguments().stream().map(Node::toPrettyString)
        .collect(Collectors.joining(", ", "(", ")"));
  }
}
