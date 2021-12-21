package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.CompilationUnit;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class CompilationUnitParser {
  public CompilationUnit getCompilationUnit(Source source) {
    CharStream charStream = CharStreams.fromString(source.sourceString());
    var lexer = new SasquachLexer(charStream);
    var errorListener = new SasquachTreeWalkErrorListener();
    lexer.addErrorListener(errorListener);

    var tokenStream = new CommonTokenStream(lexer);
    var parser = new SasquachParser(tokenStream);
    parser.addErrorListener(errorListener);

    var visitor = new Visitor(source.path(), new PackageName(source.packageName()));
    return parser.compilationUnit().accept(visitor.compilationUnitVisitor());
  }

  static class SasquachTreeWalkErrorListener extends BaseErrorListener {
    private final List<BasicError> compileErrors = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
        int charPositionInLine, String msg, RecognitionException e) {
      compileErrors.add(new BasicError(msg));
    }

    public List<BasicError> getCompileErrors() {
      return List.copyOf(compileErrors);
    }
  }
}
