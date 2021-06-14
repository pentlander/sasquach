package com.pentlander.sasquach;

record BasicError(String message) implements Error {
  @Override
  public String toPrettyString(Source source) {
    return message;
  }
}
