# Agent working rules

## Delegating work

- Orchestrate; don't do bounded work yourself. Opus briefs and reviews. Sonnet (low–medium)
  does routine pieces such as README or doc rewrites, message polish and cold-read reviews.
  The owner said (2026-10-19) the coordinator was "doing too much and not properly prompting
  workers".
- Split work by difficulty up front and tell Opus workers they may delegate to Sonnet.
- Write self-contained briefs: verified facts, exact acceptance checks, commit rules,
  toolchain. Keep your own verification to light spot-checks plus the final build.
- Prefer Claude workers (Opus 5.5, Sonnet 5.5). On Codex use only Luna or paid DeepSeek;
  the owner is low on Codex quota (2026-10-19). Re-check if they say it has recovered.

## Referencing other projects

- Every design, CI, release, Dependabot or repo-hygiene task must study established
  projects first and cite what it adopted (file path or URL). Baseline set: DrexHD's mods,
  gnembon/fabric-carpet, lucko/spark, lucko/LuckPerms, GeyserMC/Geyser, ViaVersion,
  walterhiggins/ScriptCraft; add others that fit. Three repos with "nothing to borrow" is not
  research; the owner rejected that (2026-10-20).
- Verify a worker's "project X doesn't do Y" claims before relaying them.
