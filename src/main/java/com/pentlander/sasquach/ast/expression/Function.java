package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.FunctionParameter;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Scope;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.type.Type;
import org.antlr.v4.runtime.misc.Nullable;

import java.util.List;

public record Function(Scope scope, Identifier id, FunctionSignature functionSignature,
                       Expression expression) implements Expression {
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

  @Nullable
  public Expression returnExpression() {
    return expression;
  }
}
