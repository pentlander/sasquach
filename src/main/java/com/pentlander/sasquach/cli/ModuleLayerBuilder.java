package com.pentlander.sasquach.cli;

import static java.util.Objects.requireNonNull;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class ModuleLayerBuilder {
  private final ModuleLayer baseLayer = ModuleLayer.boot();
  private final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
  @Nullable
  private String moduleName;
  @Nullable
  private String qualifiedClassName;
  @Nullable
  private Path modulePath;
  @Nullable
  private Collection<ModulePath> depModulePaths;

  public ModuleLayerBuilder moduleName(String moduleName) {
    this.moduleName = requireNonNull(moduleName);
    return this;
  }

  /**  */
  public ModuleLayerBuilder qualifiedClassName(String qualifiedClassName) {
    this.qualifiedClassName = requireNonNull(qualifiedClassName);
    return this;
  }

  public ModuleLayerBuilder modulePath(Path modulePath) {
    this.modulePath = requireNonNull(modulePath);
    return this;
  }

  public ModuleLayerBuilder depModulePaths(Collection<ModulePath> modulePaths) {
    this.depModulePaths = requireNonNull(modulePaths);
    return this;
  }

  public ModuleLayer build() {
    var parentLayers = depModulePaths.stream().map(this::newModuleLayer).toList();
    if (parentLayers.isEmpty()) {
      parentLayers = List.of(baseLayer);
    }
    var moduleFinder = ModuleFinder.of(modulePath);
    var layerConfig = Configuration.resolve(
        moduleFinder,
        parentLayers.stream().map(ModuleLayer::configuration).toList(),
        ModuleFinder.of(),
        Set.of(moduleName));
    var layerController = ModuleLayer.defineModulesWithOneLoader(
        layerConfig,
        parentLayers,
        classLoader);
    var layer = layerController.layer();

    var thisModule = getClass().getModule();
    var targetModule = layer.findModule(moduleName).orElseThrow();
    // If the whole module is open, no need to open a specific package.
    if (!targetModule.getDescriptor().isOpen()) {
      var targetPackage = qualifiedClassName.substring(0, qualifiedClassName.lastIndexOf("."));
      layerController.addOpens(targetModule, targetPackage, thisModule);
    }
    thisModule.addReads(targetModule);
    return layer;
  }

  public record ModulePath(String moduleName, List<Path> jarPaths) {}

  private ModuleLayer newModuleLayer(ModulePath modulePath) {
    var moduleFinder = ModuleFinder.of(modulePath.jarPaths().toArray(new Path[]{}));
    var layerConfig = baseLayer.configuration()
        .resolve(moduleFinder, ModuleFinder.of(), Set.of(modulePath.moduleName()));
    return baseLayer.defineModulesWithOneLoader(layerConfig, classLoader);
  }
}
