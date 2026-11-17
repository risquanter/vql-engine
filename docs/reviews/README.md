# Review records

Each review tier — `/code-review-routine`, `/code-review-complex` and
`/security-review-deep` — appends its coverage entry to
`REVIEW-<branch>.md` in this directory, one file per branch. The next tier
reads the entry, so a check already discharged is not run again.

A coverage entry lists the files read in full, each checklist item with its
result and the location that established it, and the areas escalated to a
further tier. Its format is in `~/.claude/skills/review-protocol/SKILL.md`,
"Coverage entry". The criteria the tiers apply are in
`.claude/skills/vql-code-quality-review/SKILL.md`.
