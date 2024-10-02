package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.typenode.FunctionSignature;
import com.pentlander.sasquach.ast.RecurPoint;
import com.pentlander.sasquach.type.TypeParameterNode;
import java.util.List;

public record Function(FunctionSignature functionSignature, Expression expression) implements
    Expression, RecurPoint {
  public Function {
    requireNonNull(functionSignature);
  }

  @Override
  public Range range() {
    return functionSignature.range().join(expression.range());
  }

  public List<FunctionParameter> parameters() {
    return functionSignature.parameters();
  }

  public List<TypeParameterNode> typeParameters() {
    return functionSignature.typeParameterNodes();
  }

  public String toPrettyString() {
    return "%s -> %s".formatted(functionSignature.toPrettyString(), expression.toPrettyString());
  }
}
