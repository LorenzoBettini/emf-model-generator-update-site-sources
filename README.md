# EMF Model Generator Tycho update site

This is a multi-module Maven/Tycho 5.0.3 build that creates a p2 update site for the EMF Model Generator.

## Modules

- `emf.model.generator.targetplatform`: Eclipse target definition using the Eclipse 2026-06 repository and EMF runtime bundles.
- `emf.model.generator.feature`: feature containing the EMF Model Generator bundle.
- `emf.model.generator.feature.source`: source feature containing the source bundle.
- `emf.model.generator.repository`: p2 repository/update site with categories.

## Build

```sh
mvn clean verify
```

The update site is produced in:

```text
emf.model.generator.repository/target/repository/
```

You can override the EMF Model Generator release version with:

```sh
mvn clean verify -Demf.model.generator.version=1.0.0
```

## Important source-bundle note

The main EMF Model Generator bundle currently uses `emf-model-generator` as its `Bundle-SymbolicName`, so the runtime feature includes the plug-in `emf-model-generator`.

The source feature assumes the Maven Central `sources` classifier is a real Eclipse source bundle with:

```text
Bundle-SymbolicName: emf-model-generator.source
Eclipse-SourceBundle: emf-model-generator;version="..."
```

If the deployed `sources` artifact is only a normal Maven sources JAR, Tycho will not be able to use it as a proper Eclipse source bundle with `pomDependencies=consider`. In that case, either publish an actual source bundle upstream or adjust `emf.model.generator.feature.source/feature.xml` to the real source-bundle symbolic name.

## Why the Maven dependency is in the parent POM

The Maven Central artifacts are declared in the parent POM because Tycho's `pomDependencies=consider` mechanism is project-scoped: downstream Tycho modules must all see the Maven OSGi bundles in their own target platform. The `.target` file defines the p2/Eclipse side of the target platform; the parent dependencies define the Maven Central side.
