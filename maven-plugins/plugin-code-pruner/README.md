# Eclipse plugin code pruner

A standalone Maven plugin for Tycho 5.0.4 `eclipse-plugin` projects. It uses JDT's
binding-resolving AST parser and Tycho's resolved bundle classpath and source roots
to remove unreachable Java types, methods and fields from a selected source folder.
Java 21 or newer and Maven 3.9.12 or newer are required to build the plugin.

Build and install without building the Eclipse SDK:

```sh
mvn -f maven-plugins/plugin-code-pruner/pom.xml clean install
```

Configure the plugin in the **consumer bundle's** POM:

```xml
<plugin>
  <groupId>org.eclipse.platform</groupId>
  <artifactId>plugin-code-pruner-maven-plugin</artifactId>
  <version>4.42.0-SNAPSHOT</version>
  <configuration>
    <sourceFolder>vendor</sourceFolder>
    <dryRun>true</dryRun>
    <sourceLevel>21</sourceLevel>
    <keepTypes>
      <keepType>com.example.vendor.ReflectiveEntryPoint</keepType>
    </keepTypes>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>prune</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

The default phase is `process-sources`, after Tycho resolves the target platform
and before compilation. Run `mvn process-sources` to inspect the dry-run log.
After reviewing it, use `mvn process-sources -Dpruner.dryRun=false` to apply changes
(omit the explicit `<dryRun>true</dryRun>` configuration to allow the CLI override).
The goal can also be invoked directly:

```sh
mvn org.eclipse.platform:plugin-code-pruner-maven-plugin:4.42.0-SNAPSHOT:prune \
  -Dpruner.sourceFolder=vendor -Dpruner.sourceLevel=21
```

## Reachability and safety

All declarations in the plugin's other source folders, and explicit `keepTypes`,
are roots. References are followed transitively by **binding identity**, including
overloads, fields, constructor calls and method references. Unreachable cycles are
removed. A kept type retains its members and nested types, and their dependencies.
If the selected folder contains all plugin sources, there are no Java entry points
unless `keepTypes` are supplied.

Constructors, abstract/overriding methods, enum constants, annotation members, and
fields with potentially effectful initializers are retained when their containing
type is reachable. Unused fields initialized to compile-time constants or `null`
can be removed.
Initializers and anonymous/local classes are analyzed but not pruned individually.
This conservatively preserves dispatch contracts and initialization side effects;
it is declaration reachability, not control-flow optimization. References from
unmodified imports outside the selected folder are also retained. Imports of
removed declarations in modified files are removed.

**Only Java references are roots.** Exported packages, `Bundle-Activator`,
`plugin.xml`, declarative-services XML, reflection, serialization and JNI are not
automatically considered. Supply `keepTypes` for those entry points and review
the dry run before modifying sources. This tool cannot prove the absence of
dynamic use or preserve binary compatibility for other bundles automatically.

The selected folder must be inside a Tycho source root and the bundle project.
Symlinked or overlapping source roots and source exclusions are rejected.
The entire plugin must parse without compilation errors using the resolved
classpath. Proposed sources are parsed again in temporary source roots before
any original is changed, including during a dry run. This is not a substitute for
running the consumer's Tycho build and runtime tests afterward.

Source files are edited **in place**, and Java files with no remaining types are
deleted; non-Java files and sources outside the selected folder are not changed.
Use version control to review/revert modifications. Files are checked for
concurrent edits before applying the plan, but filesystem failures during writes
can still leave a partially applied plan.

`pruner.sourceLevel` defaults to Tycho's `javacSource`, then the running JDK's
language level; configure it explicitly for BREE/toolchain builds.
`pruner.encoding` defaults to `project.build.sourceEncoding`, then UTF-8.
The running JDK supplies JDT's system library; use the consumer's intended JDK.
Reactor dependencies must already have compiled output available to JDT; prefer
`mvn verify` over `process-sources` when building dependent bundles together.
