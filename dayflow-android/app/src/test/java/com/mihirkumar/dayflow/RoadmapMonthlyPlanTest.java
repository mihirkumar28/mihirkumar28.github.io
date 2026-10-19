package com.mihirkumar.dayflow;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class RoadmapMonthlyPlanTest {
    @Before public void loadCanonicalCatalog() throws Exception {
        Path path = Paths.get("app/src/main/assets/plans_catalog.json");
        if (!Files.isRegularFile(path)) path = Paths.get("src/main/assets/plans_catalog.json");
        assertTrue("Expected bundled plan catalog at " + path, Files.isRegularFile(path));
        RoadmapMonthlyPlan.initializeFromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    @Test public void sharedCatalogContainsRoadmapAndDocumentSummaries() {
        assertEquals(2, PlanCatalog.sourcePlanVersion());
        assertEquals(64, PlanCatalog.catalogFingerprint().length());
        assertEquals(16, PlanCatalog.months().size());
        int goals = 0;
        for (PlanCatalog.Month month : PlanCatalog.months()) goals += month.goals.size();
        assertEquals(51, goals);
        assertEquals(4, PlanCatalog.documents().size());
        assertTrue(PlanCatalog.documents().get(0).originalPdfBundled);
        assertEquals("unified_master", PlanCatalog.documents().get(0).id);
        assertTrue(PlanCatalog.findGoalById("general-studies-prelims-gs-polity-i-ii").sourceIds
                .contains("upsc_gs_monthly"));
        assertEquals(1, PlanCatalog.findGoalById("other-dayflow-offline-pdf-plans-library").previousDetails.size());
    }

    @Test public void allOriginalPdfAssetsAreBundledAndValid() throws Exception {
        Path base = Paths.get("app/src/main/assets/plans");
        if (!Files.isDirectory(base)) base = Paths.get("src/main/assets/plans");
        assertTrue("Expected bundled PDF directory at " + base, Files.isDirectory(base));
        for (PlanCatalog.Document document : PlanCatalog.documents()) {
            assertTrue("Catalog must mark original PDF bundled: " + document.filename,
                    document.originalPdfBundled);
            Path pdf = base.resolve(document.filename);
            assertTrue("Missing bundled PDF: " + pdf, Files.isRegularFile(pdf));
            assertTrue("PDF unexpectedly small: " + pdf, Files.size(pdf) > 5000);
            byte[] header = new byte[5];
            try (java.io.InputStream in = Files.newInputStream(pdf)) {
                assertEquals(5, in.read(header));
            }
            assertEquals("%PDF-", new String(header, StandardCharsets.US_ASCII));
        }
    }

    @Test public void coversPlannedMonthRange() {
        assertEquals(16, RoadmapMonthlyPlan.monthCount());
        assertEquals("2026-10", RoadmapMonthlyPlan.monthKeyAt(0));
        assertEquals("2027-06", RoadmapMonthlyPlan.monthKeyAt(8));
        assertEquals("2028-01", RoadmapMonthlyPlan.monthKeyAt(15));
        assertNull(RoadmapMonthlyPlan.monthKeyAt(-1));
        assertNull(RoadmapMonthlyPlan.monthKeyAt(16));
    }

    @Test public void includesAllOctoberTracks() {
        assertEquals(4, RoadmapMonthlyPlan.rowsForMonth("2026-10").length);
        assertNotNull(RoadmapMonthlyPlan.findGoal("2026-10", "General Studies"));
        assertNotNull(RoadmapMonthlyPlan.findGoal("2026-10", "Maths Optional"));
        assertNotNull(RoadmapMonthlyPlan.findGoal("2026-10", "Software Interview Prep"));
        String[] pdfGoal = RoadmapMonthlyPlan.findGoal("2026-10", "Other");
        assertNotNull(pdfGoal);
        assertTrue(pdfGoal[1].contains("Offline PDF"));
        assertTrue(pdfGoal[2].contains("airplane mode"));
    }

    @Test public void gsSequenceMatchesSavedPrelimsPlan() {
        assertTrue(RoadmapMonthlyPlan.findGoal("2026-10", "General Studies")[1].contains("Polity"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2026-11", "General Studies")[1].contains("History"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2026-12", "General Studies")[1].contains("Environment"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-01", "General Studies")[1].contains("Geography"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-02", "General Studies")[1].contains("Economy"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-03", "General Studies")[1].contains("Science"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-04", "CSE Revision & Tests")[2].contains("700"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-05", "CSE Revision & Tests")[2].contains("23 May 2027"));
    }

    @Test public void softwarePlanIsNineMonthsThenLabelsExtension() {
        for (int i = 0; i < 9; i++) {
            assertNotNull(RoadmapMonthlyPlan.findGoal(RoadmapMonthlyPlan.monthKeyAt(i), "Software Interview Prep"));
        }
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-03", "Software Interview Prep")[1].contains("Tier 3/4"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-06", "Software Interview Prep")[1].contains("Month 9"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-07", "Software Interview Prep")[2].contains("Extension beyond"));
    }

    @Test public void defensiveCopiesAndInvalidMonths() {
        String[][] rows = RoadmapMonthlyPlan.rowsForMonth("2026-10");
        rows[0][1] = "changed";
        assertTrue(RoadmapMonthlyPlan.findGoal("2026-10", "General Studies")[1].contains("Polity"));
        assertEquals(0, RoadmapMonthlyPlan.rowsForMonth("bad").length);
        assertEquals(0, RoadmapMonthlyPlan.rowsForMonth("2028-02").length);
        assertNull(RoadmapMonthlyPlan.findGoal("2026-10", "Unknown"));
    }
}
