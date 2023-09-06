package com.pentlander.sasquach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class EndToEndTest {
  @Test
  void addition() throws Exception {
    var source = Source.fromString("main", """
        Main {
          plus = (): Int -> 3 + 4
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "plus", null);

    assertThat(sum).isEqualTo(7);
  }

  @Test
  void booleanExpr() throws Exception {
    var source = Source.fromString("main", """
        Main {
          foo = (): Boolean -> false && true || true
        }
        """);
    var clazz = compileClass(source, "main/Main");
    boolean bool = invokeName(clazz, "foo", null);

    assertThat(bool).isEqualTo(true);
  }

  @Test
  void tuple() throws Exception {
    var source = Source.fromString("main", """
        Main {
          tuplify = [A, B](a: A, b: B): (A, B) -> (a, b),
          foo = (): Int -> {
            let tuple = tuplify(5, "something")
            tuple._0
          },
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "foo", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void loopRecur() throws Exception {
    var source = Source.fromString("main", """
        Main {
          plus = (): Int -> {
            loop (let a = 0) -> if (a > 4) {
              a
            } else {
              recur(a + 1)
            }
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "plus", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void higherOrderFunc() throws Exception {
    var source = Source.fromString("main", """
        Main {
          plus = (): Int -> {
            let add = (a: Int, b: Int): Int -> a + b
            add(1, 4)
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "plus", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void applyOperator() throws Exception {
    var source = Source.fromString("main", """
        Main {
          inc = (i: Int): Int -> i + 1,
          timesTwo = (i: Int): Int -> i * 2,
          
          foo = (): Int -> {
            let add = (a: Int, b: Int): Int -> a + b
            10 |> inc() |> timesTwo() |> add(5)
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "foo", null);

    assertThat(sum).isEqualTo(27);
  }

  @Test
  void matchSumType() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Option[T] = | Some(T) | None,
          
          foo = (): String -> {
            let option = if (true) Some("foo") else None
            match option {
              Some(str) -> str,
              None -> "",
            }
          },
        }
        """);
    var clazz = compileClassDebug(source, "main/Main");
    String sum = invokeName(clazz, "foo", null);

    assertThat(sum).isEqualTo("foo");
  }

  // should fail
  @Test
  void matchSumType_sameParam_multipleVariants() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Same[T] = | A(T) | B(T),
          
          foo = (): String -> {
            let option = if (true) A("foo") else B(10)
            match option {
              A(str) -> str,
              B(int) -> int,
            }
          },
        }
        """);
    var ex = assertThrows(CompilationException.class, () -> compileClass(source, "main/Main"));
    assertThat(ex).hasMessageContaining("should be 'String'");
  }

  @Test
  void sumTypeSingleton() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Option[T] = | Some(T) | None,
          
          identity = [T](option: Option[T]): Option[T] -> option,
          
          foo = (): Option[String] -> {
            let opt = None
            let ident = identity(None)
            ident
          },
        }
        """);
    var clazz = compileClass(source, "main/Main");
    Object singleton = invokeName(clazz, "foo", null);

    assertThat(singleton.getClass().getName()).isEqualTo("main.Main$None");
  }

  @Test
  @Disabled
    // Need to generate an interface for the sum type or more likely generate a class that takes a
    // function object in the constructor
  void sumTypeWithFunc() throws Exception {
    // let none = identity(None)
    var source = Source.fromString("main", """
        Main {
          type Option[T] = | Test { foo: (bar: Int) -> String },
          
          identity = [T](option: Option[T]): Option[T] -> option,
          
          foo = (): String -> {
            let some = Test { foo = (bar: Int): String -> Int.toString(bar) }
            some._0
          },
        }
        """);
    var clazz = compileClass(source, "main/Main");
    String sum = invokeName(clazz, "foo", null);

    assertThat(sum).isEqualTo("foo");
  }

  @Test
  void foreignFunctionCall_withField() throws Exception {
    var source = Source.fromString("main", """
        Main {
          use foreign java/io/PrintStream,
          use foreign java/lang/System,
          
          foo = (): Void -> {
            PrintStream#println(System#out, "test")
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    invokeName(clazz, "foo", null);
  }

  @Test
  void foreignFunctionCall_mapGet() throws Exception {
    var source = Source.fromString("main", """
        Main {
          use foreign java/util/HashMap,
          
          foo = (): String -> {
            let map = HashMap#new()
            HashMap#put(map, "foo", "bar")
            HashMap#get(map, "foo")
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    String value = invokeName(clazz, "foo", null);

    assertThat(value).isEqualTo("bar");
  }

  @Test
  void typeAliasStruct() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Point = { x: Int, y: Int },
          newPoint = (x: Int): Point -> { x = x, y = 4 },
          getX = (point: Point): Int -> point.x,
          main = (): Int -> getX(newPoint(5))
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeAliasStructs() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Point = { x: Int, y: Int },
          newPoint = (x: Int): Point -> { x = x, y = 2 },
          getX = (point: Point): Int -> point.x,
          main = (): Int -> getX(newPoint(5))
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeAliasFunction() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type MathFunc = (x: Int, y: Int) -> Int,
          getX = (x: Int, func: MathFunc): Int -> func(x, 6),
          main = (): Int -> getX(3, (x: Int, y: Int): Int -> x + y)
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(9);
  }

  // Add test for local named type where the type name doesn't exist

  @Test
  void typeAliasStruct_importModule() throws Exception {
    var source = Source.fromString("main", """
        Point {
          type T = { x: Int, y: Int },
          
          new = (x: Int): T -> { x = x, y = 4 }
        }
                
        Main {
          use main/Point,
          
          getX = (point: Point.T): Int -> point.x,
          main = (): Int -> {
            getX(Point.new(5))
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int sum = invokeName(clazz, "main", null);

    assertThat(sum).isEqualTo(5);
  }

  @Test
  void typeCheckForeignParamType() throws Exception {
    var source = Source.fromString("main", """
        Main {
          use foreign java/util/ArrayList,
          use foreign java/util/Iterator,
          
          stringFn = (str: String): Int -> 5,
          main = (): { value: Int } -> {
            let list = ArrayList#new()
            ArrayList#add(list, "foo")
            let iter = ArrayList#iterator(list)
            
            let n = iter |> Iterator#next() |> stringFn()
            { value = n }
          },
        }
        """);
    var clazz = compileClass(source, "main/Main");
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 5);
  }

  @Test
  void complexParameterizedExpression() throws Exception {
    var source = Source.fromString("main", """
        Main {
          map = [T, U](value: T, mapper: { map: (inp: T) -> U }): { value: U } ->
              { value = mapper.map(value) },
          main = (): { value: Int } -> map("foobar", { map = (inp: String): Int -> 10 })
        }
        """);
    var clazz = compileClass(source, "main/Main");
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  @Test
  void complexParamaterizedExpression_multiModule() throws Exception {
    var source = Source.fromString("main", """
        Box {
          type T[A] = { value: A },
          
          new = [A](value: A): T[A] -> { value = value },
          unbox = [A](box: T[A]): A -> box.value,
        }
                
        Main {
          use main/Box,
                
          main = (): Int -> {
            let box = Box.new(10)
            Box.unbox(box)
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    int boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).isEqualTo(10);
  }

  @Test
  void complexParameterizedExpression_sameTypeVarName() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Box[A] = { value: A },
                
          new = [B](value: B): Box[B] -> { value = value },
          main = (): Box[Int] -> {
            let boxInt = new(10)
            let boxStr = new("hello")
            boxInt
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  @Test
  void complexParameterizedExpression_sameTypeVarNamee() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Box[A] = { value: A },
                
          new = [B](value: B): Box[B] -> { value = value },
          map = [A, B](box: Box[A], mapper: (value: A) -> B): Box[B] -> { value = mapper(box.value) },
          main = (): String -> {
            let box = new(10) |> map((n: Int): String -> "hi")
            box.value
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    String str = invokeName(clazz, "main", null);

    assertThat(str).isEqualTo("hi");
  }

  @Test
  void complexParameterizedExpression_sameAliasParamName() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type Box[A] = { value: A },
          type BoxTwo[A, B] = { one: A, two: Box[B] },
                
          new = [A, B](one: A, two: B): BoxTwo[A, B] -> {
            one = one,
            two = { value = two },
          },
          main = (): Box[String] -> {
            let box = new(10, "hello")
            box.two
          }
        }
        """);
    var clazz = compileClass(source, "main/Main");
    Object boxedStr = invokeName(clazz, "main", null);

    assertThat(boxedStr).hasFieldOrPropertyWithValue("value", "hello");
  }

  @Test
  void complexParameterizedExpression_withAlias() throws Exception {
    var source = Source.fromString("main", """
        Main {
          type T[A] = { value: A },
          type Mapper[A, B] = { map: (inp: A) -> B },
                
          map = [A, B](value: A, mapper: Mapper[A, B]): T[B] ->
              { value = mapper.map(value) },
          main = (): T[Int] -> map("foo", { map = (inp: String): Int -> 10 })
        }
        """);
    var clazz = compileClass(source, "main/Main");
    Object boxedInt = invokeName(clazz, "main", null);

    assertThat(boxedInt).hasFieldOrPropertyWithValue("value", 10);
  }

  private Class<?> compileClass(Source source, String qualifiedName)
      throws ClassNotFoundException, CompilationException {
    return compileClass(source, qualifiedName, false);
  }

  private Class<?> compileClassDebug(Source source, String qualifiedName)
      throws ClassNotFoundException, CompilationException {
    return compileClass(source, qualifiedName, true);
  }

  private Class<?> compileClass(Source source, String qualifiedName, boolean dumpClasses)
      throws ClassNotFoundException, CompilationException {
    var bytecode = new Compiler().compile(source);
    var cl = new SasquachClassloader();
    if (dumpClasses) {
      BytecodeGeneratorTest.dumpGeneratedClasses(bytecode.generatedBytecode());
    }
    bytecode.generatedBytecode().forEach(cl::addClass);
    return cl.loadClass(qualifiedName.replace('/', '.'));
  }

  @SuppressWarnings("unchecked")
  private <T> T invokeName(Class<?> clazz, String name, Object obj, Object... args)
      throws Exception {
    for (var method : clazz.getMethods()) {
      if (method.getName().equals(name)) {
        return (T) method.invoke(obj, args);
      }
    }
    throw new NoSuchMethodException();
  }
}
