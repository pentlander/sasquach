package com.pentlander.sasquach.rdparser;

import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.rdparser.Parser.Child.ChildTree;
import com.pentlander.sasquach.rdparser.Parser.Tree;
import com.pentlander.sasquach.rdparser.Parser.TreeKind;
import com.pentlander.sasquach.rdparser.Scanner.TokenType;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SasquachParserTest {
  @Test
  void tuple() {
    var tree = parse("""
        Test {
          use std/Option;
          use std/Int;
          
          type Box[A] = { item: A; ..A },
          
          type Id = (String),
          
          test0 = (true,
          test1 = (true,),
          test2 = (true, 30),
          test = (foo) -> true;
        }
          
        Bar {
          type T = String,
        }
        """);
    System.out.println(tree.treeString());
  }

  @Test
  void tuple2() {
    var tree = parse("""
        Test {
          test0 = (true,
          test1 = (true,),
          test2 = (true, 30),
          test = (foo) -> true,
        }
          
        Bar {
          type T = String,
        }
        """);
    System.out.println(tree.treeString());
  }

  @Nested
  class TypeExprTest {
    @Test
    void tuple() {
      var tree = parse(SasquachParser::typeExpr, "(Int, String)");
      System.out.println(tree.treeString());
    }

    @Test
    void function() {
      var tree = parse(SasquachParser::typeExpr, "(Int, String) -> Void");
      System.out.println(tree.treeString());
    }
  }

  private static Tree parse(String source) {
    var scanner = new Scanner(Source.fromString("main", source));
    var parser = new Parser(scanner.scanTokens());
    var sasqParser = new SasquachParser(parser);
    sasqParser.compilationUnit();
    return parser.buildTree();
  }

  private static Tree parse(Consumer<SasquachParser> parse, String source) {
    var scanner = new Scanner(Source.fromString("main", source));
    var parser = new Parser(scanner.scanTokens());
    var mark = parser.open();
    var sasqParser = new SasquachParser(parser);
    parse.accept(sasqParser);
    parser.expect(TokenType.EOF);
    parser.close(mark, TreeKind.COMP_UNIT);
    return ((ChildTree) parser.buildTree().children().getFirst()).tree();
  }
}
