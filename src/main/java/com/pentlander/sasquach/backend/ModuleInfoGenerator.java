package com.pentlander.sasquach.backend;

import static java.util.Objects.requireNonNull;

import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleRequireInfo;
import java.lang.classfile.components.ClassPrinter;
import java.lang.classfile.components.ClassPrinter.Verbosity;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.Set;

public class ModuleInfoGenerator {
  private static final ModuleRequireInfo STD_REQUIRE = ModuleRequireInfo.of(ModuleDesc.of(
      "com.sasquachlang.std"), Set.of(AccessFlag.SYNTHETIC), null);

  public ModuleAttribute runModule() {
    return ModuleAttribute.of(ModuleDesc.of("sasquach.run"), mab -> mab.requires(STD_REQUIRE));
  }

  public ModuleAttribute parseModuleAttribute(List<String> lines) {
    ModuleDesc moduleName = null;
    var lineIter = lines.iterator();
    for (var line = lineIter.next(); lineIter.hasNext(); line = lineIter.next()) {
      if (line.isBlank())
        continue;

      var parts = line.split(" ");
      if (parts[0].equals("module")) {
        moduleName = ModuleDesc.of(parts[1]);
        break;
      }
    }

    return ModuleAttribute.of(requireNonNull(moduleName), mab -> {
      for (var line = lineIter.next(); lineIter.hasNext(); line = lineIter.next()) {
        var parts = line.split(" ");
        switch (parts[0]) {
          case "requires" -> mab.requires(ModuleDesc.of(parts[1]), Set.of(), null);
          case "exports" -> mab.exports(PackageDesc.ofInternalName(parts[1]), Set.of());
        }
      }
    });
  }

  public byte[] generateModuleInfo(ModuleAttribute modAttribute) {
    var classFile = ClassFile.of();
    var bytes = classFile.buildModule(modAttribute);
    classFile.verify(bytes).stream().findFirst().ifPresent(err -> {
      ClassPrinter.toYaml(classFile.parse(bytes), Verbosity.CRITICAL_ATTRIBUTES, System.err::printf);
      throw err;
    });

    return bytes;
  }
}
