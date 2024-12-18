package com.pentlander.sasquach.cli;

import com.pentlander.sasquach.Compiler.Result;
import java.util.concurrent.Callable;
import org.jspecify.annotations.NullUnmarked;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@NullUnmarked
@Command(name = "run")
public class Run implements Callable<Integer> {
  @Mixin
  BuildMixin buildMixin;

  @Option(names = {"--main-module", "-m"}, required = true)
  String mainModule;

  @Override
  public Integer call() throws Exception {
    if (buildMixin.compile() == Result.FAILURE) {
      return 1;
    }

    var builder = new ProcessBuilder(
        "java",
        "--class-path",
        buildMixin.outputPath.toString(),
        mainModule.replace('/', '.')).inheritIO();

    return builder.start().waitFor();
  }
}
