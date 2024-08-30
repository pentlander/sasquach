package com.pentlander.sasquach.tast;

import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.TFunctionParameter.Label.None;
import com.pentlander.sasquach.tast.TFunctionParameter.Label.Some;
import com.pentlander.sasquach.tast.TFunctionParameter.Label.WithDefault;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.FunctionType.Param;
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
    return new FunctionType(
        parameters.stream().map(Param::from).toList(),
        typeParameters,
        returnType);
  }

  @Override
  public String toPrettyString() {
    var typeParams = !typeParameters.isEmpty() ? typeParameters.stream()
        .map(Node::toPrettyString)
        .collect(joining(", ", "[", "]")) : "";
    return typeParams + parameters().stream()
        .map(param -> param.name() + ": " + param.type().toPrettyString())
        .collect(joining(", ", "(", ")")) + ": " + returnType.toPrettyString();
  }
}
