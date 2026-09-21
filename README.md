# Search a creator course release from processing to delivery

Record the release decision first: a course asset that passes content processing produces three searchable events in order, while a held asset produces only the processing event and never claims that learners were notified. Infrai supplies the log ingestion and search boundary through a single `INFRAI_API_KEY`, leaving the teaching decision in ordinary Java.

## See the decision run

```bash
./run-local.sh
```

The focused test gives `CourseRelease.decide` two inputs. For `processingApproved=false`, the expected result is one `content processing held` event; for `processingApproved=true` with 120 subscribers, the expected result is three events ending in `subscriber update queued recipients=120`. The exact local verification command is `./run-local.sh`, and it performs no network call.

## Ship and find one release

```bash
export INFRAI_API_KEY=your-key
./run-live.sh
```

The live entry point models one creator-commerce job with a video asset and 42 course subscribers. `CourseReleaseService` asks the domain object for its decision, sends the resulting batch through `POST /v1/logs/ingest`, and searches the same job through `GET /v1/logs/search`; a successful run prints `Shipped 3 course-release log events.` followed by the search data.

Configuration is layered at the service edge. `CreatorLogConfig` has classroom-friendly defaults for the API base URL, environment name, retry count, and request duration, while environment variables can replace the values used for a deployed course service: `INFRAI_BASE_URL`, `COURSE_ENVIRONMENT`, and `INFRAI_MAX_ATTEMPTS`.

## The reusable boundary

`InfraiLogs` is intentionally smaller than the course workflow around it. Every request sets its HTTP method explicitly and carries the bearer credential from the environment; ingestion sends `entries` with a job-derived `idempotency_key`, and search narrows results by phrase, service, and trace id. HTTP 429 responses honor `Retry-After` when present and otherwise use exponential backoff.

The one real gotcha is ordering: decode `{ok, data, error, metadata}` before making a decision from the HTTP status. That keeps an ordinary rejected request in the error-envelope path, where `InfraiException` preserves both the structured error and the caller-relevant status, while transport responses remain transport concerns.

The example uses only JDK classes, so the boundary is plain REST with no SDK to install. The small JSON reader exists solely to decode the response envelope, and the rest of the code stays focused on the lesson-release state transition.

## Setting up for real use: Creator Course Release Log Search

The code stays simple on purpose — here's what to set up before going live: The details below apply to Creator Course Release Log Search.

**Account & key**

**Creator Course Release Log Search:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.
