package learning;

import java.util.List;

public final class CourseReleaseTest {
    public static void main(String[] args) {
        CourseRelease workflow = new CourseRelease();
        CourseRelease.Job held = new CourseRelease.Job(
                "job-1", "teacher-4", "java-basics", "video-9", 120, false);
        List<CourseRelease.LogEvent> heldEvents = workflow.decide(held, "test");
        check(heldEvents.size() == 1, "a held asset must stop before delivery and subscriber updates");
        check(heldEvents.get(0).message().contains("processing held"), "the decision must be searchable");

        CourseRelease.Job approved = new CourseRelease.Job(
                "job-2", "teacher-4", "java-basics", "video-9", 120, true);
        List<CourseRelease.LogEvent> approvedEvents = workflow.decide(approved, "test");
        check(approvedEvents.size() == 3, "an approved asset must record all three stages");
        check(approvedEvents.get(2).message().contains("recipients=120"),
                "the subscriber decision must retain its audience count");
        System.out.println("CourseReleaseTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
