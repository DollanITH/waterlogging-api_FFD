# Waterlogged API

The API is published as a separate artifact so integrations can compile against a small, versioned surface instead of the implementation mod.

```gradle
repositories {
    mavenLocal()
}

dependencies {
    compileOnly 'dev.xy177.waterlogged:waterlogged-api:0.1.0'
}
```

The API jar is not a replacement for the Waterlogged mod. The mod remains a required runtime dependency and supplies the `WaterloggedApi.Provider` implementation. Do not bundle the API classes into an integration mod; install the API jar alongside Waterlogged at runtime.

`IWaterloggableBlock` is for blocks that own a persistent waterlogged property. Implement it on the block class; Waterlogged reads and updates the state through these methods. For blocks whose state is provided by an adapter, register an implementation of `WaterloggedApi.Adapter`. Optional fluid interactions can be registered with `WaterloggedApi.FluidMixRule`.

Registrations made before Waterlogged initializes are queued and replayed when its provider is installed. Registration and unregistration use object identity. The API artifact contains only public integration contracts and has no dependency on Waterlogged implementation classes. It targets the project's Minecraft 1.12.2 / Forge mappings; integrations should use a matching mapping environment.

Build and install locally with:

```text
gradlew.bat :api:publishToMavenLocal
```

`WaterloggedBlockApi` remains in the mod jar as a compatibility facade for existing integrations. New integrations should use `WaterloggedApi`.
