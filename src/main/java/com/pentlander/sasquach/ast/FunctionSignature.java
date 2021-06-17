package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

import java.util.List;

public record FunctionSignature(List<FunctionParameter> parameters, Type returnType, Range.Single nameRange, Range range) {
}
