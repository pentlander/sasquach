package com.pentlander.sasquach.parser;

import com.pentlander.sasquach.Position;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.SourcePath;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

public interface RangeHelper {

  SourcePath sourcePath();

  default Range rangeFrom(ParserRuleContext context) {
    Token start = context.getStart();
    Token end = context.getStop();
    var pos = new Position(start.getLine(), start.getCharPositionInLine());
    if (start.getLine() == end.getLine()) {
      return new Range.Single(sourcePath(),
          pos,
          end.getCharPositionInLine() - start.getCharPositionInLine() + 1);
    }
    return new Range.Multi(sourcePath(),
        pos,
        new Position(end.getLine(), end.getCharPositionInLine() + end.getText().length()));
  }

  default Range.Single rangeFrom(Token token) {
    return new Range.Single(sourcePath(),
        new Position(token.getLine(), token.getCharPositionInLine()),
        token.getText().length());
  }

  default Range.Single rangeFrom(TerminalNode node) {
    return rangeFrom(node.getSymbol());
  }
}
