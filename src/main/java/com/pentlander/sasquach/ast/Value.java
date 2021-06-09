package com.pentlander.sasquach.ast;

public record Value(Type type, String value) implements Expression {
}
