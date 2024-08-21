package com.pentlander.sasquach.e2e;

import static com.pentlander.sasquach.TestUtils.invokeMain;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.pentlander.sasquach.BaseTest;
import com.pentlander.sasquach.CompilationException;
import com.pentlander.sasquach.Compiler;
import com.pentlander.sasquach.PackageName;
import com.pentlander.sasquach.SasquachClassloader;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.Sources;
import com.pentlander.sasquach.TestUtils;
import com.pentlander.sasquach.ast.QualifiedModuleName;
import com.pentlander.sasquach.backend.BytecodeResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Objects;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class StdlibTest extends BaseTest {

  @Test
  void test() throws Exception {
    var clazz = compile("""
        Main {
           main = (): Int -> 1
        }
        """);

  }

  @Nested
  class MapTest extends BaseStdTest {
    @Test
    void assocAndGet() throws Exception {
      var clazz = compile("""
        Main {
          use std/Hash,
          use std/Map,
          use std/Option,
        
          main = (): Int -> {
            let hash = Hash.new((value: String): Int -> 0)
            let map = Map.new(hash)
            
            let mapWithKey = map |> Map.assoc("foo", 1)
            
            mapWithKey
              |> Map.get("foo")
              |> Option.unwrap()
          }
        }
        """);

      int value = invokeMain(clazz);
      assertThat(value).isEqualTo(1);
    }
  }

  private static class BaseStdTest extends BaseTest {
    @Override
    protected int defaultOpts() {
      return INCLUDE_STD;
    }
  }
}
