package com.pentlander.sasquach.rdparser;

import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.SourcePath;
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
          use std/Option,
          use std/Int,
        
          type Box[A] = { item: A, ..A },
        
          type Id = (String),
        
          test0 = (true),
          test1 = (true,),
          test2 = (true, 30),
          test = (foo) -> true,
        }
        
        Bar {
          type T = String,
        }
        """);
    System.out.println(tree.treeString());
    var result = AstBuilder.build(new PackageName("test"), new SourcePath("test.sasq"), tree);
    assertThat(result.errors().errors()).isEmpty();
  }

  @Test
  void ref() {
    var tree = parse("""
        Iterator {
          use std/Option,
          use std/Ref,

          typealias T[A] = { next: () -> Option.T[A], .. },

          new = [A](next: () -> Option.T[A]): T[A] -> { next = next },

          next = [A](iter: T[A]): Option.T[A] -> iter.next(),

          map = [A, B](iter: T[A], fn: (item: A) -> B): T[B] -> new(() ->
            iter |> next() |> Option.map(fn)
          ),

          filter = [A](iter: T[A], fn: (item: A) -> Boolean): T[A] ->
            new(() ->
              loop () -> match next(iter) {
                Option.Some(item) -> if fn(item) Option.Some(item) else recur(),
                Option.None -> Option.None,
              }
            ),

          filterMap = [A, B](iter: T[A], fn: (item: A) -> Option.T[B]): T[B] ->
            new(() ->
              loop () -> match next(iter) {
                Option.Some(item) -> match fn(item) {
                  Option.Some(mappedItem) -> Option.Some(mappedItem),
                  Option.None -> recur(),
                },
                Option.None -> Option.None,
              }
            ),

          flatMap = [A, B](iter: T[A], fn: (item: A) -> T[B]): T[B] -> {
            let nestedIterRef = Ref.new(Option.None)
            new(() -> {
              // Option.T[T[B]]
              Ref.get(nestedIterRef)
                |> Option.orElse(() ->
                  next(iter) |> Option.map((item) -> {
                    let newNestedIter = fn(item)
                    Ref.set(nestedIterRef, Option.Some(newNestedIter))
                    newNestedIter
                  })
                )
                |> Option.flatMap((nestedIter) -> next(nestedIter))
            })
          },

          reduce = [A, B](iter: T[A], init: B, accumulator: (accum: B, value: A) -> B): B ->
            loop (let accum = init) -> match next(iter) {
              Option.Some(item) -> recur(accumulator(accum, item)),
              Option.None -> accum,
            },

          forEach = [A](iter: T[A], fn: (item: A) -> Void): Void -> {
            loop () -> match next(iter) {
              Option.Some(item) -> {
                fn(item)
                recur()
              },
              Option.None -> {},
            }
          },
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
          test2 = {
            (true, 30)
          },
          test = (foo) -> true,
        }
          
        Bar {
          type T = String,
        }
        """);
    System.out.println(tree.treeString());
  }

  @Test
  void namedStruct() {
    var tree = parse("""
        Test {
          test = Foo { bar = 1 + 2, baz = "hello" },
        }
        """);
    System.out.println(tree.treeString());
  }

  @Test
  void funcApplication() {
    var tree = parse("""
        Test {
          test = foo(bar, 10, 5),
        }
        """);
    System.out.println(tree.treeString());
  }

  /**
   * Need to decide when an end of line should complete a statement. It's only relevant when the
   * parser is in a binary statement, in other cases it's unambiguous.
   */
  @Test
  void funcApplication_block() {
    var tree = parse("""
        Test {
          test = {
            foo
            (bar, 10, 5)
          }
        }
        """);
    System.out.println(tree.treeString());
  }

  @Test
  void memberAccess() {
    var tree = parse("""
        Test {
          test = {
            Foo.T {
              bar = true
            }
            foo.bar
            { true }
          }
        }
        """);
    System.out.println(tree.treeString());
  }

  @Test
  void funcApplication_noargs() {
    var tree = parse("""
        Test {
          test = foo(),
        }
        """);
    System.out.println(tree.treeString());
  }

  @Test
  void infix() {
    var tree = parse("""
        Test {
          test = 3 + 4 * 4,
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
      var tree = parse(SasquachParser::typeExpr, "(i: Int, s: String) -> Void");
      System.out.println(tree.treeString());
    }
  }

  private static Tree parse(String source) {
    var scanner = new Scanner(Source.fromString("main", source));
    var result = scanner.scanTokens();
    var parser = new Parser(result.tokens(), result.newlineIndexes());
    var sasqParser = new SasquachParser(parser);
    sasqParser.compilationUnit();
    return parser.buildTree();
  }

  private static Tree parse(Consumer<SasquachParser> parse, String source) {
    var scanner = new Scanner(Source.fromString("main", source));
    var result = scanner.scanTokens();
    var parser = new Parser(result.tokens(), result.newlineIndexes());
    var mark = parser.open();
    var sasqParser = new SasquachParser(parser);
    parse.accept(sasqParser);
    parser.expect(TokenType.EOF);
    parser.close(mark, TreeKind.COMP_UNIT);
    return ((ChildTree) parser.buildTree().children().getFirst()).tree();
  }
}
