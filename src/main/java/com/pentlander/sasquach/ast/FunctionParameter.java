package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public record FunctionParameter(String name, Type type, int index, Range.Single identifierRange, Range.Single typeRange) implements Expression {
}
