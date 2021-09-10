package com.pentlander.sasquach;

import java.nio.file.Paths;

public class Main {
  public static void main(String[] args) {
    try {
      var sasquachPath = Paths.get(args[0]);
      var outputPath = Paths.get("out");
      var compiler = new Compiler();
      compiler.compile(sasquachPath, outputPath);
    } catch (Exception e) {
      System.err.println(e);
      e.printStackTrace();
    }
  }

}