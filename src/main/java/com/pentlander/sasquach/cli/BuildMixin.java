package com.pentlander.sasquach.cli;

import com.pentlander.sasquach.Compiler;
import com.pentlander.sasquach.Compiler.Result;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullUnmarked;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@NullUnmarked
public class BuildMixin {
  @Parameters(arity = "1..*", description = "source file paths")
  List<Path> sourcePaths;
  @Option(names = { "-o", "--output-path"}, defaultValue = "out")
  Path outputPath;

  Result compile() {
    var compiler = new Compiler(Set.of());
    return compiler.compile(sourcePaths, outputPath);
  }
}
