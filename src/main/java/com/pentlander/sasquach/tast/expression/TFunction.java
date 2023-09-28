package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.tast.TRecurPoint;
import com.pentlander.sasquach.type.FunctionType;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
// Need to include both the signature and function type in preparation for not requiring
// parameters types and return type for lambdas
// TODO: consolidate function signature and function type. Should create a TFunctionSignature
//  that requires type params and update FunctionSignature to not require type params, since
//  type parameters for lambdas are not required
public record TFunction(FunctionSignature functionSignature, FunctionType type,
                        TypedExpression expression) implements TypedExpression, TRecurPoint {
  public TFunction {
    requireNonNull(functionSignature);
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
