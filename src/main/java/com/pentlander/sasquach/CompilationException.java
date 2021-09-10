package com.pentlander.sasquach;

import java.util.List;

public class CompilationException extends Exception {
  private final List<? extends Error> errors;

  public CompilationException(Source source, List<? extends Error> errors) {
    super(errorMessage(source, errors));
    this.errors = errors;
  }

  private static String errorMessage(Source source, List<? extends Error> errors) {
    StringBuilder stringBuilder = new StringBuilder();
    for (Error compileError : errors) {
      stringBuilder.append("error: %s\n".formatted(compileError.toPrettyString(source)));
    }
    return stringBuilder.toString();
  }

  public List<? extends Error> errors() {
    return errors;
  }
}
