import org.jspecify.annotations.NullMarked;

@NullMarked
module sasquach.main {
  requires java.compiler;
  requires jdk.dynalink;
  requires org.jspecify;
  requires info.picocli;
  requires static io.soabase.recordbuilder.core;
  requires static classindex;

  opens com.pentlander.sasquach.runtime;
  opens com.pentlander.sasquach.runtime.bootstrap;
  opens com.pentlander.sasquach.cli;
}
