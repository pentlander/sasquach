package com.pentlander.sasquach;

import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.ast.CompilationUnit;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class CompilationUnitParser {
  public CompilationUnit getCompilationUnit(Source source) {
    CharStream charStream = CharStreams.fromString(source.sourceString());
    var lexer = new SasquachLexer(charStream);
    var errorListener = new SasquachTreeWalkErrorListener(source);
    lexer.removeErrorListeners();
    lexer.addErrorListener(errorListener);

    var tokenStream = new CommonTokenStream(lexer);
    var parser = new SasquachParser(tokenStream);
    parser.addErrorListener(errorListener);

    var visitor = new Visitor(source.path(), new PackageName(source.packageName()));
    return parser.compilationUnit().accept(visitor.compilationUnitVisitor());
  }

  static class SasquachTreeWalkErrorListener extends BaseErrorListener {
    private final Source source;
    private final List<BasicError> compileErrors = new ArrayList<>();

    SasquachTreeWalkErrorListener(Source source) {
      this.source = source;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
        int charPositionInLine, String msg, RecognitionException e) {
      var token = (CommonToken) offendingSymbol;
      System.err.println(msg);
      System.err.println(source.highlight(new Single(source.path(), new Position(line,
          charPositionInLine + 1),
          token.getText().length())));
      compileErrors.add(new BasicError(msg));
    }

    public List<BasicError> getCompileErrors() {
      return List.copyOf(compileErrors);
    }
  }
}
