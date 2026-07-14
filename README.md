# EMF Model Generator update site

This project is a Tycho multi-module build that creates a p2 update site for the EMF Model Generator.

The update site publishes two Eclipse features:

- **EMF Model Generator**: the runtime feature, containing the `emf-model-generator` bundle.
- **EMF Model Generator Sources**: the source feature, containing the corresponding `emf-model-generator.source` bundle.

The EMF Model Generator runtime bundle and source bundle are resolved from the Maven Central artifact:

```text
io.github.lorenzobettini:emf-model-generator:1.0.0
```

The runtime artifact is an OSGi bundle. The source bundle is made available through the Maven-based target-platform location in the `.target` file, so this project does not need to generate a local source-bundle module.

## Project structure

```text
emf-model-generator-update-site/
├── pom.xml
├── emf.model.generator.targetplatform/
│   ├── pom.xml
│   └── emf.model.generator.targetplatform.target
├── emf.model.generator.feature/
│   ├── pom.xml
│   └── feature.xml
├── emf.model.generator.feature.source/
│   ├── pom.xml
│   └── feature.xml
└── emf.model.generator.repository/
    ├── pom.xml
    └── category.xml
```

## Modules

- `emf.model.generator.parent`: root Maven parent project.
- `emf.model.generator.targetplatform`: target definition used by Tycho to resolve the build target platform.
- `emf.model.generator.feature`: Eclipse feature for the EMF Model Generator runtime bundle.
- `emf.model.generator.feature.source`: Eclipse source feature for the EMF Model Generator source bundle.
- `emf.model.generator.repository`: p2 repository/update-site project with runtime and source categories.

## Target platform

The target platform is defined in:

```text
emf.model.generator.targetplatform/emf.model.generator.targetplatform.target
```

It has two locations.

The first location uses the Eclipse release repository and selects the EMF bundles required by EMF Model Generator:

```text
https://download.eclipse.org/releases/2026-06
```

Selected installable units:

- `org.eclipse.emf.common`
- `org.eclipse.emf.ecore`
- `org.eclipse.emf.ecore.xmi`

The second location is a Maven target-platform location that resolves the EMF Model Generator artifact from Maven Central:

```xml
<location includeDependencyDepth="none"
          includeDependencyScopes="compile"
          includeSource="true"
          missingManifest="generate"
          type="Maven"
          label="EMF Model Generator from Maven Central">
  <dependencies>
    <dependency>
      <groupId>io.github.lorenzobettini</groupId>
      <artifactId>emf-model-generator</artifactId>
      <version>1.0.0</version>
      <type>jar</type>
    </dependency>
  </dependencies>
  <repositories>
    <repository>
      <id>central</id>
      <url>https://repo.maven.apache.org/maven2/</url>
    </repository>
  </repositories>
</location>
```

The parent POM configures Tycho with:

```xml
<targetDefinitionIncludeSource>force</targetDefinitionIncludeSource>
```

Together with `includeSource="true"` in the Maven target location, this makes the source bundle available to the source feature as:

```text
emf-model-generator.source
```

## Features

The runtime feature is defined in:

```text
emf.model.generator.feature/feature.xml
```

It contains the runtime bundle:

```xml
<plugin
      id="emf-model-generator"
      download-size="0"
      install-size="0"
      version="0.0.0"
      unpack="false"/>
```

The source feature is defined in:

```text
emf.model.generator.feature.source/feature.xml
```

It includes the runtime feature and the source bundle:

```xml
<includes
      id="emf.model.generator.feature"
      version="0.0.0"/>

<plugin
      id="emf-model-generator.source"
      version="0.0.0"/>
```

This keeps the source feature explicit instead of relying on Tycho-generated source-feature support.

## Repository

The update site is defined in:

```text
emf.model.generator.repository/
```

The categories are defined in:

```text
emf.model.generator.repository/category.xml
```

The repository contains two categories:

- **EMF Model Generator**: runtime feature.
- **EMF Model Generator Sources**: source feature.

The repository intentionally does not use `includeAllDependencies`. This keeps the generated p2 repository focused on the EMF Model Generator features rather than mirroring all target-platform dependencies such as EMF itself.

The repository POM enables source inclusion and compression:

```xml
<includeAllSources>true</includeAllSources>
<compress>true</compress>
<xzCompress>true</xzCompress>
```

## Building

Run the build from the root directory:

```sh
mvn clean verify
```

The generated update site is created under:

```text
emf.model.generator.repository/target/repository
```

You can use that directory directly as a local Eclipse update site, or publish its contents to a web server.

## Using the generated update site in Eclipse

In Eclipse:

1. Open **Help > Install New Software...**.
2. Add the generated repository location.
3. Select **EMF Model Generator**.
4. Optionally select **EMF Model Generator Sources** if you want source attachment support in the IDE.

## Updating to a different EMF Model Generator version

When publishing a new EMF Model Generator release, update the version consistently in these places:

1. `emf.model.generator.targetplatform/emf.model.generator.targetplatform.target`, in the Maven dependency version.
2. `emf.model.generator.feature/feature.xml`, if the feature version should track the released bundle version.
3. `emf.model.generator.feature.source/feature.xml`, if the source-feature version should track the released bundle version.
4. The parent `pom.xml` project version, if the update-site build itself should be released with the same version.
5. The `emf.model.generator.version` property in the parent `pom.xml`, if you keep it as the central version marker for the build.

For example, when moving from `1.0.0` to `1.0.1`, the Maven target-platform dependency should become:

```xml
<dependency>
  <groupId>io.github.lorenzobettini</groupId>
  <artifactId>emf-model-generator</artifactId>
  <version>1.0.1</version>
  <type>jar</type>
</dependency>
```

## Notes

This project deliberately avoids `pomDependencies=consider` and `pomDependencies=wrapAsBundle` in the parent POM. The EMF Model Generator artifact is resolved through the Maven location in the `.target` file instead.

This keeps the target platform self-contained and explicit:

- EMF dependencies come from the Eclipse 2026-06 p2 repository.
- EMF Model Generator comes from Maven Central.
- Sources are requested through the target definition and exposed to the explicit source feature.
