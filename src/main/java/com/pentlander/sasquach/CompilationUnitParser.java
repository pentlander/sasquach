package com.pentlander.sasquach;

import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.parser.CompileResult;
import com.pentlander.sasquach.parser.SasquachLexer;
import com.pentlander.sasquach.parser.SasquachParser;
import com.pentlander.sasquach.parser.CompilationUnitVisitor;
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
  public CompileResult<CompilationUnit> parse(Source source) {
    CharStream charStream = CharStreams.fromString(source.sourceString());
    var lexer = new SasquachLexer(charStream);
    var errorListener = new SasquachTreeWalkErrorListener(source);
    lexer.removeErrorListeners();
    lexer.addErrorListener(errorListener);

    var tokenStream = new CommonTokenStream(lexer);
    var parser = new SasquachParser(tokenStream);
    parser.addErrorListener(errorListener);

    var visitor = new CompilationUnitVisitor(source.path(), new PackageName(source.packageName()));
    return parser.compilationUnit().accept(visitor);
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
      var tokenLen = token != null ? token.getText().length() : 0;
      var range = new Single(source.path(),
          new Position(line, charPositionInLine + 1),
          tokenLen);
      System.err.println(msg);
      System.err.println(source.highlight(range));
      compileErrors.add(new BasicError(msg, range));
    }

    public List<BasicError> getCompileErrors() {
      return List.copyOf(compileErrors);
    }
  }
}
