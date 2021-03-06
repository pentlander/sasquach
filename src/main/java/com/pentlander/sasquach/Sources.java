package com.pentlander.sasquach;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Sources {
  private final Map<SourcePath, Source> pathToSource;

  private Sources(Map<SourcePath, Source> pathToSource) {
    this.pathToSource = pathToSource;
  }

  public static Sources empty() {
    return new Sources(Map.of());
  }

  public static Sources single(Source source) {
    return new Sources(Map.of(source.path(), source));
  }

  public static Sources fromMap(Map<SourcePath, Source> sources) {
    return new Sources(Map.copyOf(sources));
  }

  public Sources merge(Sources other) {
    var sources = new HashMap<>(pathToSource);
    sources.putAll(other.pathToSource);
    return Sources.fromMap(sources);
  }

  public Source getSource(SourcePath path) {
    return Objects.requireNonNull(pathToSource.get(path), "No source with path: " + path);
  }

  public Collection<Source> values() {
    return pathToSource.values();
  }
}
