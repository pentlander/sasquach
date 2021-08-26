module sasquach.main {
  requires java.compiler;
  requires org.antlr.antlr4.runtime;
  requires org.objectweb.asm;
  requires com.fasterxml.jackson.annotation;
  requires com.fasterxml.jackson.databind;
  requires static io.soabase.recordbuilder.core;
  requires static classindex;

  opens com.pentlander.sasquach.runtime;
}