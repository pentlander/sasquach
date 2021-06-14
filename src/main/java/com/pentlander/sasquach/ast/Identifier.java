package com.pentlander.sasquach.ast;

public record Identifier(String name, Type type) implements Expression {
}
