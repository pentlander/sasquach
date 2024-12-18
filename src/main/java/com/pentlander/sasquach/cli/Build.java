package com.pentlander.sasquach.cli;

import java.util.concurrent.Callable;
import org.jspecify.annotations.NullUnmarked;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@NullUnmarked
@Command(name = "build")
public class Build implements Callable<Integer> {
  @Mixin
  BuildMixin buildMixin;

  @Override
  public Integer call() {
    return switch (buildMixin.compile()) {
      case SUCCESS -> 0;
      case FAILURE -> 1;
    };
  }
}
