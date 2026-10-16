package com.mihirkumar.dayflow;

/**
 * Shared definition of syllabus-topic progress shown on Today and Roadmap.
 * Roadmap goals and milestones are deliberately excluded from this percentage.
 */
final class SyllabusProgress {
    private static final String INTERVIEW_SUBJECT = "Software Interview Prep";

    private SyllabusProgress() {}

    static boolean isInterviewPrep(String subject) {
        return INTERVIEW_SUBJECT.equals(subject);
    }

    static boolean countsTowardCseSyllabus(String subject) {
        return !isInterviewPrep(subject);
    }

    static int percentage(int completed, int total) {
        if (total <= 0) return 0;
        int safeCompleted = Math.max(0, Math.min(completed, total));
        return Math.round(safeCompleted * 100f / total);
    }

    static String label(int completed, int total) {
        if (total <= 0) return "Syllabus not set";
        return "Syllabus " + percentage(completed, total) + "%";
    }
}
