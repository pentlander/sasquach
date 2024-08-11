package com.pentlander.sasquach;

public record PackageName(String name) {
  public PackageName {
    if (name.contains(".")) {
      throw new IllegalStateException("Package name cannot contain '.', must be separated by '/'");
    }
  }

  @Override
  public String toString() {
    return name;
  }
}
