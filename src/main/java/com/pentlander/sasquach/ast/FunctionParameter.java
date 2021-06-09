package com.pentlander.sasquach.ast;

public record FunctionParameter(String name, Type type, int index) implements Expression {
}
