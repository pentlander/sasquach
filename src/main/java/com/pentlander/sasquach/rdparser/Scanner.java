package com.pentlander.sasquach.rdparser;

import static com.pentlander.sasquach.rdparser.Scanner.TokenType.*;
import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Character.isJavaIdentifierStart;
import static java.util.Map.entry;

import com.pentlander.sasquach.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class Scanner {
  private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
      entry("if", IF),
      entry("else", ELSE),
      entry("match", MATCH),
      entry("let", LET),
      entry("print", PRINT),
      entry("foreign", FOREIGN),
      entry("use", USE),
      entry("type", TYPE),
      entry("typealias", TYPEALIAS),
      entry("loop", LOOP),
      entry("true", TRUE),
      entry("false", FALSE)
  );

  private final Source source;
  private final List<Token> tokens = new ArrayList<>();
  private final List<Integer> newlineStrIndexes = new ArrayList<>();
  private final List<Integer> newlineTokenIndexes = new ArrayList<>();
  private int start = 0;
  private int current = 0;
  private int line = 0;

  public Scanner(Source source) {
    this.source = source;
  }

  private String sourceStr() {
    return source.sourceString();
  }

  private boolean isAtEnd() {
    return current >= sourceStr().length();
  }

  private boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private char advance() {
    return sourceStr().charAt(current++);
  }

  private void addToken(TokenType type) {
    addToken(type, null);
  }

  private void addToken(TokenType type, @Nullable Object literal) {
    String text = currentSubstring();
    tokens.add(new Token(type, text, literal, line));
  }

  private boolean match(char expected) {
    if (isAtEnd()) return false;
    if (sourceStr().charAt(current) != expected) return false;

    current++;
    return true;
  }

  private TokenType match(char expected, TokenType matches, TokenType notMatches) {
    return match(expected) ? matches : notMatches;
  }

  private char peek() {
    return !isAtEnd() ? sourceStr().charAt(current) : '\0';
  }

  private char peekNext() {
    if (current + 1 >= sourceStr().length()) return '\0';
    return sourceStr().charAt(current + 1);
  }

  private void addString() {
    while (peek() != '"' && peek() != '\n' && !isAtEnd()) {
      advance();
    }

    if (isAtEnd()) {
      addToken(ERROR_TOKEN);
      return;
    } else if (peek() == '\n') {
      addToken(ERROR_TOKEN);
      return;
    }

    // The closing "
    advance();

    // Trim the surrounding quotes.
    String value = sourceStr().substring(start + 1, current - 1);
    addToken(STRING, value);
  }

  private void addNumber() {
    while (isDigit(peek())) advance();

    // Look for a fractional part.
    if (peek() == '.' && isDigit(peekNext())) {
      // Consume the "."
      advance();

      while (isDigit(peek())) advance();
      addToken(NUMBER, Double.parseDouble(currentSubstring()));
    } else {
      addToken(NUMBER, Long.parseLong(currentSubstring()));
    }
  }

  private String currentSubstring() {
    return sourceStr().substring(start, current);
  }

  private void addIdentifier() {
    while (isJavaIdentifierPart(peek()) && !isAtEnd()) advance();

    var text = currentSubstring();
    var tokenType = KEYWORDS.getOrDefault(text, NAME);
    addToken(tokenType);
  }

  private void scanToken() {
    char c = advance();
    switch (c) {
      case '(' -> addToken(L_PAREN);
      case ')' -> addToken(R_PAREN);
      case '{' -> addToken(L_CURLY);
      case '}' -> addToken(R_CURLY);
      case '[' -> addToken(L_BRACK);
      case ']' -> addToken(R_BRACK);
      case ',' -> addToken(COMMA);
      case ':' -> addToken(COLON);
      case '+' -> addToken(PLUS);
      case '*' -> addToken(STAR);
      case '#' -> addToken(POUND);
      case '-' -> addToken(match('>', ARROW, MINUS));
      case '.' -> addToken(match('.', DOT_DOT, DOT));
      case '!' -> addToken(match('=', BANG_EQ, BANG));
      case '=' -> addToken(match('=', EQ_EQ, EQ));
      case '<' -> addToken(match('=', LT_EQ, LT));
      case '>' -> addToken(match('=', GT_EQ, GT));
      case '/' -> {
        if (match('/')) {
          while (peek() != '\n' && !isAtEnd()) advance();
        } else {
          addToken(SLASH);
        }
      }
      case '|' -> {
        if (match('|')) {
          addToken(PIPE_PIPE);
        } else if (match('>')) {
          addToken(PIPE_OP);
        } else {
          addToken(PIPE);
        }
      }
      case '&' -> {
        if (match('&')) {
          addToken(AMP_AMP);
        } else {
          addToken(ERROR_TOKEN);
        }
      }
      case '"' -> addString();
      case '\n' -> {
        newlineStrIndexes.add(current);
        newlineTokenIndexes.add(tokens.size());
        line++;
      }
      case ' ', '\r', '\t' -> {}
      default -> {
        if (isDigit(c)) {
          addNumber();
        } else if (isJavaIdentifierStart(c)) {
          addIdentifier();
        } else {
          addToken(ERROR_TOKEN);
        }
      }
    }
  }

  Result scanTokens() {
    while (!isAtEnd()) {
      // We are at the beginning of the next lexeme.
      start = current;
      scanToken();
    }

    tokens.add(new Token(EOF, "", null, line));

    return new Result(tokens, Set.copyOf(newlineTokenIndexes));
  }

  record Result(List<Token> tokens,  Set<Integer> newlineIndexes) {}

  record Token(TokenType type, String lexeme, @Nullable Object literal, int line) {
    @Override
    public String toString() {
      var str = literal != null ? type + "[" + literal + "]" : "'" + lexeme + "'";
      return str + " @ " + line;
    }
  }
  enum TokenType {
    PLUS, MINUS, STAR, SLASH, EQ, BANG, COMMA, DOT, POUND, PIPE, COLON,
    LT, GT, L_PAREN, R_PAREN, L_CURLY, R_CURLY, L_BRACK, R_BRACK,

    PIPE_OP, ARROW,
    LT_EQ, GT_EQ, EQ_EQ, BANG_EQ,
    AMP_AMP, PIPE_PIPE, DOT_DOT, SLASH_SLASH,

    IF, ELSE, MATCH, LET, PRINT, FOREIGN, USE, TYPE, TYPEALIAS, LOOP,
    TRUE, FALSE,

    NAME, STRING, NUMBER,

    ERROR_TOKEN, EOF;

    @Override
    public String toString() {
      return switch (this) {
        case PLUS -> "+";
        case MINUS -> "-";
        case STAR -> "*";
        case SLASH -> "/";
        case EQ -> "=";
        case BANG -> "!";
        case COMMA -> ",";
        case DOT -> ".";
        case POUND -> "#";
        case PIPE -> "|";
        case COLON -> ":";
        case LT -> "<";
        case GT -> ">";
        case L_PAREN -> "(";
        case R_PAREN -> ")";
        case L_CURLY -> "{";
        case R_CURLY -> "}";
        case L_BRACK -> "[";
        case R_BRACK -> "]";
        case PIPE_OP -> "|>";
        case ARROW -> "->";
        case LT_EQ -> "<=";
        case GT_EQ -> ">=";
        case EQ_EQ -> "==";
        case BANG_EQ -> "!=";
        case AMP_AMP -> "&&";
        case PIPE_PIPE -> "||";
        case DOT_DOT -> "..";
        case SLASH_SLASH -> "//";
        case IF -> "if";
        case ELSE -> "else";
        case MATCH -> "match";
        case LET -> "let";
        case PRINT -> "print";
        case FOREIGN -> "foreign";
        case USE -> "use";
        case TYPE -> "type";
        case TYPEALIAS -> "typealias";
        case LOOP -> "loop";
        case TRUE -> "true";
        case FALSE -> "false";
        case NAME -> "Name";
        case STRING -> "String";
        case NUMBER -> "Number";
        case ERROR_TOKEN -> "Error";
        case EOF -> "EOF";
      };
    }
  }
}
