package com.pentlander.sasquach;

/**
 * Error message that only contains the message.
 */
record BasicError(String message) implements Error {
  @Override
  public String toPrettyString(Sources source) {
    return message;
  }
}
