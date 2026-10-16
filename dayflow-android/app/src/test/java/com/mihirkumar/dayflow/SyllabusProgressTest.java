package com.mihirkumar.dayflow;

import org.junit.Test;

import static org.junit.Assert.*;

public class SyllabusProgressTest {
    @Test public void oneOfElevenTopicsDisplaysNinePercent() {
        assertEquals(9, SyllabusProgress.percentage(1, 11));
        assertEquals("Syllabus 9%", SyllabusProgress.label(1, 11));
    }

    @Test public void percentageUpdatesFromSameTopicCounts() {
        assertEquals("Syllabus 0%", SyllabusProgress.label(0, 11));
        assertEquals("Syllabus 18%", SyllabusProgress.label(2, 11));
        assertEquals("Syllabus 100%", SyllabusProgress.label(11, 11));
    }

    @Test public void interviewTopicsAreExcludedFromCseSyllabusProgress() {
        assertTrue(SyllabusProgress.countsTowardCseSyllabus("General Studies"));
        assertTrue(SyllabusProgress.countsTowardCseSyllabus("Maths Optional"));
        assertFalse(SyllabusProgress.countsTowardCseSyllabus("Software Interview Prep"));
        assertTrue(SyllabusProgress.isInterviewPrep("Software Interview Prep"));
        assertFalse(SyllabusProgress.isInterviewPrep("CSAT"));
    }

    @Test public void emptyAndInvalidCountsAreHandledSafely() {
        assertEquals("Syllabus not set", SyllabusProgress.label(0, 0));
        assertEquals("Syllabus not set", SyllabusProgress.label(3, -1));
        assertEquals(0, SyllabusProgress.percentage(3, 0));
        assertEquals(0, SyllabusProgress.percentage(-1, 11));
        assertEquals(100, SyllabusProgress.percentage(15, 11));
    }
}
