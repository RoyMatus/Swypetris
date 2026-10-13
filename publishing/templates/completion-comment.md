Completed #N in [PR #P](<URL>): <actual delivered behavior>. <Release URL or Merged - unreleased>.

Selected local checks: <suite, actual result/count/duration and evidence link or path>; <exact-head local gate result>. Recorded active work: <observed lower bound and Partial/Complete coverage with source, or Unknown>. <Relevant physical-device result or limitation, if applicable>.

<Only for a substantive failure, retry, rework or follow-up: observed facts, evidence and remaining uncertainty.>

Prepare with `tools/release/New-TicketCompletion.ps1` when runner/gate summaries exist. Review the draft, post one English comment after verified merge and issue closure, and re-read the API. Do not turn an unknown time into zero or sum check runtime into active work. A cancelled issue gets a decision summary and `not_planned` reason instead of implementation claims.
