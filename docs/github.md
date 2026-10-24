# GitHub setup

The files are GitHub-ready. This workspace originally had no Git metadata or remote; connecting a
repository and enabling hosted features require the repository owner's GitHub access.

After committing the source and reviewing the dependency baseline:

- Set the default branch to `main`.
- Enable Actions, dependency graph, Dependabot alerts, and private vulnerability reporting.
- Enable CodeQL code scanning where your repository plan supports it.
- Enable GitHub's automatic dependency submission for Gradle if you want dependency review to include
  the complete resolved transitive graph; catalog/manifest review alone is not equivalent.
- Require PRs, one approving review, resolved review conversations, and up-to-date checks on main.
- Require `Unit and analysis`, `AWS integration`, `Packaged JVM`, `Mutation`, `Wrapper validation`,
  and `CodeQL`; require `Dependency review` on pull requests where supported.
- Disable force pushes and deletion of main. Consider rules protecting stable `v*` tags.

CI runs on PRs, main pushes, merge groups, and manual dispatch. No PR job uses repository secrets,
`pull_request_target`, persisted Git credentials, or AWS access. Shared Gradle caches are read-only
outside main. Reports are retained as workflow artifacts. The security scan also runs weekly.
Action references use full commit SHAs; Dependabot tracks their version comments.

## Release

Manually dispatch **Release** from main with a stable semantic version such as `0.1.0`.
The workflow validates the input, rejects an existing tag, runs `verifyAll`, builds the versioned
JVM distribution and runtime SBOM, and verifies the archive layout. Only a separate publishing job
has contents-write permission. It creates a draft GitHub Release at the verified source commit,
uploads the artifacts, then publishes the release with generated notes. Releases are serialized.

No deployment occurs. If asset upload fails, the release remains a draft; inspect it before retrying.
An existing tag is never overwritten. All repository-level settings above are documented recommendations,
not changes automatically applied by these files.
