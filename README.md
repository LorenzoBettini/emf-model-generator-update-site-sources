# EMF Model Generator update site

This is a Tycho 5.0.3 multi-module build that creates a p2 update site for the EMF Model Generator Maven Central artifact.

## Modules

- `emf.model.generator.parent`: parent Maven project.
- `emf.model.generator.targetplatform`: target definition using Eclipse 2026-06.
- `emf.model.generator.feature`: binary feature containing the `emf-model-generator` bundle.
- `emf-model-generator.source`: local PDE source bundle created from the Maven Central `sources` classifier.
- `emf.model.generator.feature.source`: source feature containing `emf-model-generator.source` and including the binary feature.
- `emf.model.generator.repository`: p2 update site with runtime and source categories.

## Why there is a source-bundle module

The Maven Central artifact `io.github.lorenzobettini:emf-model-generator:1.0.0` is usable by Tycho as an OSGi bundle through `pomDependencies=consider`.

However, `io.github.lorenzobettini:emf-model-generator:1.0.0:sources` is a plain Maven source archive, not a PDE source bundle. Therefore it does not provide the installable unit `emf-model-generator.source`, and a source feature that references that IU cannot resolve.

The module `emf-model-generator.source` solves this by unpacking the Maven Central `sources` classifier into `src-gen/` and packaging a real Eclipse source bundle with:

```text
Bundle-SymbolicName: emf-model-generator.source
Eclipse-SourceBundle: emf-model-generator;version="1.0.0";roots:="src-gen"
```

This keeps the actual source content coming from Maven Central, while producing the PDE metadata that Eclipse and p2 expect.

## Build

```sh
mvn clean verify
```

The generated update site is under:

```text
emf.model.generator.repository/target/repository
```

## Updating to a different EMF Model Generator release

When changing the release, update these places consistently:

1. `emf.model.generator.version` in the parent `pom.xml`.
2. `Bundle-Version` in `emf-model-generator.source/META-INF/MANIFEST.MF`.
3. `Eclipse-SourceBundle` version in `emf-model-generator.source/META-INF/MANIFEST.MF`.
4. Feature versions if you want the feature version to track the released bundle version.

For example, for `1.0.1`, use:

```text
Bundle-Version: 1.0.1.qualifier
Eclipse-SourceBundle: emf-model-generator;version="1.0.1";roots:="src-gen"
```

## Notes

The target definition references `https://download.eclipse.org/releases/2026-06` and selects the required EMF bundles:

- `org.eclipse.emf.common`
- `org.eclipse.emf.ecore`
- `org.eclipse.emf.ecore.xmi`

The runtime Maven artifact is not listed in the `.target` file itself. It is declared as a parent-POM dependency and made visible to Tycho via:

```xml
<pomDependencies>consider</pomDependencies>
```

Do not use `wrapAsBundle` for this build: it wraps the plain source archive as a generic bundle with a generated symbolic name, but that does not create the expected PDE source bundle `emf-model-generator.source`.
