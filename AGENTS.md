# Session completion note

When the work in a session on this repository is fully complete, create or update a Markdown report under `docs/` summarizing the delivered changes and verification results.

# Waterlogged repair requirement

For waterlogged fixes, investigate the complete lifecycle before changing code:

- Persist the contained fluid and the host waterlogged state together on the server.
- Restore them together after chunk load; never leave a dry host with a residual independent fluid record.
- Treat ordinary vanilla water as an existing fluid, not as a new block to recreate during reload.
- Keep client state render/synchronization-only; the client must not create, persist, or resurrect contained water during block breaking.
- On block breaking, bucket collection, neighbor updates, and fluid exhaustion, remove or update the independent record atomically with the host state.
- Verify the exact sequence in one pass: place water, save, exit, reload, inspect F3 state, break the host, collect with an empty bucket, and inspect server/client logs.
- Prefer an upstream-compatible, complete repair over incremental patches. Do not claim completion from compilation alone; require a fresh `runclient` verification and record the evidence in `docs/`.

- 扩散进入含水宿主时，必须同步保存 host waterlogged=true 与独立流体状态，避免重进后干状态加残留水记录。

- 扩散流体同时写入 FFDStoredFluidStates（等级）与 FFDFluidloggedData（类型）作为区块持久化兜底；流尽时必须原子清除两者。
