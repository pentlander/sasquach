package com.pentlander.sasquach.rdparser;

import static com.pentlander.sasquach.rdparser.Scanner.TokenType.ARROW;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.COLON;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.COMMA;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.DOT;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.DOT_DOT;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.EOF;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.EQ;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.FOREIGN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.LET;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_BRACK;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_CURLY;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_PAREN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.NAME;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.PIPE;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_BRACK;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_CURLY;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_PAREN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.SLASH;

import com.pentlander.sasquach.Preconditions;
import com.pentlander.sasquach.rdparser.Parser.BacktrackException;
import com.pentlander.sasquach.rdparser.Parser.MarkOpened;
import com.pentlander.sasquach.rdparser.Parser.TreeKind;
import com.pentlander.sasquach.rdparser.Scanner.TokenType;
import java.util.Arrays;
import java.util.function.Supplier;

public class SasquachParser {
  private final Parser p;

  public SasquachParser(Parser parser) {
    this.p = parser;
  }

  void compilationUnit() {
    var mark = p.open();
    while (!p.isAtEnd()) {
      if (p.at(NAME)) {
        module();
      } else if (!p.eat(EOF)) {
        p.advanceWithError("expected a module name");
      }
    }
    p.close(mark, TreeKind.COMP_UNIT);
  }

  private void module() {
    // Module identifier
    var mark = expectOpen(NAME);
    if (p.at(L_CURLY)) {
      struct();
    }

    p.close(mark, TreeKind.MODULE);
  }

  private void struct() {
    var mark = expectOpen(L_CURLY);

    while (!p.at(R_CURLY) && !p.isAtEnd()) {
      structStatement();
      if (!p.at(R_CURLY)) {
        p.expect(COMMA);
      }
    }
    p.expect(R_CURLY);
    p.close(mark, TreeKind.STRUCT);
  }

  // TypeParamList = '[' Identifier (',' Identifier)* ']'
  private void typeParamList() {
    var mark = expectOpen(L_BRACK);
    commaSeparated(R_BRACK, () -> p.eat(NAME), () -> {});
    p.close(mark, TreeKind.TYPE_PARAM_LIST);
  }

  private void sumTypeDef() {
    var mark = expectOpen(PIPE);
  }

  // StructTypeMember = Name TypeAnnotation
  // StructTypeSpread = '..' NamedType?
  // StructType = '{' StructTypeMember | StructTypeSpread (',' StructTypeMember | StructTypeSpread)* '}'
  private void structType() {
    var mark = expectOpen(L_CURLY);
    boolean shouldContinue = true;
    while (!p.at(R_CURLY) && !p.isAtEnd() && shouldContinue) {
      switch (p.peek()) {
        case NAME -> assertTree(NAME, TreeKind.STRUCT_TYPE_MEMBER, this::typeAnnotation);
        case DOT_DOT -> assertTree(DOT_DOT, TreeKind.STRUCT_TYPE_SPREAD, () -> {
          if (p.at(NAME)) namedType();
        });
        default -> shouldContinue = false;
      }
      if (!p.at(R_CURLY)) {
        p.expect(COMMA);
      }
    }
    p.expect(R_CURLY);
    p.close(mark, TreeKind.STRUCT_TYPE);
  }

  private void tupleType() {
    var mark = expectOpen(L_PAREN);
    commaSeparated(R_PAREN, this::isTypeExprStart, this::typeExpr);
    p.close(mark, TreeKind.TUPLE_TYPE);
  }

  // TypeArgList = '[' TypeExpr, (',' TypeExpr)* ']'
  private void typeArgList() {
    var mark = expectOpen(L_BRACK);
    commaSeparated(R_BRACK, this::isTypeExprStart, this::typeExpr);
    p.close(mark, TreeKind.TYPE_ARG_LIST);
  }

  // NamedType = Identifier ('.' Identifier)? TypeArgList?
  private void namedType() {
    var mark = expectOpen(NAME);
    if (p.eat(DOT)) {
      p.expect(NAME);
    }

    if (p.at(L_BRACK)) {
      typeArgList();
    }
    p.close(mark, TreeKind.NAMED_TYPE);
  }


  // FunctionParam = Identifier? Identifier TypeAnnotation? ('=' Expression)?
  private void functionParam() {
    // Either the label or the param name
    assertTree(NAME, TreeKind.FUNCTION_PARAM, () -> {
      // If consume the next Identifier as well in case the first was a label
      p.eat(NAME);
      if (p.at(COLON)) {
        typeAnnotation();
      }
      if (p.eat(EQ)) {
        expr();
      }
    });
  }

  // FunctionParamList = '(' FunctionParam? (',' FunctionParam)* ')'
  private void functionParamList() {
    assertTree(
        L_PAREN,
        TreeKind.FUNCTION_PARAM_LIST,
        () -> commaSeparated(R_PAREN, () -> p.at(NAME), this::functionParam));
  }

  // FunctionType = FunctionParamList '->' TypeExpr
  private void functionType() {
    var mark = p.open();
    functionParamList();
    p.expect(ARROW);
    typeExpr();
    p.close(mark, TreeKind.FUNCTION_TYPE);
  }

  // TypeAnnotation = ':' TypeExpr
  private void typeAnnotation() {
    var mark = expectOpen(COLON);
    typeExpr();
    p.close(mark, TreeKind.TYPE_ANNOTATION);
  }

  private boolean isTypeExprStart() {
    return switch (p.peek()) {
      case L_CURLY, NAME, L_PAREN -> true;
      default -> false;
    };
  }

  // TypeExpr = StructType | NamedType | FunctionType | TupleType
  void typeExpr() {
    checkStart(this::isTypeExprStart, "type expr");

    var mark = p.open();
    boolean isError = false;
    switch (p.peek()) {
      // StructType
      case L_CURLY -> structType();
      // NamedType
      case NAME -> namedType();
      // FunctionType | TupleType
      case L_PAREN -> {
        switch (p.nth(1)) {
          case EOF -> {}
          // Must
          case R_PAREN -> functionType();
          // Could still be either a function type or a tuple type
          case NAME -> {
            switch (p.nth(2)) {
              //  foo followed by '[A]' | ',' means it must be a tuple type.
              case L_BRACK, COMMA, R_PAREN -> tupleType();
              default -> functionType();
            }
          }
          // '(' must be followed by a label or param name in a function type, so it must be a tuple
          default -> tupleType();
        }
      }
      default -> isError = true;
    }
    p.close(mark, !isError ? TreeKind.TYPE_EXPR : TreeKind.ERROR_TREE);
  }

  void commaSeparated(TokenType endToken, Supplier<Boolean> checkShouldParse, Runnable parser) {
    while (!p.at(endToken) && !p.isAtEnd()) {
      if (checkShouldParse.get()) {
        parser.run();
        if (!p.at(endToken)) {
          p.expect(COMMA);
        }
      } else {
        break;
      }
    }
    p.expect(endToken);
  }

  private void qualifiedName() {
    assertTree(NAME, TreeKind.QUALIFIED_NAME, () -> {
        do {
          p.expect(SLASH);
          p.expect(NAME);
        } while (p.at(SLASH) && !p.isAtEnd());
    });
  }

  private void varDecl() {
    assertTree(LET, TreeKind.VAR_DECL, () -> {
      p.expect(NAME);
      if (p.at(COLON)) {
        typeAnnotation();
      }
      p.expect(EQ);
      expr();
    });
  }

  private boolean isPatternStart() {
    return p.peek() == NAME;
  }
  private void pattern() {
    checkStart(this::isPatternStart, "pattern");

    var mark = p.open();
    namedType();
    // Named tuple pattern
    if (p.eat(L_PAREN)) {
      commaSeparated(R_PAREN, () -> p.at(NAME), p::advance);
    } else if (p.eat(L_CURLY)) {
      commaSeparated(R_CURLY, () -> p.at(NAME), p::advance);
    }
    p.close(mark, TreeKind.PATTERN);
  }

  private void funcExpr() {
    assertAt(L_PAREN);
    functionParamList();
    if (p.eat(COLON)) {
      typeExpr();
    }
    p.expect(ARROW);
    expr();
  }


  private boolean isExprStart() {
    return switch (p.peek()) {
      case TRUE, FALSE, NUMBER, STRING, LOOP, MATCH, L_PAREN, L_CURLY -> true;
      default -> false;
    };
  }

  public boolean isBlockStatementStart() {
    return switch (p.peek()) {
      case LET, PRINT -> true;
      default -> isExprStart();
    };
  }

  private void blockStatement() {
    var mark = p.open();
    switch (p.peek()) {
      case LET -> {
        p.advance();
        p.expect(NAME);
        if (p.at(COLON)) {
          typeAnnotation();
        }
        p.expect(EQ);
        expr();
        p.close(mark, TreeKind.VAR_DECL);
      }
      case PRINT -> {
        p.advance();
        expr();
        p.close(mark, TreeKind.PRINT_STMT);
      }
      default -> {
        if (isExprStart()) {
          expr();
          p.close(mark, TreeKind.EXPR);
        } else {
          throw new IllegalStateException("Must check if valid start before calling");
        }
      }
    }
  }

  private void expr() {
    exprDelimited();
  }

  private void exprDelimited() {
    checkStart(this::isExprStart, "expr");

    var mark = p.open();
    switch (p.peek()) {
      case TRUE, FALSE, NUMBER, STRING -> {
        p.advance();
        p.close(mark, TreeKind.EXPR_LITERAL);
      }
      case LOOP -> {
        p.advance();
        p.expect(L_PAREN);
        commaSeparated(R_PAREN, () -> p.at(LET), this::varDecl);
        p.expect(ARROW);
        expr();
        p.close(mark, TreeKind.EXPR_LOOP);
      }
      case MATCH -> {
        p.advance();
        expr();
        p.expect(L_CURLY);
        commaSeparated(R_CURLY, this::isPatternStart, this::pattern);
        p.close(mark, TreeKind.EXPR_MATCH);
      }
      // ParenExpr | TupleExpr | FuncExpr
      case L_PAREN -> {
        if (tryParse(this::funcExpr)) {
          p.close(mark, TreeKind.EXPR_FUNC);
        } else {
          p.advance();
          expr();
          if (p.eat(COMMA)) {
            commaSeparated(R_PAREN, this::isExprStart, this::expr);
            p.close(mark, TreeKind.EXPR_TUPLE);
          } else if (p.eat(R_PAREN)) {
            p.close(mark, TreeKind.EXPR_PAREN);
          } else {
            p.advanceWithError(mark, "failed");
          }
        }
      }
      case L_CURLY -> {
        p.advance();
        while (!p.at(R_CURLY) && !p.isAtEnd()) {
          if (isBlockStatementStart()) {
            blockStatement();
          } else {
            break;
          }
        }
        p.expect(R_CURLY);
        p.close(mark, TreeKind.EXPR_BLOCK);
      }
      default -> p.advanceWithError(mark, "failed");
    }
  }

  private void structStatement() {
    var mark = p.open();
    switch (p.peek()) {
      case USE -> {
        p.advance();
        p.eat(FOREIGN);
        qualifiedName();
      }
      case TYPE, TYPEALIAS -> {
        p.advance();
        p.expect(NAME);
        if (p.at(L_BRACK)) {
          typeParamList();
        }
        p.expect(EQ);

        switch (p.peek()) {
          case PIPE -> sumTypeDef();
          default -> typeExpr();
        }
      }
      // Member definition
      case NAME -> {
        p.advance();
        p.expect(EQ);
        expr();
      }
      // Spread operator
      case DOT_DOT -> {
        p.advance();
        p.expect(NAME);
      }
      default -> {
        if (!p.isAtEnd()) p.advance();
      }
    }
    p.close(mark, TreeKind.STRUCT_STATEMENT);
  }

  private MarkOpened assertOpen(TokenType tokenType) {
    assertAt(tokenType);
    return p.open();
  }

  private MarkOpened expectOpen(TokenType tokenType) {
    var mark = assertOpen(tokenType);
    p.expect(tokenType);
    return mark;
  }

  private void assertTree(TokenType tokenType, TreeKind treeKind, Runnable runnable) {
    assertAt(tokenType);
    var mark = p.open();
    p.expect(tokenType);
    runnable.run();
    p.close(mark, treeKind);
  }

  private void assertAt(TokenType... tokenTypes) {
    for (var type : tokenTypes) {
      if (p.at(type)) return;
    }
    throw new IllegalStateException("Expected to be at token type '%s', found: %s".formatted(Arrays.toString(
        tokenTypes), p.peekToken()));
  }

  // Check that the method used to check if the starting token is valid matches the actual implementation
  private void checkStart(Supplier<Boolean> startPred, String checkName) {
    Preconditions.checkState(startPred.get(), "Does not match " + checkName + " start");
  }

  private boolean tryParse(Runnable parser) {
    p.checkpoint();
    try {
      parser.run();
      p.clearCheckpoint();
      return true;
    } catch (BacktrackException e) {
      p.restore();
      return false;
    }
  }
}
