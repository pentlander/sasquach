package com.pentlander.sasquach.tast;

import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.List;

/**
 * Type signature of a function, including the function parameters, type parameters, and return
 * type.
 */
public record TFunctionSignature(List<TFunctionParameter> parameters,
                                 List<TypeParameter> typeParameters, Type returnType,
                                 Range range) implements TypedNode {

  @Override
  public FunctionType type() {
    return new FunctionType(parameters.stream().map(TFunctionParameter::type).toList(),
        typeParameters,
        returnType);
  }

  @Override
  public String toPrettyString() {
    var typeParams = !typeParameters.isEmpty() ? typeParameters.stream()
        .map(TypeNode::toPrettyString)
        .collect(joining(", ", "[", "]")) : "";
    return typeParams + parameters().stream()
        .map(param -> param.name() + ": " + param.type().toPrettyString())
        .collect(joining(", ", "(", ")")) + ": " + returnType.toPrettyString();
  }
}
