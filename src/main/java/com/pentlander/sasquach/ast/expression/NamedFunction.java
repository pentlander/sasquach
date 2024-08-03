package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record NamedFunction(Id id, Function function) implements Node {
  public String name() {
    return id().name();
  }

  public Range.Single nameRange() {
    return id.range();
  }

  public FunctionSignature functionSignature() {
    return function.functionSignature();
  }

  public Type returnType() {
    return functionSignature().returnType();
  }

  public List<FunctionParameter> parameters() {
    return function.parameters();
  }

  public Expression expression() {
    return function.expression();
  }

  @Override
  public Range range() {
    return id.range().join(function.range());
  }

  public String toPrettyString() {
    return "%s = %s".formatted(id().name(), function.toPrettyString());
  }
}
