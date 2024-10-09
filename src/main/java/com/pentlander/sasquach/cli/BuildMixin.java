package com.pentlander.sasquach.cli;

import com.pentlander.sasquach.Compiler;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@NullUnmarked
public class BuildMixin {
  @Parameters(arity = "1..*", description = "source file paths")
  List<Path> sourcePaths;
  @Option(names = { "-o", "--output-path"}, defaultValue = "out")
  Path outputPath;

  void compile() {
    var compiler = new Compiler();
    compiler.compile(sourcePaths, outputPath);
  }
}
