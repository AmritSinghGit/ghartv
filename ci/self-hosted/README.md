# GharTV self-hosted CI contract

The repository's provider-neutral validation entry point is:

```sh
./ci/run.sh
```

Use the same command locally, from CircleCI cloud jobs, and from an Amrit-controlled self-hosted CircleCI runner. This keeps validation portable and prevents the workflow from depending on GitHub Actions alone.

## Runner boundary

- Run under a dedicated, non-root operating-system account.
- Pin the runner to this repository or another explicitly approved trusted-project group.
- Do not expose Android release-signing material, Cloudflare production credentials, Jio credentials, telemetry administration tokens, or customer data to pull-request jobs.
- Do not execute untrusted fork pull requests on a runner that can reach private networks or persistent credentials.
- Keep build output disposable. Preserve the receipt and debug APK as CI artifacts.
- Configure the exact CircleCI namespace and self-hosted `resource_class` only after the owner creates the runner resource; do not invent these values in source control.

## Migration gate

Keep the existing GitHub workflow during the pilot. Remove it only after the same exact commit has passed both the existing workflow and CircleCI/self-hosted validation, with matching source checks and independently retained artifacts. Moving the Git repository away from GitHub is a separate owner-authorized migration.
