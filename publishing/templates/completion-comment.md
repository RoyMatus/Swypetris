Completed #N through [PR #P](<URL>): <behavior and files>. Release: <URL or Merged - unreleased>. Device/store limits: <facts or not applicable>.

Recorded active work: <minutes and seconds or Unknown> (coverage: Complete|Partial|Unknown; source: `app/build/verification/.../work.jsonl`). Breakdown: Implementation <s>, Review <s>, ReleasePreparation <s>, Rework <s, already included in active>. LocalChecks phase <s>; observed Waiting/CIWait <s>. Runner/check durations and outcomes: <suite, scope, count, duration, SHA, fresh/reused/cached evidence>. Delivery elapsed: <first to last observed boundary and s, or partial/unknown>. Missing intervals: <explicit gap>; estimates, if any, are separate.

Problems: <observed failure, diagnostic reference, supported cause, resolution/retry and remaining unknown; or "No problems observed">. Bottleneck: <phase/task, observed duration/count, comparable baseline or "none", impact; do not assert excess without baseline>. Improvement candidate: <evidence, proposed change, expected benefit as prediction, validation preserving assertions/isolation/local gates>. Follow-up: <existing issue link or authorized new issue, if any>.

For a cancelled issue, replace completion claims with the decision, `not_planned` reason, work/time coverage if any, and effects on the epic. Never use this template to describe an open/On Hold ticket as delivered. Post one consolidated English comment after verified merge/closure, then re-read the API result.

## Illustrative wording (replace every placeholder with observed evidence)

Completed: "PR #P delivered <behavior>. Recorded active work: <m:ss> (coverage: Complete; source: <journal>); LocalChecks <m:ss>; fresh tests <counts and evidence>; no problems observed. Candidate: <observed repeated step> could use <change>, expected to reduce <cost>; validate with unchanged assertions and the exact-head gate."

Partial measurement: "Recorded active work: <m:ss> lower bound (coverage: Partial; source: <journal>); work before <first recorded start> is unknown. The <runner> took <s>; it is not added to active work."

Observed slow work: "The <phase/test> took <s> across <count> attempts; no matching baseline exists, so excess time and cause are unproven. <Diagnostic> suggests <bounded inference>. Try <change>; expect <benefit>, then compare matching scope and retain all assertions/local gates."

Cancelled: "Closed as not_planned because <decision and evidence>. No implementation or release is claimed. Recorded time: <value/coverage or Unknown>. Epic consequence: <queue/criterion change>."
