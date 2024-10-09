package com.pentlander.sasquach.cli;

import picocli.CommandLine.Command;

@Command(subcommands = {
    Build.class,
    Run.class
})
public class Cli {}
