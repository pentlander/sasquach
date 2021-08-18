package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Scope;
import com.pentlander.sasquach.type.Type;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record Function(Scope scope, Identifier id, FunctionSignature functionSignature,
                       Expression expression) implements Expression {
  public Function {
    requireNonNull(id);
    requireNonNull(functionSignature);
  }

  public String name() {
    return id.name();
  }

  @Override
  public Range range() {
    return nameRange().join(expression.range());
  }

  public Range.Single nameRange() {
    return id.range();
  }

  public Type returnType() {
    return functionSignature.returnType();
  }

  public List<FunctionParameter> parameters() {
    return functionSignature.parameters();
  }

  public String toPrettyString() {
    return "%s = %s -> %s".formatted(id().name(), functionSignature.toPrettyString(),
        expression.toPrettyString());
  }
}
