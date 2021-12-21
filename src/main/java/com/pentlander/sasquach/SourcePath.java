package com.pentlander.sasquach;

import java.nio.file.Path;

public record SourcePath(String filepath) {
  public static SourcePath fromPath(Path path) {
    return new SourcePath(path.toString());
  }
}
