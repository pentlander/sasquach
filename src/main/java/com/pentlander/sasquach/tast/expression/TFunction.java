package com.pentlander.sasquach.tast.expression;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.TFunctionParameter;
import com.pentlander.sasquach.tast.TFunctionSignature;
import com.pentlander.sasquach.tast.TRecurPoint;
import com.pentlander.sasquach.type.FunctionType;
import java.util.List;
import java.util.stream.Stream;

// Need to include both the signature and function type in preparation for not requiring
// parameters types and return type for lambdas
public record TFunction(TFunctionSignature functionSignature, TypedExpression expression, List<TLocalVariable> captures) implements
    TypedExpression, TRecurPoint {
  public TFunction {
    requireNonNull(functionSignature);
  }

  public FunctionType type() {
    return functionSignature.type();
  }

  public FunctionType typeWithCaptures() {
    var captureTypes = captures.stream().map(TLocalVariable::variableType);
    var sigParamTypes = functionSignature.parameters().stream().map(TFunctionParameter::type);
    var paramTypes = Stream.concat(captureTypes, sigParamTypes).toList();
    return new FunctionType(
        paramTypes,
        functionSignature.typeParameters(),
        functionSignature.returnType());
  }

  @Override
  public Range range() {
    return functionSignature.range().join(expression.range());
  }

  public List<TFunctionParameter> parameters() {
    return functionSignature.parameters();
  }

  public String toPrettyString() {
    return "%s -> %s".formatted(functionSignature.toPrettyString(), expression.toPrettyString());
  }
}
