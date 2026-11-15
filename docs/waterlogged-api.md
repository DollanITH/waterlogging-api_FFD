# Waterlogged API

The API is published as a separate artifact so integrations can compile against a small, versioned surface instead of the implementation mod.

```gradle
repositories {
    maven {
        name = 'WaterloggedAPI'
        url = 'https://dollanith.github.io/waterlogging-api_FFD/'
    }
}

dependencies {
    // Unimined / RetroFuturaGradle: the plugin remaps the published jar back into development mappings
    modCompileOnly 'dev.xy177.waterlogged:waterlogged-api:0.1.0'
}

// Plain ForgeGradle (fg.deobf environment) instead uses:
// compileOnly fg.deobf('dev.xy177.waterlogged:waterlogged-api:0.1.0')
```

The published jar is remapped, but the API surface only names Minecraft types in signatures and calls no Minecraft members, so its names come out identical to the development mappings (1.12.2 srg keeps the mcp class names) and a plain `compileOnly` compiles too. `modCompileOnly` is still the safer declaration: Unimined runs the artifact through its searge to mcp remapper, which matters the moment a default method or constant starts referencing a Minecraft member. Outside Unimined, wrap the coordinate with the loader plugin's deobf helper (`fg.deobf` on ForgeGradle 4+, `deobf` on RetroFuturaGradle) if the IDE resolves `func_`/`field_` names. `-sources.jar` ships alongside the binary, so IDEA resolves the sources automatically. Dependencies are stripped from the published POM (Unimined logs `stripping dependencies from published POM of api`), so nothing transitive leaks into an integration's classpath.

The API jar is not a replacement for the Waterlogged mod. The mod remains a required runtime dependency and supplies the `WaterloggedApi.Provider` implementation. The API classes are also included in the main Waterlogged jar, so runtime installations need only Waterlogged; integration mods should declare the API artifact as a compile-only dependency and must not bundle it.

`IWaterloggableBlock` is for blocks that own a persistent waterlogged property. Implement it on the block class; Waterlogged reads and updates the state through these methods. For blocks whose state is provided by an adapter, register an implementation of `WaterloggedApi.Adapter`. Optional fluid interactions can be registered with `WaterloggedApi.FluidMixRule`.

Registrations made before Waterlogged initializes are queued and replayed when its provider is installed. Registration and unregistration use object identity. The API artifact contains only public integration contracts and has no dependency on Waterlogged implementation classes. It targets the project's Minecraft 1.12.2 / Forge mappings; integrations should use a matching mapping environment.

## Publishing

The `:api` subproject publishes to the `Pages` repository, a local staging directory at `build/gh-pages`:

```text
gradlew.bat :api:publishAllPublicationsToPagesRepository
```

`.github/workflows/publish-api-maven.yml` runs that task on a `v*` tag (or manually), then pushes the directory to the `gh-pages` branch with `keep_files: true`, so older versions stay downloadable. GitHub Pages serves it at `https://dollanith.github.io/waterlogging-api_FFD/`. One-time setup: enable Pages on the repository with *Deploy from a branch* → `gh-pages` → `/ (root)`. A `.nojekyll` file is added so Pages publishes the files verbatim.

The version comes from `rootProject.version`, i.e. `gradle.properties`' `mod_version`. Bump it whenever the API surface changes, otherwise the new jar overwrites the old coordinates on `gh-pages`. Each publish rewrites `maven-metadata.xml` with only the version it just built, so version ranges and `latest`/`release` lookups see the newest release while exact coordinates keep resolving to every version kept on the branch.

For local iteration without a push, `gradlew.bat :api:publishToMavenLocal` still works: set `publish_to_local_maven = true` and consume the same coordinate from `mavenLocal()`.

`WaterloggedBlockApi` remains in the mod jar as a compatibility facade for existing integrations. New integrations should use `WaterloggedApi`.

## Integration extension points (API 1.1)

`WaterloggedApi.API_VERSION` is now `1.1.0`. Three additions change what an integration can do without editing Waterlogged:

**1. A block can append the mod's property itself.** The runtime adds `waterlogged` to a fixed list of vanilla classes plus a hard-coded FutureMC class. A third-party block never reaches that list, and the injection happens while the block is constructed, so an `instanceof` check cannot cover it. Instead, build the container with the shared instance:

```java
@Override
protected BlockStateContainer createBlockState() {
    return new BlockStateContainer(this, MY_PROPERTY,
            WaterloggedApi.universalWaterloggedProperty());
}
```

`universalWaterloggedProperty()` returns the same `PropertyBool` the runtime uses, so `WaterloggedApi.isWaterlogged(world, pos)`, `withWaterlogged(...)` and the mixin's injector all agree. The injector skips an already-present same-named property, so adding it twice is harmless.

**2. Or let the runtime inject it, by opting the class in before construction.** The state container is created inside the block constructor, so this must run first — a static initializer placed above the block field is enough:

```java
static { WaterloggedApi.registerUniversalWaterloggableBlock(MyBlock.class); }
public static final Block MY_BLOCK = new MyBlock();
```

**3. `IWaterloggableBlock.supportsWaterlogging(state)`** reports which states can hold water, so callers no longer infer capability from a `null` returned by `setWaterloggedState`. `setWaterloggedState` is documented as nullable; `null` means "this state cannot represent waterlogging", not "dry".

A block that declares its own `waterlogged` property without implementing the interface is also recognised now: `WaterloggedBlockApi.findOwnWaterloggedProperty` matches by property name, so `isWaterlogged`, `withWaterlogged` and `canBeWaterlogged` work for it. Registering an `Adapter` is still the route for blocks whose wet/dry flag lives in a differently named property.

One caveat unchanged by 1.1: `waterlogged` state cannot be persisted through 1.12.2 NBT (the chunk stores block id + four metadata bits; the palette container in memory does not change the disk format). Persistence of the wet flag comes from the chunk-side contained-fluid record, which is why integrations must query the world-level API (`isWaterlogged(world, pos)`) rather than reading the block state themselves.

