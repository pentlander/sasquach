package com.pentlander.sasquach.rdparser;

import static com.pentlander.sasquach.rdparser.Scanner.TokenType.ARROW;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.COLON;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.COMMA;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.DOT;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.DOT_DOT;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.EOF;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.EQ;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.FOREIGN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_BRACK;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_CURLY;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.L_PAREN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.NAME;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.PIPE;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_BRACK;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_CURLY;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.R_PAREN;
import static com.pentlander.sasquach.rdparser.Scanner.TokenType.SLASH;

import com.pentlander.sasquach.rdparser.Parser.MarkOpened;
import com.pentlander.sasquach.rdparser.Parser.TreeKind;
import com.pentlander.sasquach.rdparser.Scanner.TokenType;
import java.util.Arrays;

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
    var mark = assertOpen(NAME);
    if (p.at(L_CURLY)) {
      struct();
    }

    p.close(mark, TreeKind.MODULE);
  }

  private void struct() {
    var mark = assertOpen(L_CURLY);

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
    var mark = assertOpen(L_BRACK);
    while (!p.at(R_BRACK) && !p.isAtEnd()) {
      if (p.eat(NAME)) {
        if (!p.at(R_BRACK)) {
          p.expect(COMMA);
        }
      } else {
        break;
      }
    }
    p.expect(R_BRACK);
    p.close(mark, TreeKind.TYPE_PARAM_LIST);
  }

  private void sumTypeDef() {
    var mark = assertOpen(PIPE);
  }

  // StructTypeMember = Name TypeAnnotation
  // StructTypeSpread = '..' NamedType?
  // StructType = '{' StructTypeMember | StructTypeSpread (',' StructTypeMember | StructTypeSpread)* '}'
  private void structType() {
    var mark = assertOpen(L_CURLY);
    while (!p.at(R_CURLY) && !p.isAtEnd()) {
      switch (p.nth(0)) {
        case NAME -> assertTree(NAME, TreeKind.STRUCT_TYPE_MEMBER, this::typeAnnotation);
        case DOT_DOT -> assertTree(DOT_DOT, TreeKind.STRUCT_TYPE_SPREAD, () -> {
          if (p.at(NAME)) namedType();
        });
        default -> {
          if (!p.isAtEnd()) p.advance();
        }
      }
    }
    p.expect(R_CURLY);
    p.close(mark, TreeKind.STRUCT_TYPE);
  }

  private void tupleType() {
    var mark = assertOpen(L_PAREN);
    while (!p.at(R_PAREN) && !p.isAtEnd()) {
      if (isTypeExprStart(p.nth(0))) {
        typeExpr();
        if (!p.at(R_PAREN)) {
          p.expect(COMMA);
        }
      } else {
        break;
      }
    }
    p.expect(R_PAREN);
    p.close(mark, TreeKind.TUPLE_TYPE);
  }

  // TypeArgList = '[' TypeExpr, (',' TypeExpr)* ']'
  private void typeArgList() {
    var mark = assertOpen(L_BRACK);
    while (!p.at(R_BRACK) && !p.isAtEnd()) {
      if (isTypeExprStart(p.nth(0))) {
        typeExpr();
        if (!p.at(R_BRACK)) {
          p.expect(COMMA);
        }
      } else {
        break;
      }
    }
    p.expect(R_BRACK);
    p.close(mark, TreeKind.TYPE_ARG_LIST);
  }

  // NamedType = Identifier ('.' Identifier)? TypeArgList?
  private void namedType() {
    var mark = assertOpen(NAME);
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
    assertTree(L_PAREN, TreeKind.FUNCTION_PARAM_LIST, () -> {
      while (!p.at(R_PAREN) && !p.isAtEnd()) {
        if (p.at(NAME)) {
          functionParam();
          if (!p.at(R_PAREN)) {
            p.expect(COMMA);
          }
        } else {
          break;
        }
      }
      p.expect(R_PAREN);
    });
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
    var mark = assertOpen(COLON);
    typeExpr();
    p.close(mark, TreeKind.TYPE_ANNOTATION);
  }

  private boolean isTypeExprStart(TokenType tokenType) {
    return switch (tokenType) {
      case L_CURLY, NAME, L_PAREN -> true;
      default -> false;
    };
  }

  // TypeExpr = StructType | NamedType | FunctionType | TupleType
  void typeExpr() {
    var mark = p.open();
    boolean isError = false;
    switch (p.nth(0)) {
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

  void parseCommaSeparated(TokenType endToken, Runnable parser) {
    while (!p.at(endToken) && !p.isAtEnd()) {
      if (p.at(NAME)) {
        functionParam();
        if (!p.at(endToken)) {
          p.expect(COMMA);
        }
      }
    }
  }

  private void qualifiedName() {
    assertTree(NAME, TreeKind.QUALIFIED_NAME, () -> {
        do {
          p.expect(SLASH);
          p.expect(NAME);
        } while (p.at(SLASH) && !p.isAtEnd());
    });
  }

  private void expr() {
    var mark = p.open();
    p.expect(L_CURLY);
    p.expect(R_CURLY);
    p.close(mark, TreeKind.EXPR);
  }

  private void structStatement() {
    var mark = p.open();
    switch (p.nth(0)) {
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

        switch (p.nth(0)) {
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
    var mark = p.open();
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
}
