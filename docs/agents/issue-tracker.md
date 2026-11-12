# Issue tracker: GitLab

Issues and specs for this repo live as GitLab issues (`nuestra-traviesa/GestionPhotosBackend`). Use the `glab` CLI for all operations. The GitHub repo (`Bertolen/GestionPhotosBackend`) is a read-only archive: its issues #1-5 are closed and point to their GitLab twins.

## Conventions

- **Create an issue**: `glab api --method POST projects/nuestra-traviesa%2FGestionPhotosBackend/issues -f title="..." -f description="..." -f labels=<label>`. For multi-line or accented bodies on Windows, read the body from a UTF-8 file (`Get-Content -Raw -Encoding UTF8 <file>`) and pass it as `-f description="$body"` — never inline French accents in a `.ps1` literal (PowerShell 5.1 reads `.ps1` files as ANSI).
- **Read an issue**: `glab api projects/nuestra-traviesa%2FGestionPhotosBackend/issues/<iid>`.
- **List issues**: `glab api "projects/nuestra-traviesa%2FGestionPhotosBackend/issues?state=opened&per_page=100"`.
- **Comment on an issue**: `glab api --method POST projects/nuestra-traviesa%2FGestionPhotosBackend/issues/<iid>/notes -f body="..."`.
- **Apply / remove labels**: `glab api --method PUT projects/nuestra-traviesa%2FGestionPhotosBackend/issues/<iid> -f add_labels=wayfinder:research` / `-f remove_labels=...`.
- **Close**: `glab api --method PUT projects/nuestra-traviesa%2FGestionPhotosBackend/issues/<iid> -f state_event=close`.
- **Assign (claim)**: `glab api --method PUT projects/nuestra-traviesa%2FGestionPhotosBackend/issues/<iid> -f "assignee_ids[]=43638255"` (Bertolen).

**PowerShell 5.1 caveat**: it strips embedded double quotes in native-command args. For GraphQL queries, run `glab.exe` from a WSL bash *script file* (never `-c` one-liners from PowerShell): `bash /mnt/c/Users/Bertolen/AppData/Local/Temp/opencode/<script>.sh` with `GLAB="/mnt/c/Users/Bertolen/AppData/Local/Programs/glab/glab.exe"`.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Les merge requests GitLab sont un usage interne uniquement.)_

## When a skill says "publish to the issue tracker"

Create a GitLab issue (see **Create an issue** above).

## When a skill says "fetch the relevant ticket"

Read it as in **Read an issue** above.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue labelled `wayfinder:map`; its tickets are issues of the same project.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. Create as any other issue with `-f labels=wayfinder:map`.
- **Child ticket**: an issue in the same project. GitLab does not allow ISSUE-type work items as children of an ISSUE parent (`workItemsHierarchyReorder` rejects it — only TASK children are allowed), so the parent/child relation is expressed by convention: a task list of the tickets in the map body, and `Part of #<map-iid>` at the top of each ticket body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: body convention. Native `is_blocked_by` issue links require a Premium license — GitLab.com Free answers `403 "Blocked issues not available for current license"` (verified empirically) — so blocking is expressed by a `Bloqué par : #<n>, #<m>` line at the top of the child body, right after the `Part of` line. If the project ever upgrades, the call is `glab api --method POST projects/nuestra-traviesa%2FGestionPhotosBackend/issues/<child-iid>/links -f target_project_id=88770936 -f target_issue_iid=<blocker-iid> -f link_type=is_blocked_by`. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open tickets (`glab api "projects/.../issues?state=opened&per_page=100"`, keep those whose body starts with `Part of #<map-iid>`), drop any whose `Bloqué par :` line names a still-open issue, or that has an assignee; first in map order wins.
- **Claim**: assign the ticket to the driving dev (see **Assign** above), the session's first write.
- **Resolve**: comment the answer on the ticket, close it, then append a context pointer (gist + link) to the map's Decisions-so-far.
