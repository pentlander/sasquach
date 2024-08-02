package com.pentlander.sasquach;

import java.nio.file.Paths;
import java.util.Arrays;

public class Main {
  public static void main(String[] args) {
    try {
      var sasquachPaths = Arrays.stream(args).map(Paths::get).toList();
      var outputPath = Paths.get("out");
      var compiler = new Compiler();
      compiler.compile(sasquachPaths, outputPath);
    } catch (Exception e) {
      System.err.println(e);
      e.printStackTrace(System.err);
    }
  }

}
