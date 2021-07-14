package com.pentlander.sasquach;

/** Exception that indicates a bug in the compiler. */
public class InternalCompilerException extends RuntimeException {
  public InternalCompilerException() {
    super();
  }

  public InternalCompilerException(String message) {
    super(message);
  }

  public InternalCompilerException(String message, Throwable cause) {
    super(message, cause);
  }

  public InternalCompilerException(Throwable cause) {
    super(cause);
  }
}
