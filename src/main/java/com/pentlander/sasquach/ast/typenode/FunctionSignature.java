package com.pentlander.sasquach.ast.typenode;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.expression.FunctionParameter;
import com.pentlander.sasquach.type.FunctionType;
import com.pentlander.sasquach.type.TypeParameterNode;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Type signature of a function, including the function parameters, type parameters, and return
 * type.
 */
public record FunctionSignature(List<FunctionParameter> parameters,
                                List<TypeParameterNode> typeParameterNodes,
                                @Nullable TypeNode returnTypeNode, Range range) implements Node,
    TypeNode {
  public FunctionSignature(List<FunctionParameter> parameters, TypeNode returnTypeNode,
      Range range) {
    this(parameters, List.of(), returnTypeNode, range);
  }

  @Override
  public FunctionType type() {
    var returnType = requireNonNull(returnTypeNode, "Return type must not be null").type();
    return new FunctionType(
        parameters.stream().map(FunctionType.Param::from).toList(),
        typeParameterNodes.stream().map(TypeParameterNode::toTypeParameter).toList(),
        returnType);
  }

  @Override
  public String toPrettyString() {
    var typeParams = !typeParameterNodes.isEmpty() ? typeParameterNodes.stream()
        .map(Node::toPrettyString)
        .collect(joining(", ", "[", "]")) : "";
    var returnTypeStr = returnTypeNode != null ?  ": " + returnTypeNode.toPrettyString() : "";
    return typeParams + parameters().stream()
        .map(FunctionParameter::toPrettyString)
        .collect(joining(", ", "(", ")")) + returnTypeStr;
  }
}
