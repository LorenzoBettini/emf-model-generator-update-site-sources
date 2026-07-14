# EMF Model Generator Tycho update site

This is a multi-module Maven/Tycho 5.0.3 build that creates a p2 update site for the EMF Model Generator.

## Modules

- `emf.model.generator.targetplatform`: Eclipse target definition using the Eclipse 2026-06 repository and EMF runtime bundles.
- `emf.model.generator.feature`: feature containing the EMF Model Generator bundle.
- `emf.model.generator.feature.source`: explicit source feature containing the EMF Model Generator source bundle.
- `emf.model.generator.repository`: p2 repository/update site with runtime and source categories.

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

## Maven Central artifacts

The main EMF Model Generator bundle currently uses `emf-model-generator` as its `Bundle-SymbolicName`, so the runtime feature includes the plug-in `emf-model-generator`.

The Maven Central `-sources.jar` is a plain Maven source archive, not a PDE source bundle. The parent POM therefore uses `pomDependencies=wrapAsBundle`, so Tycho can wrap that source archive and make the `emf-model-generator.source` IU available to the explicit source feature.

## Why the Maven dependencies are in the parent POM

The Maven Central artifacts are declared in the parent POM because Tycho's `pomDependencies` mechanism is project-scoped: downstream Tycho modules must all see the Maven OSGi bundles in their own target platform. The `.target` file defines the p2/Eclipse side of the target platform; the parent dependencies define the Maven Central side.

## Why `pomDependencies=wrapAsBundle` is used

`pomDependencies=consider` is enough for the runtime artifact because `emf-model-generator` is already an OSGi bundle. It is not enough for the Maven `sources` classifier: that artifact is a plain Maven sources jar, so Tycho ignores it and the source feature cannot resolve `emf-model-generator.source`.

Using `pomDependencies=wrapAsBundle` keeps the runtime bundle behavior and additionally lets Tycho generate OSGi metadata for the non-bundle source archive.
