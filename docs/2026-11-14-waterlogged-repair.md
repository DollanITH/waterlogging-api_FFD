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
