# Spaced review: behavior and scheduling contract

## User flow

Save vocabulary → open Review from Home / My Words / Stats → recall the English word → reveal translation, definition and example → choose a grade → view session summary. Device Text-to-Speech is available on the question side. The UI blocks grading before reveal and disables actions during writes.

Sessions snapshot up to 20 due saved words, ordered by due time and word. Due means `dueAt <= now`. An unfinished account-scoped session resumes after navigation, restart or database reopen; its word keys, position and answer visibility are stored in Room. Compose saved state retains the session ID during process recreation, including completed summaries. Deleted words are skipped without synthetic answer events. Again does not append a word to the current finite batch; start another session after its due time.

## Shared deterministic algorithm

Source: `shared/src/main/kotlin/com/quocdat/lingolens/review/core/ReviewScheduler.kt`, compiled by both Android and backend. The scheduler has no Android, database, Spring or network dependency. This is a custom heuristic; it makes no validated claim about recall probability and does not implement FSRS or SM-2.

| Grade | New / learning card | Review card | Ease change |
| --- | --- | --- | --- |
| Again | 1 minute, learning | 1 minute, learning; reset interval and repetitions; increment lapses | −0.20 |
| Hard | 10 minutes, learning | ceil(previous days × 1.2) | −0.15 |
| Good | 1 day, review | ceil(previous days × ease) | unchanged |
| Easy | 4 days, review | ceil(previous days × updated ease × 1.3) | +0.15 |

Ease starts at 2.5 and stays within 1.3–3.0. Day intervals stay within 1–365. One day is a fixed 86,400,000 ms duration; local calendar/timezone is used for activity statistics and presentation. The scheduling anchor is `max(reviewedAt, previous.lastReviewedAt)`. Grade history keeps the submitted timestamp; it is not a trusted server clock. Backend accepts positive integer timestamps no more than one day into its future. Incorrect device clocks can therefore block an upload; the sync status exposes that rejection.

## Persistence and conflict policy

`ReviewCard` is keyed by account + normalized word. Its schedule survives deletion and resave; due queries join current saved words, so removed words cannot enter review. `ReviewEvent.id` equals the durable outbox operation UUID. A Room transaction updates the schedule, records the answer, advances the session and inserts `REVIEW_WORD`. An expected-position check prevents a duplicate tap from grading the next card. WorkManager scheduling happens only after commit; startup recovery covers an interrupted enqueue.

Backend V8 stores schedules and immutable events. The existing account lock and receipt transaction prevent duplicate scheduling on ambiguous success or concurrent retry. It computes the authoritative result using the shared algorithm. Reviews from multiple devices apply in server acceptance order; this is not event-time sorting or a CRDT. The next successful snapshot replaces unprotected local schedules and merges answer IDs. Local edits made during download protect their words; queued review answers remain intact.

Sessions are installation-local; schedules and answers restore on another installation after sign-in and sync. This version uses full snapshots with indefinite history retention. Pagination, pruning/replay horizons, adaptive retention modeling and review reminders are future work.

## Statistics

- Due cards and next due timestamp reflect the saved schedule; overview time refreshes approximately every 30 seconds while composed.
- Today and seven-day review counts use actual immutable events in the current timezone.
- Self-reported recall = `(Hard + Good + Easy) / all answers`; an empty period displays 0%, not a validated memory estimate.
- Card distribution shows New, Learning and Review states. Streak includes first-save and review activity; the daily vocabulary goal and existing new-word chart continue to use first saves only.

## Device acceptance walkthrough

1. Sign in, save at least two published words, disable networking and open Review.
2. Verify translation is hidden, reveal, listen with device TTS, grade a word and inspect the next card.
3. Close/reopen, rotate, and restart the process; verify cursor and visibility resume. Sign into another account and confirm isolation.
4. Delete a remaining word in My Words and reopen the session; verify it is skipped. Resave a graded word and verify its prior due schedule remains.
5. Grade Again; complete the batch, wait one minute and start another session. Verify due count updates and no duplicate answer appears on rapid taps.
6. Reconnect, observe sync status, then sign into a second installation. Verify word library, schedule and answer statistics restore; session position remains installation-local.

Automated persistence/API tests complement this checklist. No physical-device walkthrough or measured retention study is claimed in this milestone.
