package com.pentlander.sasquach;

import com.pentlander.sasquach.cli.Cli;
import picocli.CommandLine;

public class Main {
  public static void main(String[] args) {
    System.exit(new CommandLine(new Cli()).execute(args));
  }
}
