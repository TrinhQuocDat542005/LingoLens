# Durable account sync protocol

## Client lifecycle

1. Save/delete updates Room and inserts an operation in one transaction. Recognition and correction metadata also enter the same durable queue.
2. A connected WorkManager job drains operations in increasing local sequence. Operation UUIDs stay unchanged on retries.
3. The worker verifies the signed-in account before each request. The HTTP interceptor fences sync requests against the stored profile; the server independently verifies the expected account against the JWT user.
4. Only a matching successful receipt removes an operation. Process death, cancellation or a lost response leaves it available for replay.
5. After draining, pull the full account learning snapshot and merge locally. New operations created while the GET was in flight protect their words from that snapshot.

Immediate jobs use an account-specific `APPEND_OR_REPLACE` chain to cover enqueue/finish races. Periodic connected jobs run approximately every 15 minutes subject to Android scheduling. Startup and signed-in navigation resume work. A process-wide mutex prevents immediate/periodic drains from overlapping; server transactions cover independent devices and processes.

Room schema 1 → 2 preserves words, first-learning events and vocabulary cache. On the first run for an account, bootstrap queues existing saved words and all historical learning events once, including words deleted before this feature existed.

## Wire contract

Authenticated `POST /api/v1/sync/operations`:

```json
{
  "accountId": 123,
  "operationId": "b8017e4b-6f94-48a2-9d43-e18c7a99f7e2",
  "kind": "RECOGNITION",
  "payload": {"detectedLabel": "book", "confidence": 0.9, "engine": "EFFICIENTDET"}
}
```

Response: `data.operationId` and nullable `data.remoteId`. Account ID is a consistency check, never an authorization grant.

| Kind | Payload | Effect |
| --- | --- | --- |
| `SAVE_WORD` | `word`, object `content`, epoch-ms `savedAt`, `firstLearnedAt` | Upsert personal snapshot; retain earliest learning date |
| `DELETE_WORD` | `word` | Mark a tombstone; retain first-learning history |
| `RECORD_LEARNING` | `word`, epoch-ms `firstLearnedAt` | Import historical first-learning date without restoring a deleted word |
| `RECOGNITION` | `detectedLabel`, `confidence`, `engine` | Create one recognition history row |
| `REPORT` | `recognitionOperationId`, `expectedLabel`, optional `note` | Resolve an owned recognition receipt and submit one correction |

`GET /api/v1/sync/learning?accountId=123` returns `data` as an array of `{word, content, savedAt, firstLearnedAt, deleted}`. `content` is a JSON string containing a personal vocabulary snapshot, not shared/published catalog content. Tombstones and historical first-learning dates are included.

## Server invariants

- Expected account must equal the authenticated database user. Authentication still enforces disabled-user status.
- Account mutation transactions lock the user row. Effect and receipt either both commit or both roll back.
- Receipt key is `(user_id, operation_id)`. A canonical payload hash rejects conflicting UUID reuse with `409`; field order alone does not change the hash.
- Replaying an acknowledged save returns its original receipt without executing again, even after a later delete.
- Report dependencies must reference that account's recognition receipt. Existing recognition ownership and single-report constraints remain enforced.
- Payload size, vocabulary snapshot fields, timestamps and recognition/report validation are checked before acknowledgement.

## Retry and failure policy

| Failure | Client behavior |
| --- | --- |
| Transport / 408 / 429 / 5xx | Keep operation; increment attempts; exponential WorkManager retry starting at 30 seconds |
| 401 / 403 | Keep operation; pause until a valid matching session resumes sync |
| Other rejection or invalid acknowledgement | Keep operation as blocked; stop FIFO processing and show error |
| Cancellation / process death | Propagate cancellation; retain unacknowledged operation UUID |
| Different signed-in account | No drain or pull for the previous account |

The sync card shows pending count, last successful full sync and failure state. Manual **Sync / retry** resets blocked operations and requests work. Repeated invalid input remains blocked; it is not silently discarded. There is no automatic dead-letter deletion.

## Conflict and retention policy

Different devices preserve their own FIFO queues. Across devices, the last server-accepted save/delete determines library presence and content. A stale offline edit may therefore supersede a newer-looking edit on another device; client wall clocks do not decide winners. The first-learning date is the minimum recorded timestamp. History is separate from library deletion.

Receipts/tombstones are retained indefinitely in this version. Pruning requires a future protocol defining a replay horizon; deleting them opportunistically would break retry safety. Snapshot pull is currently a full account read, so large-library pagination/delta synchronization is future work.

## Verification boundaries

Automated checks cover Room persistence/migration, FIFO, ambiguous success, account isolation, failure policy, concurrent local edits during cloud pull, bootstrap, concurrent refresh and late-session races. Backend H2/PostgreSQL tests cover atomic rollback, payload-conflict rejection, dependent reports, deletion replay and concurrent identical operations. Actual Doze, process-kill, reconnect timing and multi-phone behavior require the device checklist in the week 3 report.
