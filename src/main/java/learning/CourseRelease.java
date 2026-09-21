package learning;

import java.util.List;

public final class CourseRelease {
    public record Job(String jobId, String creatorId, String courseId, String assetId,
                      int subscriberCount, boolean processingApproved) {}

    public record LogEvent(String message, String level, String service,
                           String environment, String traceId) {}

    public List<LogEvent> decide(Job job, String environment) {
        LogEvent processed = event(job, environment,
                job.processingApproved() ? "content processing approved" : "content processing held",
                job.processingApproved() ? "info" : "warn");
        if (!job.processingApproved()) {
            return List.of(processed);
        }
        return List.of(
                processed,
                event(job, environment, "digital asset delivered asset=" + job.assetId(), "info"),
                event(job, environment,
                        "subscriber update queued recipients=" + job.subscriberCount(), "info"));
    }

    private LogEvent event(Job job, String environment, String decision, String level) {
        String message = "course=" + job.courseId() + " creator=" + job.creatorId() + " " + decision;
        return new LogEvent(message, level, "creator-course-release", environment, job.jobId());
    }
}
