module sasquach.main {
  requires jdk.dynalink;
  requires org.antlr.antlr4.runtime;
  requires org.jspecify;
  requires static io.soabase.recordbuilder.core;
  requires static classindex;

  opens com.pentlander.sasquach.runtime;
}
