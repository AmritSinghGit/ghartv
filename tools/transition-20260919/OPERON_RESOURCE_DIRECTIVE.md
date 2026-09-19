# Operon owner directive — concurrent lanes, bounded runtime lifecycle

Effective19September2026. Owner explicitly permits MULTIPLE concurrent product lanes. This replaces the assistant's earlier proposal of one active project across the entire Mac. It does not authorise indiscriminate deletion or stopping other agents' active work.

## Existing implementation authority

Continue Operon Owner OS PR39 / codex/opr-local-001-portfolio-runner; integrate existing Local Fabric PR56 / codex/opr-local-fabric-001. Do not build a new control website, daemon, resource database, lane registry, port allocator or deployment product. Runtime management must appear in the existing owner-control experience, including incomplete support explicitly labelled. Current installed5711-versus43918/43919 convergence has not been verified; do not force a cutover while fixing GharTV.

## Immediate next directive for every active lane

Before the next candidate starts services: read latest matching lane/task/Obsidian receipt; reconcile source vs actual runtime; detect active work including the existing Codex cleanup task; declare the intended current candidate, required shared dependencies, optional services and resource budget. Preserve dirty/unpushed/ignored private work. Reuse and update the current lane/environment runtime. Do not launch another full stack merely because a new worktree/command/chat is used.

After verified replacement: gracefully stop only proven superseded app processes/containers, retain recoverable exact artifacts and data, and report STOPPED/KEPT/DELETION_HELD with evidence. A failed candidate must not remove the prior usable one. If active work or ownership is ambiguous, keep that item, record why, and continue unrelated lanes. No global freeze because one product is blocked.

Every next receipt must include: lane + environment + repository/sourceSHA; actual runtime object IDs/start identity; current candidate/previous candidate; service roles; limits and measured working set; shared dependency owners; scheduled-worker lease; verification outcome; old/new process result; cleanup files/bytes or explicit hold; rollback reference; Obsidian/readback and GitHub status separately. Source, installed, accepted, merged and deployed SHA remain distinct.

## Admission and controls to implement in the existing console

The owner should see one lane/environment card with all its source worktrees, service roles and instances, not each worktree as a competing product. Provide Start/Reuse, Stop optional services, Suspend lane applications, Resume exact instance and Review retirement plan. Show what other lanes depend on each database/worker and block unsafe suspension. Show Mac memory pressure, VM budget, active container allocations/working sets, emulator load and queued builds. Limits alone do not establish current consumption.

Admit multiple lanes while measured headroom permits. Keep always-needed shared data services bounded and available; load workers, OCR/AI models, Selenium/browser sessions, development compilers and emulators only when their actual tasks need them. Serialize duplicate scheduled writers by tenant/source, not all product development. Queue expensive builds/AVD cold boots under pressure, leaving lightweight reviews/source work available. Use production-mode frontend bundles for review rather than several watchers when not debugging.

Use the existing Docker context/engine identity and full IDs plus image/config/mount fingerprints for actions. Recheck PID/container/start identities just before mutation. A zero-CPU sample, old timestamp, tmp suffix or numerically older worktree does not prove inactivity. Profiles group optional services but do not automatically retire already-running instances. No unrequested pauses to databases, ingestion or live meetings.

## Observed September19 inventory: facts and limits

Owner-provided desktop-linux snapshot:65containers total/43active,105images,84volumes. Three IdentiFlow stacks, three active JalNirnay stacks and12Supabase containers were running along with Personal Intelligence and shared VCNow PostgreSQL. This is a point-in-time upload, not a current API observation. Supabase's temporary-looking project name does not establish that its data/task is disposable. Several VM-sized7.746GiB limits in docker stats suggest absent per-service caps but effective HostConfig must be inspected before that claim is made.

Docker screenshot: CPU ceiling18, VM memory8GB, swap1GB, statusRAM7.46GB and disk242.32GB. The disk slider is a MAXIMUM, not current use; do not shrink/delete the Docker disk image as cleanup. Existing consumption is close to8GB, so the earlier blanket6GiB recommendation is withdrawn pending workload retirement and measurement. Do not apply a reduced limit to active data services without planned downtime and validation.

IdentiFlow PR38 currently names identiflow_dev_41fb0d3fed as its existing project. The older4e7ee30787 and99fae6a23d app front/back pairs are candidates for a reversible, explicitly confirmed suspension; their PostgreSQL containers/volumes remain protected. No retirement decision is established for JalNirnay, Supabase, current PI, core VCNow, active Pexip/MeetingOps or the control-plane services until their owners/dependencies are reconciled.

## Cleanup and production gates

RAM relief: stop verified idle/superseded APPLICATION workloads; record exact objects and a tested/verified resume path. Disk cleanup: separate inventory; backup/remote-retrieval proof for regenerable artifacts; remove only exact safe items after replacement acceptance. Private database/media backups never go to public GitHub. No docker system/image/volume prune, compose down-v, Docker reset, Docker.raw removal/shrink, git reset/clean/prune/stash/bulk rebase or branch/worktree deletion from this directive. No age/name-only automatic deletion.

Production migrations use existing Mac→staging→approved production targets, not guessed hosts. Choose accepted exact source/artifact, health checks, secrets/data backup and restore evidence, system resource limits and rollback. A merged PR does not stop local resources; an APK published to GitHub does not move its web/owner backend to a server. Keep the last recoverable local artifact until production functionality and data integrity have been checked, then stop the retired local runtime explicitly.

The provided one-shot resource_transition.py is a bridge for current evidence and optional reversible old-IdentiFlow app suspension. It is NOT the completed Operon UI integration. Default is audit-only; mutation requires the owner to attest those old apps/tasks and external dependants are idle. It preserves all containers/volumes/images/files, never recreates them and never automatically starts a review. Private results stay on the Mac and in its existing Obsidian lane. The actual existing Codex task should read this directive and adopt its results instead of running competing cleanup.

Primary references: Docker Desktop Resources settings, Resource Saver, docker stats, Compose profiles, container stop timeout semantics, Docker Desktop Mac disk-image FAQ. Source-derived measurements and proposals remain separately labelled. All new limits or consolidation choices must be based on fresh local evidence and recorded as applied only after readback.
