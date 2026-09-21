package learning;

import java.util.List;

public final class CourseReleaseService {
    private final CreatorLogConfig config;
    private final CourseRelease release;
    private final InfraiLogs logs;

    public CourseReleaseService(CreatorLogConfig config, CourseRelease release, InfraiLogs logs) {
        this.config = config;
        this.release = release;
        this.logs = logs;
    }

    public List<CourseRelease.LogEvent> run(CourseRelease.Job job) throws Exception {
        List<CourseRelease.LogEvent> events = release.decide(job, config.environment());
        logs.ingest(job.jobId(), events);
        return events;
    }

    public String findDelivery(String jobId) throws Exception {
        return logs.search("digital asset delivered", jobId);
    }
}
