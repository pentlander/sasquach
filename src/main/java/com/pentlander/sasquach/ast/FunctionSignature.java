package com.pentlander.sasquach.ast;

import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.type.Type;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Type signature of a function, including the function parameters, type parameters, and return
 * type.
 */
public record FunctionSignature(List<FunctionParameter> parameters, List<TypeNode> typeParameters,
                                TypeNode returnTypeNode, Range range) implements Node {
  public FunctionSignature(List<FunctionParameter> parameters, TypeNode returnTypeNode,
      Range range) {
    this(parameters, List.of(), returnTypeNode, range);
  }

  public Type returnType() {
    return returnTypeNode.type();
  }

  @Override
  public String toPrettyString() {
    var typeParams =
        !typeParameters.isEmpty() ? typeParameters.stream().map(TypeNode::toPrettyString)
            .collect(joining(", ", "[", "]")) : "";
    return typeParams + parameters().stream()
        .map(param -> param.name() + ": " + param.type().toPrettyString())
        .collect(joining(", ", "(", ")")) + ": " + returnTypeNode.toPrettyString();
  }
}
