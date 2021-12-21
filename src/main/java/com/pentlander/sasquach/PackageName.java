package com.pentlander.sasquach;

public record PackageName(String name) {
  @Override
  public String toString() {
    return name;
  }
}
