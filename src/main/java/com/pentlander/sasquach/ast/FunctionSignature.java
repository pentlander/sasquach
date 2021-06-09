package com.pentlander.sasquach.ast;

import java.util.List;

public record FunctionSignature(String name, List<FunctionParameter> parameters, Type returnType) {
}
