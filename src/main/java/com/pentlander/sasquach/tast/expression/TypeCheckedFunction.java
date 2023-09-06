package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.type.Type;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record TypeCheckedFunction(FunctionSignature functionSignature,
                                  TypedExpression expression) implements TypedExpression {
  public TypeCheckedFunction {
    requireNonNull(functionSignature);
  }

  @Override
  public Type type() {
    return functionSignature.type();
  }

  @Override
  public Range range() {
    return functionSignature.range().join(expression.range());
  }

  public List<FunctionParameter> parameters() {
    return functionSignature.parameters();
  }

  public String toPrettyString() {
    return "%s -> %s".formatted(functionSignature.toPrettyString(), expression.toPrettyString());
  }
}
