package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

import java.util.List;

public record FunctionSignature(List<FunctionParameter> parameters, Type returnType, Range range) implements Node {
}
