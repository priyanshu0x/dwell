# plans/

Internal implementation plans for the Dwell screensaver/dashboard app. Each
file in this directory is a phased, multi-milestone plan kept in version
control so design decisions and the reasoning behind them stay discoverable.

These are working documents — they may lag behind the code as phases are
completed and may be amended as decisions change. Treat them as design context,
not as authoritative architecture docs.

Not to be confused with `/plan/` (singular) at the repo root, which holds
short-lived audit notes such as `user-perspective-issues.md`.

## Current plans

- `should-we-create-a-mossy-adleman.md` — Full implementation plan for the
  idle-triggered interactive dashboard: widget API, JAR/declarative widget
  loaders, idle activation across Windows/Linux, first-party widgets,
  settings UI, packaging.
