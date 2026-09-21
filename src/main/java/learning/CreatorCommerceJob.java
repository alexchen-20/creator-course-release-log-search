package learning;

import java.util.List;

public final class CreatorCommerceJob {
    public static void main(String[] args) throws Exception {
        CreatorLogConfig config = CreatorLogConfig.load();
        InfraiLogs logs = new InfraiLogs(config);
        CourseReleaseService service = new CourseReleaseService(config, new CourseRelease(), logs);
        CourseRelease.Job job = new CourseRelease.Job(
                "release-java-204", "creator-18", "course-observability", "lesson-video-6", 42, true);

        List<CourseRelease.LogEvent> events = service.run(job);
        System.out.println("Shipped " + events.size() + " course-release log events.");
        System.out.println("Search result: " + service.findDelivery(job.jobId()));
    }
}
