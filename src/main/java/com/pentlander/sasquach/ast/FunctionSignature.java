package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

import java.util.List;

public record FunctionSignature(List<FunctionParameter> parameters,
                                List<TypeNode> typeParameters, TypeNode returnTypeNode,
                                Range range) implements Node {
    public FunctionSignature(List<FunctionParameter> parameters, TypeNode returnTypeNode,
        Range range) {
        this(parameters, List.of(), returnTypeNode, range);
    }

    public Type returnType() {
        return returnTypeNode.type();
    }
}
