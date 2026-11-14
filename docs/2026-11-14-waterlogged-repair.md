# Waterlogged repair status

Date: 2026-11-14

## Delivered

- Removed the universal state-carrier block path; contained fluid remains in chunk side data, without consuming block IDs.
- Sponge absorption follows vanilla's connected-water traversal bounds (depth 6, at most 64 blocks) and additionally dries connected waterlogged hosts. Dry-state changes clear both persisted fluid records and synchronize the host update.
- Fluid replacement drops the replaced block only after `World#setBlockState` succeeds.
- Trapdoor state changes refresh only that trapdoor's contained fluid entry as still source fluid. This callback does not update neighboring fluid blocks or start a spread pass.
- Waterlogged host state and contained-fluid state are persisted together through `FFDStoredFluidStates` and `FFDFluidloggedData`.

## Verification

- `gradlew.bat build -x test --console=plain`: passed.
- `git diff --check`: passed.
- Sponge behavior was accepted in game by the user.
- Trapdoor collision-driven fluid spread is not verified or considered fixed. The current requested scope only refreshes the trapdoor's contained-fluid state; direction-aware spread remains outstanding.
- WorldEdit `//stack` persistence has not been verified.

## 2026-11-14 Flowing Water Placement Repair

- Vanilla flowing-water writes no longer convert a dry waterloggable host into a waterlogged host; source-water placement retains the existing behavior.
- The mod's contained-fluid spread path also refuses to replace a dry waterloggable/additional host with flowing water. This prevents the fluid replacement path from producing an extra host-block drop.
- `./gradlew.bat build -x test --console=plain`: passed after the change.
- `git diff --check`: passed.
- Fresh client and in-world verification have not been performed; flowing-water placement behavior remains awaiting gameplay confirmation.

## 2026-11-14 Placement Drops and Trapdoor Flow Refresh

- Vanilla dynamic water now treats dry waterloggable hosts as blocked, preventing its displacement path from reporting a successful liquid write and dropping the replaced host repeatedly.
- A waterlogged trapdoor state/collision change now schedules a near-immediate contained-fluid update and notifies adjacent blocks so possible flow directions are recalculated against the new collision shape.
- Build and diff checks are run for this revision; the repeated-drop scenario and trapdoor opening/closing flow still require in-world verification.

## 2026-11-14 WorldEdit Core Transformer

- Added an optional Waterlogged core transformer for WorldEdit's `ExtentBlockCopy.apply` path.
- The transformer records the source extent and source position before `Extent#setBlock`, then copies the source host's waterlogged state, `FFDStoredFluidStates` entry, and `FFDFluidloggedData` fluid type to the destination after the write.
- No files in `E:\WorldEdit\we` were changed. The transformer is inert when WorldEdit is absent and catches optional integration failures.
- `./gradlew.bat build -x test --console=plain`: passed. `git diff --check`: pending final run.
- Runtime `//stack` verification has not yet been performed.

## 2026-11-14 WorldEdit Transformer Follow-up

- Fixed the bridge to resolve and cache the source world, source position, host state, contained fluid state, and fluid type before WorldEdit writes the destination.
- The destination copy no longer depends on resolving a WorldEdit extent after the write; missing state records are reconstructed from the cached fluid type as a still source state.
- Build and `git diff --check` passed. In-game `//stack` verification is still required.

- Renamed the Forge loading plugin from `Waterlogged Mixin` to `Waterlogged Coremod`, since it now registers both the existing mixin bootstrap and an ASM CoreTransformer.

- Corrected the WorldEdit transformer for the project's actual `IExplicitTransformer`/`TransformerDelegate` path and allowed both `INVOKEINTERFACE` and `INVOKEVIRTUAL` `setBlock` calls. The coremod now emits a registration marker during startup for runtime confirmation.

## 2026-11-14 WorldEdit Runtime No-Op Follow-up

- User runtime logs showed Foundation rejecting the anonymous visitor helper classes (`WaterloggedWorldEditTransformer$1` and `$1$1`) as unable to handle Java 21 class files. Replaced anonymous ASM visitors with ASM tree instruction edits, so the transformer no longer generates those visitor classes.
- Registered both dotted and internal slash target names for Foundation's explicit-transformer lookup.
- The transformer now logs separately when it injects successfully, when the target class lacks a matching call, or when transformation throws.
- `./gradlew.bat build -x test --console=plain`: passed. Runtime `//stack` confirmation after launching the rebuilt jar remains pending.
- A subsequent `runClient` attempt did not start: `preRunClient` failed with `AccessDeniedException` for `run/client/natives/SAPIWrapper_x64.dll`; this is an environment lock and gives no gameplay result.

## 2026-11-14 WorldEdit `//stack` follow-up

- Clarified that `//stack` uses WorldEdit's `ExtentBlockCopy.apply`, bypassing `ItemBlock.onItemUse`.
- The transformer is now registered through Foundation's `TransformerDelegate.registerExplicitTransformer(...)`, with `ExtentBlockCopy` as its explicit target; Forge's `getASMTransformerClass()` no longer returns it.
- The main project and transformer are compiled together for Java 25, matching the Foundation-based registration model used by the reference coremod.
- `gradlew.bat clean jar -x test --console=plain` passed after the registration migration.
- A fresh `runClient` attempt was blocked by an existing locked `run/client/natives/SAPIWrapper_x64.dll`; no in-game `//stack` verification was possible in this pass.

## 2026-11-14 WorldEdit `//stack` bridge no-op fix

- The supplied runtime logs proved the explicit transformer injected into `ExtentBlockCopy`, but the bridge returned early every time: its pending-state constructor stored a null source position in `sourcePos`, while `finish` required that field to be non-null.
- Corrected the pending source-position assignment and changed `finish` to validate the parsed `BlockPos` actually used by the copy.
- The transformer now passes the result of `Extent#setBlock` to the bridge; failed destination writes discard the pending copy without changing fluid records.
- Replaced silent exception swallowing with Waterlogged logger diagnostics for source capture, destination resolution, and contained-fluid copy failures.
- `gradlew.bat build`: passed; `git diff --check`: passed. The user's latest screenshot/log proves the previous revision failed; gameplay acceptance of this repair still requires a new `runClient` `//stack` test.

## 2026-11-14 WorldEdit `//stack` source detection follow-up

- The newest supplied runtime log contains only the transformer registration marker and no bridge `begin`/`finish` output, so it does not show the rebuilt transformer executing during `//stack`.
- Extended source detection to treat any persisted contained-fluid state, fluid-type fallback, or saved wet host state as a wet source, even if the live block property has already been normalized by WorldEdit or chunk synchronization.
- Added source-capture diagnostics showing the source position, resolved world, host state, fluid state, and fluid type. Dry sources are explicitly logged and skipped.
- `gradlew.bat build`: passed. A fresh client restart with the rebuilt artifact and a new `//stack` test is still required.

## 2026-11-14 WorldEdit `//stack` runtime diagnostics

- Replaced bridge diagnostics with `System.out` markers consistent with the transformer marker, so Forge captures them in `latest.log`.
- A waterlogged source with no readable side-table entries now falls back to a still vanilla water state, and both contained-fluid persistence tables are written at the target.
- Destination diagnostics include the `setBlock` result, target host state, and stored fluid state. A failed host-state write is reported rather than silently leaving partial state.
- `gradlew.bat build`: passed. Fresh `runClient` reached the main menu and loaded all 14 mods. This proves startup only; `//stack` gameplay still needs validation. Keep the client session open for the user's test.

## 2026-11-14 WorldEdit deferred write ordering

- The user's latest `latest.log` showed each wet source captured and its water state copied, but the destination host was still `minecraft:air` immediately after `ExtentBlockCopy` called `EditSession#setBlock`. WorldEdit queues the host block and commits it later, so the early side-state write could be invalidated by the actual chunk write.
- Changed the ExtentBlockCopy bridge to queue each source fluid snapshot by destination world and position. Added a second explicit CoreTransformer target for WorldEdit `ForgeWorld#setBlock`; it applies the paired wet host and both fluid records after `Chunk#setBlockState` commits the queued block.
- `gradlew.bat build`: passed. Fresh `runClient` reached mod loading with 14 mods. Logs confirm injection into both `ExtentBlockCopy` and `ForgeWorld`. In-world `//stack` result remains unverified.

## 2026-11-14 WorldEdit clipboard copy/paste

- `//copy` and `//cut` use `ForwardExtentCopy` to transfer world blocks into a `BlockArrayClipboard`; `//paste` uses another `ForwardExtentCopy` from that clipboard into an `EditSession`.
- Added Waterlogged-owned in-memory clipboard fluid snapshots keyed by clipboard identity and source position. Clipboard copy captures the existing host/fluid record; paste retrieves it and hands it to the deferred ForgeWorld chunk-commit hook.
- This covers in-session clipboard operations and transformations that read/write through WorldEdit extents. Schematic file save/load persistence is not included in this change.
- `gradlew.bat build`: passed. Fresh `runClient` loaded all 14 mods and confirmed the ForgeWorld deferred transformer injection. Actual `//copy` + `//paste` gameplay verification remains pending.

## 2026-11-14 WorldEdit clipboard paste no-op diagnosis

- The user's failed `//copy`/`//paste` run showed that copy captured and stored wet source entries in `BlockArrayClipboard`, but paste entered the queue path with `Pending.source=null`. The paste source extent was not the same object key used by copy, so identity-only clipboard lookup missed.
- Clipboard source lookup now falls back to matching the stored clipboard position across tracked clipboard entries. The unresolved-extent fallback retains the parsed source position, and dry clipboard positions are ignored without producing false queue failures.
- `gradlew.bat build`: passed. Fresh `runClient` loaded all 14 mods and injected the deferred ForgeWorld write hook. A new `//copy`/`//paste` gameplay run is required to confirm this repair.

## 2026-11-14 WorldEdit clipboard coordinate follow-up

- The subsequent runtime log showed wet clipboard entries successfully restored for several targets, including `waterlogged=true` hosts with both contained-fluid records. It also showed many source coordinates unresolved, so the prior global coordinate fallback was incomplete and could match the wrong clipboard.
- Clipboard reads now resolve the wrapped clipboard by following WorldEdit delegate extents and normalize the read position using the clipboard origin and minimum point. Lookup is scoped to that clipboard identity; cross-clipboard coordinate guesses are removed.
- The user is considering an API artifact for third-party block integrations. Current `IWaterloggableBlock` is a small candidate, but `WaterloggedBlockApi` itself depends on Minecraft, Forge, and implementation classes. No API packaging/build changes are made pending a stable public contract decision.
- The previous `//copy`/`//paste` runtime session ended normally after saving at 22:07:53; fresh verification of this coordinate correction remains required.

## 2026-11-14 WorldEdit clipboard paste coordinate correction

- The user's new screenshot and `latest.log` show copy stored wet entries at the clipboard's original region coordinates, while paste reads the same coordinates through `BlockTransformExtent`. The prior origin/minimum offset was invalid: WorldEdit 1.12 `BlockArrayClipboard` uses region coordinates for `getBlock`/`setBlock`; origin affects paste placement only.
- Removed that offset and kept identity-scoped lookup on the unwrapped clipboard. This directly addresses the observed paste lookup misses without reintroducing cross-clipboard coordinate matching.
- Build/runtime verification for this correction is pending; the screenshot is evidence that the preceding build failed acceptance.

## 2026-11-14 Standalone integration API artifact

- Added an `api` Gradle subproject publishing `dev.xy177.waterlogged:waterlogged-api:0.1.0`, with separate source and remapped jars.
- The artifact owns `IWaterloggableBlock` and the stable `WaterloggedApi` registration/query contracts. It contains no direct reference to Waterlogged implementation classes; the main mod installs a provider during pre-initialization.
- Adapter and fluid-rule registrations are queued until provider installation and bridge by identity so duplicate registration and unregistration behave predictably. The old `WaterloggedBlockApi` remains in the mod jar as a compatibility facade.
- The main jar excludes only API-owned classes; it keeps the compatibility facade. API installation and usage guidance is in `docs/waterlogged-api.md`.
- `gradlew.bat build :api:publishToMavenLocal` passed. The published POM now has the remapped jar as its default artifact plus a sources jar; the artifact is present under `dev.xy177.waterlogged:waterlogged-api:0.1.0` in Maven Local.
- Jar inspection confirmed the main mod keeps `WaterloggedBlockApi` and excludes `WaterloggedApi`/`IWaterloggableBlock`, while the separate API jar contains those public contracts.
- Fresh `runClient` reached successful loading of 14 mods. This verifies development runtime class loading and mod startup, not a third-party mod build or integration acceptance. Existing unrelated FutureMC model warnings appeared during startup.
