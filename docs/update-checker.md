# Update Checker

JManhunt compares the running version against the latest GitHub release
(never a pre-release) and tells admins when an update is out. The check
runs once, shortly after the plugin enables; admins who join later hear
about a pending update when they log in. Only players holding
`jmanhunt.admin` (default: op) see the notice.

```yaml
update-checker:
  enabled: true
  releases:
    major: true
    minor: true
    hotfix: false
```

Each severity toggles separately: with the defaults, new major and minor
releases notify while hotfixes stay silent. Failed checks (no network,
GitHub down) log one warning and never affect startup.
