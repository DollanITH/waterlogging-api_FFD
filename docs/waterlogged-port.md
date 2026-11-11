# Waterlogged Port

Date: 2026-11-10

## Source and template

- Waterlogged code was separated from [Farmers Future Delight](https://github.com/xy177/Farmers-Future-Delight), upstream revision `bd0b45045edbdbe07a2e98e93c564c47796381c9`.
- The project uses the Project Outlands `mdk-java-mixin` template, release `mdk/2026.11.03-2`; it does not use the source repository's Forge template.
- The Gradle wrapper uses Gradle 9.8.2 from the Tencent mirror. The wrapper defaults `GRADLE_USER_HOME` to `E:/mdk/waterlogged/.gradle` when the variable is not already set.

## Waterlogged model handling

The upstream implementation does not add a separate model for the universal `waterlogged` property. Before a state mapper turns properties into a model key, it removes that property, so wet and dry states use the existing block model. The extracted code applies this in `StateMapperMixin` through `WaterloggedBlockApi.withoutUniversalWaterloggedProperty`.

Blocks that only carry encoded state use `BlockWaterloggedStateCarrier`; its custom state mapper returns an empty map, following the upstream carrier handling. This prevents model loading from requesting a nonexistent carrier or barrier blockstate. A previous development run that mapped carriers to `minecraft:barrier#normal` logged a missing barrier blockstate; the final run no longer logged that model error.

At `ModelRegistryEvent`, blocks with the universal property are registered for model wrapping and water tinting. `WaterloggedPlantBakedModel` adds contained-fluid quads during the translucent render pass. The water bucket interaction handler also intercepts right-clicks on eligible dry blocks, stores water in the clicked block, consumes the bucket outside creative mode, and prevents vanilla from pouring into the adjacent position.

## Verification

- `gradlew.bat build --console=plain`: passed. There are no test sources (`test` reported `NO-SOURCE`).
- `gradlew.bat runClient --console=plain`: launched the Outlands 1.12.2 client and entered a single-player world. After model registration was fixed, the supplied in-game screenshot shows water visibly rendered around the `minecraft:stone_slab` (`half: bottom`, `variant: cobblestone`, `waterlogged: true`). The latest client log had no `Exception loading model` or `Exception loading blockstate` entries for waterlogged carriers.
- `gradlew.bat build --console=plain`: passed after adding the right-click water bucket handler. That client instance was already running before the bucket change, so the interaction fix still needs a restart for in-game verification.
- The built artifact is `build/libs/waterlogged-0.1.0.jar`; inspection confirmed it contains the mixin configuration, refmap, and `StateMapperMixin` class.

The client log still has development-environment notices, including the missing Forge binary patch set and a CleanMix warning about resolving the mixin configuration from an exploded development classpath. They did not prevent client startup or model registration. The rendered water surface is visually verified; the bucket interaction is build-verified but not yet game-verified. This is not a standalone dedicated-server or release-installation test.

## Waterlogging Behavior Audit (2026-11-11)

- Compared the interaction and rendering paths with Minecraft Java waterlogging behavior. The official 18w10d notes say water can flow out of waterlogged blocks but not into them, and that it no longer passes through solid faces; they also mark all-face flow as provisional and list the stair-flow regression MC-126927. The 1.13 pre-release notes list MC-126906 (water buckets cannot be emptied against waterlogged blocks) and MC-126915 (dispenser empty buckets remove, rather than dry, waterlogged blocks). Java 1.19.3 later fixed MC-127110, about emptying a water bucket into a waterlogged block; that is distinct from collecting water with an empty bucket. References: [18w10d snapshot notes](https://feedback.minecraft.net/hc/en-us/articles/360004167851-Minecraft-Java-Edition-Snapshot-18W10D), [1.13 pre-release notes](https://feedback.minecraft.net/hc/en-us/articles/360004784612-Minecraft-Java-Edition-1-13-Pre-Release), [1.19.3 release notes](https://feedback.minecraft.net/hc/en-us/articles/11280166737293-Minecraft-Java-Edition-1-19-3).
- Audited horizontal flow in `WaterloggedPlantFluid`: all horizontal candidates and slope-search steps call `canPassThrough`. The initial port allowed flow when any portion of the shared face was open; follow-up review identified that this let water escape through partial openings beside slabs and stairs, contrary to the requested behavior. The follow-up below changes the rule to require the entire shared face to be free of collision boxes.
- Audited destruction-particle texture lookup: the state mapper drops only the universal `waterlogged` property, the existing host model is wrapped, and `WaterloggedPlantBakedModel.getParticleTexture()` delegates to that host model. This matches upstream. No particle-specific mixin or alternate texture lookup exists in this port. Runtime mismatch diagnosis still requires checking the destroyed state's host block and baked model.
- Updated bucket event handling to process `FillBucketEvent` only while its result is `DEFAULT`; previously an earlier listener's `ALLOW` could be overridden and the water drained again. `RightClickBlock` now leaves the interaction alone when another listener has denied item use. The bucket-targeted and item-use event paths remain separate, as in Forge 1.12.2.
- Verification on 2026-11-11: `GRADLE_USER_HOME=E:/mdk/waterlogged/.gradle gradlew.bat build --console=plain` passed; `test` reported `NO-SOURCE`. The client was not restarted for in-game verification, so empty-bucket pickup and the reported particle/flow appearance are not runtime-accepted by this audit.

## Follow-up: flowing water placement and remaining visual reports (2026-11-11)

- Fixed the `World#setBlockState` replacement path: when a flowing fluid block is replaced by a waterloggable host, it is no longer copied into the host's stored-fluid state. Only a source fluid state can be preserved this way. This closes the path that let non-source flowing water make a newly placed slab or similar block waterlogged. The separate two-source water-formation rule remains active and checks actual source neighbors plus collision-face openings.
- The first follow-up temporarily changed the shared-face rule to reject any collision contact. This was too strict for partial blocks and is superseded by the next follow-up below; it could prevent water from escaping a slab entirely.
- Destruction particles use the baked host block model's particle sprite through `WaterloggedPlantBakedModel#getParticleTexture`. Existing development logs do not record the model selected at the exact destruction event, so the reported purple/black particle is not yet conclusively resolved by runtime evidence. Recheck the exact `stone_slab` variant's baked model and particle sprite after launching a build containing this change.
- The client was not restarted for this follow-up. Build verification is not in-game acceptance; flow and particle appearance remain pending a fresh client session.

## Follow-up: water flow, pickup, and particle texture (2026-11-11)

- Reverted the overly strict full-face collision rule: it could seal every side of a bottom slab and prevent contained water from flowing out. `canPassThrough` again checks the union of both collision shapes on their shared face and permits flow where a portion remains open. Horizontal propagation, slope search, imported-fluid updates, and source formation share this geometry check.
- When the host model reports `missingno` as its particle sprite, `WaterloggedPlantBakedModel` now falls back to the first usable sprite on the host model's baked face quads, so break particles use the original block texture.
- `setDryState` now explicitly removes the saved contained-fluid state after a successful drain. Empty-bucket right-click handling runs at highest event priority, and pickup treats water stored in a waterlogged block as collectible without rejecting it solely because a stale partial-level entry was present.
- Verification: build pending. In-game acceptance of outward flow, empty-bucket pickup, and destroy particles requires a client using the rebuilt artifact.

## Follow-up: unstable flow, bucket refill, and break particles (2026-11-11)

- The 02:03 screenshots show water repeatedly appearing around a wet slab and purple/black break particles. The prior full-face collision change was already reverted; the new issue is state regeneration after bucket collection.
- Disabled automatic source formation inside dry waterloggable hosts. The former `tryFormWaterSource` path could immediately re-wet a slab after an empty bucket drained it when neighboring source water satisfied the two-source rule. Explicit bucket/source placement still waterlogs a host.
- `setDryState` now sends a block update after stored fluid removal so the client receives the final dry state and rerenders that position.
- Added a client `ParticleManager` mixin that supplies the original dry host state to the vanilla block destruction particle path. This directs texture selection to the original block's model instead of the encoded wet state/carrier.

## Follow-up: runtime root-cause repair (2026-11-11)

- The 02:13 client log confirmed that the previous particle mixin loaded, but bytecode inspection showed that vanilla calls `IBlockState#getActualState` after the old injection point. The dry-state conversion now runs after that call and immediately before `ParticleDigging` receives the state, preventing the world-backed actual-state lookup from restoring the wet model used for particle texture selection.
- The retract/reappear loop came from two independent schedulers: the contained source placed a vanilla flowing-water block, while `BlockDynamicLiquid` could not see the adjacent contained source and removed that flow; the resulting neighbour update made the host place it again. `BlockDynamicLiquidMixin` now reports a matching waterlogged neighbour as level zero during vanilla's horizontal depth calculation, allowing the emitted flow to remain supported by the contained source.
- Empty-bucket drainage now schedules all adjacent vanilla water blocks for an immediate recalculation after the contained source is removed. This removes the support added above and lets residual outward flow decay without requiring the host block to be broken.
- Compile verification passed with `GRADLE_USER_HOME=E:/mdk/waterlogged/.gradle`. A fresh client run is still required to accept the three visual/runtime behaviours.
- `GRADLE_USER_HOME=E:/mdk/waterlogged/.gradle gradlew.bat build --console=plain` passed on 2026-11-11; `test` reported `NO-SOURCE`. The resulting jar is `build/libs/waterlogged-0.1.0.jar`. A fresh client session has not yet verified the new runtime behavior.

## Client startup failure after the 02:18 build (2026-11-11)

- `run/client/logs/latest.log` at 02:21 shows a fatal `InvalidInjectionException` for `BlockDynamicLiquidMixin`: the previous `getSlopeDistance` injection supplied three target arguments, while that method requires a fourth `EnumFacing` argument. This prevented the client from starting, so that build could not validate flow, bucket pickup, or particles.
- The intended three-argument vanilla water-neighbour calculation is named `checkAdjacentBlock` in the current Outlands MCP mappings. The injection now targets that method. The corrected `build` passed and the remap step reported no unresolved injection target.
- The corrected build still requires a fresh client launch and in-game verification. The failed 02:21 launch is not evidence for or against the three behavioural fixes.

## Follow-up: empty bucket and water surface seam (2026-11-11)

- The 02:26 client log starts normally and loads the corrected `BlockDynamicLiquidMixin`; there is no Mixin apply failure in this run.
- Empty-bucket collection now runs before unrelated interaction cancellation checks. When the targeted block is a collectible waterlogged host, the handler explicitly denies the normal block and item use paths, drains the host, updates its stored fluid data, and schedules adjacent vanilla water for recalculation.
- Destroy particles now force the dry host state at the exact `ParticleDigging` constructor argument, in addition to the earlier state conversion. This prevents later state handling from selecting the wrapped waterlogged model.
- The custom contained-water top quad now uses the same inset perimeter as its side quads. The previous full-block top perimeter could leave a visible slit against the flowing side surface.
- `build` passed with the configured Gradle home. Runtime confirmation of the new JAR is still required for bucket pickup and particle appearance.

## Follow-up: strict upstream interaction and particle paths (2026-11-11)

- Removed the port-only `ParticleManagerMixin`. Upstream does not alter `ParticleManager`; the state mapper removes the universal `waterlogged` property and the wrapped host baked model supplies the original block particle sprite.
- Disabled the port-only `RightClickBlock` bucket implementation and adjacent-flow rescheduling. Empty-bucket collection now has one authoritative `FillBucketEvent` path, matching the upstream Forge interaction flow: validate the ray-traced host, clear the contained state, set the water bucket result, and notify the host neighbours.
- The strict-upstream build passed with `GRADLE_USER_HOME=E:/mdk/waterlogged/.gradle`. Runtime acceptance still requires launching this new artifact and testing the empty bucket and break particles in-game.

## Correction: preserve water-bucket placement (2026-11-11)

- The previous strict-upstream cleanup removed both empty-bucket collection and water-bucket placement from `RightClickBlock`; that was too broad for this 1.12.2 port. Empty-bucket collection remains exclusively in `FillBucketEvent`, while water-bucket placement has been restored as a separate highest-priority right-click handler.
- The restored placement path only handles `Items.WATER_BUCKET`, targets the clicked block or the adjacent face, calls `setContainedFluid`, consumes the bucket outside creative mode, and prevents vanilla placement into the neighboring block.
- Particle handling remains upstream-style: no `ParticleManagerMixin` is registered. The build passed after restoring this separation.

## Continued state and bucket repair (2026-11-11)

- Audited the port's chunk state callback. `FFDStoredFluidStates.changed` could copy a source into its auxiliary storage even when the new host already had a `waterlogged` property. The callback now restricts this copy to hosts without that property. Property-bearing hosts therefore have one authoritative wet state rather than a second saved source that can survive bucket pickup.
- After an empty bucket changes the host to its dry state, the bucket handler schedules adjacent fluid blocks to recalculate and notifies neighbors. This is intended to let unsupported outflow decay without breaking the host.
- The current baked model delegates its particle sprite to the original host model, as upstream does. This code audit does not prove that the user's reported incorrect break particles are gone.
- Final command: `GRADLE_USER_HOME=E:/mdk/waterlogged/.gradle gradlew.bat build --console=plain` succeeded on the final source; `test` was `NO-SOURCE`. Artifact: `build/libs/waterlogged-0.1.0.jar`. No fresh client session tested bucket pickup, flow stability, water surface seams, or break particles after these changes.

## Screenshot follow-up: bucket, particles, water seam, movement (2026-11-11)

- The user's 13:43 screenshots show a stone bottom slab with `waterlogged: true`, an empty bucket after collection, then `waterlogged: false`. The reported pickup required two right clicks. Added a `RightClickBlock` empty-bucket path that handles the clicked source host in one interaction and retains `FillBucketEvent` as a fallback.
- The Outlands 1.12.2 `ParticleDigging` constructor asks `BlockModelShapes.getTexture(state)` for the break sprite. Wet states can miss its state-to-model cache even though the dry host renders normally. `BlockModelShapesMixin` resolves a wet host to the corresponding dry model. `WaterloggedPlantBakedModel` also falls back to a non-missing sprite from the host's baked face quads when the host's declared particle sprite is `missingno`.
- `BlockFluidRenderer` used only vanilla water material when calculating corner heights. It now uses the same fluid-height sampling as the contained-water model at corners touching a waterlogged host. The shared side of native water next to contained water is culled, reducing the visible seam.
- Vanilla `World.handleMaterialAcceleration` and `Entity.isInsideOfMaterial` ignored water inside a solid host. The new mixin paths recognize contained water for movement and eye-level checks up to the source-water surface.
- Final `GRADLE_USER_HOME=E:/mdk/waterlogged/.gradle gradlew.bat build --console=plain` passed. `test` was `NO-SOURCE`. This is compile and remap verification; a new client session still needs to confirm one-click pickup, host-textured break particles, seam continuity, and water movement. The screenshots predate this build.

## Upstream code audit after renewed reports (2026-11-11)

- The user reported that empty-bucket pickup again required breaking the host, that particles still used the missing texture, that contained water looked static beside outflow, and that water movement remained ineffective.
- Re-read upstream revision `bd0b45045edbdbe07a2e98e93c564c47796381c9`. Upstream `WaterloggedPlantBakedModel#getParticleTexture` delegates to the host model. Its `FFDHeightTransformer` also patches `RenderGlobal.playEvent` to decode the complete waterlogged state ID before spawning block destruction particles. The port now adds that missing destruction-event decode; a model sprite fallback alone could not restore the lost state ID.
- Upstream patches `World.handleMaterialAcceleration` at `Block#isEntityInsideMaterial` and both `Block#modifyAcceleration` calls. The port now follows those call sites so contained water participates in the same material and motion accumulation path as vanilla water. The previous return-only check remains as a fallback.
- The port's automatic two-neighbor source formation could refill a dry host immediately after bucket pickup. That automatic path is now disabled for dry hosts, while bucket filling and placing a block into source water remain available. This is an intentional divergence from the source mod to meet the requested pickup behavior.
- A contained source next to flowing vanilla water now selects the flowing water sprite for its top surface; its side sprite was already flowing. The contained fluid is still represented by the host block's state, not by replacing the host with a vanilla water block.
- Final build command using `E:/mdk/waterlogged/.gradle` passed; `test` was `NO-SOURCE`. No fresh client session has tested these latest changes. In particular, one-click pickup, particle texture, connected-water animation, and movement still require runtime acceptance.

## Client startup repair (2026-11-11 17:47 CST)

- The 17:44 client crash was caused by an invalid @ModifyArg callback signature in RenderGlobalMixin. Mixin expected only the selected IBlockState argument; the callback also declared the enclosing playEvent arguments.
- The callback now accepts only IBlockState. BlockMixin#getStateById already decodes the full break-event state ID before RenderGlobal passes the state to the particle manager.
- With GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle, gradlew.bat build --console=plain succeeded. test had NO-SOURCE.
- gradlew.bat runClient --console=plain started the client. The 17:47 log reports Forge Mod Loader has successfully loaded 9 mods and no Mixin apply failure. The client was left running at the user's request.
- This startup check does not establish that empty-bucket pickup, break particles, water movement, or water surfaces behave correctly in-game.


## Corrected screenshot follow-up (2026-11-11)

- The corrected screenshot shows an existing outflow to one side of a waterlogged slab, while the marked adjacent side remains dry. In WaterloggedPlantFluid, a contained source previously used the shortest-downhill-route selector; it now considers every exposed horizontal side. Flowing contained cells retain slope selection.
- GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle gradlew.bat compileJava --console=plain passed. The existing client was left running at the user's request; it still uses the classes loaded before this change. The changed flow behavior and reported empty-bucket issue are not yet verified in a restarted client.


## Flow texture, bucket server handling, and destruction particles (2026-11-11)

- The corrected screenshot shows the animated contained-water top surface moving across the visible flow. The top quad now rotates its UV coordinates using the sampled surface-height gradient. This is a code-level approximation; the result needs visual confirmation in a newly launched client.
- `RightClickBlock` previously cancelled empty-bucket interaction on the client before the right-click packet could reach the server. The client now leaves the event uncancelled, while the server removes the contained source and fills the bucket. This targets the reported case where water only disappears after breaking the host.
- `RenderGlobalMixin` now passes the original host's dry state to `ParticleManager.addBlockDestroyEffects`, so the particle texture lookup does not use the `waterlogged=true` state. The preceding no-op callback left the wet state in place.
- `GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle gradlew.bat build --console=plain` succeeded after all three changes; `test` was `NO-SOURCE`. The existing client remains running and still has the older classes loaded. These behaviors remain unverified in-game until a new client session loads this build.

## Screenshot comparison and follow-up (2026-11-11 18:41 CST)

- The contained-water image shows a differently scaled and oriented animated top surface compared with ordinary flowing water. The port mapped the full 0-16 UV range onto the contained top quad, while Minecraft's `BlockFluidRenderer` maps flowing tops around the texture center using a roughly 4-12 UV range. The contained top quad now uses the vanilla range and rotation formula driven by its sampled height gradient.
- The second image visibly shows purple/black missing-texture destruction particles from a waterlogged stone slab. `ParticleDigging` obtains its sprite through `BlockModelShapes.getTexture(state)`, and `getModelForState` can return the missing model for a dry state absent from its state cache. The wet-state lookup now falls back to the dry host's mapped model location in `ModelManager`.
- Build completed successfully with `GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle`; tests were `NO-SOURCE`. These changes have not yet been visually confirmed in a restarted client. The previously running client was not closed.

## Empty bucket placing water and wet break particles (2026-11-11)

- Outlands `PlayerControllerMP.processRightClickBlock` sends `CPacketPlayerTryUseItemOnBlock` even when the client `RightClickBlock` event is cancelled. Empty-bucket pickup now cancels the event on both sides. The client therefore does not continue into vanilla bucket handling, while the server still receives the packet and performs the source removal and bucket fill. This addresses the reported occasional water placement one block above the waterlogged host.
- Outlands `RenderGlobal.playEvent` constructs destruction particles from `block.getStateFromMeta(data >> 12 & 0xFF)`. That loses the universal wet state encoded in the complete event ID and can resolve to a carrier block with a missing model. The new injection reads the complete ID through `Block.getStateById(data)`, converts a wet state to its original dry host state, and supplies that to `ParticleManager` before cancelling the legacy particle call. The sound path remains before the injection.
- `GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle gradlew.bat build --console=plain` passed; `test` reported `NO-SOURCE`. The existing client was not closed. The build does not prove runtime behavior; a newly loaded client must verify bucket pickup and stone particle texture.

## Bucket, rails, leaves, and one-cell outflow (2026-11-11)

- Empty-bucket pickup now accepts a source held in `FFDStoredFluidStates`, including hosts without a `waterlogged` state property. The client sends the pickup packet after cancelling local vanilla bucket use; the server rechecks the held bucket, distance, edit permission, and source before changing the block and inventory.
- Vanilla `BlockDynamicLiquid.tryFlowInto` drops a rail before replacing it with water. Water now intercepts that path before the drop and stores the incoming flow level in the rail cell. A flowing level remains flowing rather than becoming a source.
- When a broken waterlogged host is replaced with its contained water, the water tick is scheduled and neighbors are notified so the restored water can spread.
- Ordinary water next to a contained source now includes that source in `getFlow` direction sampling. This allows the single outflow cell's top to select the animated flowing texture and direction.
- `GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle gradlew.bat build --console=plain` succeeded after the final changes; `test` was `NO-SOURCE`. The client was left running as requested, so none of these four behaviors has been confirmed in a client loaded with this build.
