package com.mihirkumar.dayflow;

import java.util.Arrays;

/**
 * Source-aligned month-by-month goals for the DayFlow roadmap.
 *
 * Dates are anchored to the saved October 2026–January 2028 CSE plan.
 * GS targets through May 2027 follow the saved Prelims GS monthly PDF.
 * Maths Optional dates are a proposed sequence based on the saved IMS-note
 * spine and UPSC syllabus; the saved GS PDF does not provide a Maths month map.
 * Software goals follow the saved 6-/9-month EM interview plan through June 2027.
 * Later software rows are an explicitly labelled execution/maintenance extension.
 */
final class RoadmapMonthlyPlan {
    private RoadmapMonthlyPlan() {}

    private static final String START_MONTH = "2026-10";

    private static final String[][][] MONTHS = {
        {
            {"General Studies", "Prelims GS: Polity I + II",
                "October 2026 · Primary source: Vajiram Yellow Books Polity I + II (about 584 pages). Complete one static pass; make concise Constitution, institutions and governance notes; solve PYQs after major topics. Plan roughly 12–13 hours/week for static reading and 3–4 hours/week for PYQs/revision; keep current affairs integrated rather than creating a separate block."},
            {"Maths Optional", "Paper I: Linear Algebra foundations",
                "Start with the December 2022 IMS notes as the conceptual spine. Cover vector spaces and subspaces, basis/dimension, linear transformations, matrices and rank. Keep a proof/problem error log and measure progress by mastered topics, worked problems and PYQs rather than page count."},
            {"Software Interview Prep", "Month 1: Java/JVM baseline + DSA reset",
                "Core Java/OOP, collections, generics, exceptions, streams and JVM/memory basics. DSA: arrays, strings, hashing, two pointers and sliding window. Deliverables: 20–25 timed problems, 3 evidence-based EM stories and one Java debugging lab; record the starting baseline."},
            {"Other", "DayFlow: Offline PDF plans library",
                "October 2026 · Make the four saved plan PDFs accessible inside DayFlow from More → Plans & Documents, without an internet connection. Current implementation: the library screen and navigation exist, but the screen shows concise reading copies rather than the original PDFs. Remaining work: bundle the original Unified Master Execution Plan, UPSC 2027 Prelims GS Monthly Plan, 2027 EM 6/9-Month Master Plan and 2027 EM Interview Readiness Plan as local app assets; update each entry to open its original PDF; build the APK and verify all four open in airplane mode. Keep this goal open until original-file access and offline verification pass."}
        },
        {
            {"General Studies", "Prelims GS: Modern, Ancient and Medieval History",
                "November 2026 · Primary sources: Vajiram Modern History (about 296 pages), Ancient History (about 140) and Medieval History (about 103), approximately 539 pages combined. Build chronology/theme notes and use PYQs to identify examinable causes, outcomes, movements and contributors; avoid elaborate note-making."},
            {"Maths Optional", "Paper I: Calculus and remaining Linear Algebra",
                "Continue linear transformations, eigenvalues/eigenvectors and matrix methods; build limits, continuity, differentiability, mean-value results and single-variable integration. Attempt selected problems without looking at worked solutions; retain difficult proofs for spaced revision."},
            {"Software Interview Prep", "Month 2: Concurrency + LLD",
                "Threads, executors, locks, atomics, Java Memory Model and CompletableFuture; object-oriented design, SOLID, testability and code review. Deliverables: 25–30 cumulative timed problems, a concurrency lab, 4 LLD drills and 3 additional EM stories."}
        },
        {
            {"General Studies", "Prelims GS: Environment + Art & Culture",
                "December 2026 · Primary sources: Vajiram Environment & Ecology (about 307 pages) and NEXT IAS Art & Culture (about 184), approximately 491 pages combined. Keep factual material in compact revision sheets; connect ecology, biodiversity and conservation concepts to PYQs and current events."},
            {"Maths Optional", "Paper I: Analytic Geometry + Ordinary Differential Equations",
                "Cover lines, planes and standard curves/surfaces, followed by first- and higher-order ordinary differential equations and standard solution methods. Maintain a worked-problem/error log and revisit prerequisites whenever solutions fail."},
            {"Software Interview Prep", "Month 3: Spring + SQL + HLD foundation",
                "Spring Boot DI, REST, validation, transactions and JPA/Hibernate; PostgreSQL and SQL tuning; CAP, consistency, caching and queues. Deliverables: one deployable service slice, 4 HLD drills, a SQL tuning set and 8 total core EM stories."}
        },
        {
            {"General Studies", "Prelims GS: Physical/World + Indian Geography",
                "January 2027 · Primary source: NEXT IAS Physical & World Geography (about 211 pages) plus Indian Geography & Disaster Management (about 382), approximately 593 pages. Prioritise concepts, maps and diagrams. This is the heaviest month by page count; keep notes short and use topic-wise PYQs."},
            {"Maths Optional", "Paper I: Dynamics and Statics",
                "Cover motion, work and energy, equilibrium, forces, potential energy and standard dynamics/statics problem types. Practise derivations and timed numerical questions; integrate these with previously studied Calculus rather than treating them as isolated formulas."},
            {"Software Interview Prep", "Month 4: Messaging + reliability + production thinking",
                "Kafka/RabbitMQ/Redis; retries, ordering, idempotency, observability, SLOs, incident/postmortem reasoning and target-role calibration. Deliverables: one async workflow, 2 incident drills, 2 external calibration interviews/mocks and a company-role matrix."}
        },
        {
            {"General Studies", "Prelims GS: Indian Economy",
                "February 2027 · Primary source: NEXT IAS Indian Economy (about 467 pages). Study mechanisms rather than terminology alone: concept → mechanism → Indian application → PYQ. Consolidate budgeting, growth, inclusion, agriculture and related economic institutions with mixed practice."},
            {"Maths Optional", "Paper I: Vector Analysis + integration of Paper I",
                "Cover grad, div, curl, line/surface/volume integrals and the major integral theorems. Consolidate Linear Algebra, Calculus, Analytic Geometry, ODE, Dynamics/Statics and Vector Analysis; identify syllabus gaps before moving into Paper II."},
            {"Software Interview Prep", "Month 5: Tier 3/4 interview conversion",
                "Practise harder coding, mixed HLD/LLD, architecture trade-offs, working React/TypeScript breadth and mixed EM rounds. Deliverables: 2 full mock loops, 10–12 strong leadership stories, 60–80 cumulative timed problems and 8–10 design cases."}
        },
        {
            {"General Studies", "Prelims GS: General Science + Science & Technology",
                "March 2027 · Primary sources: NEXT IAS General Science (about 135 pages) and Science & Technology (about 231), approximately 366 pages. Finish the static source set by 31 March; use remaining time for cumulative PYQs, revision and backlog clearance. The page counts are edition-dependent planning figures."},
            {"Maths Optional", "Paper I closure + Paper II Algebra foundations",
                "Audit every Paper I unit against the UPSC syllabus, attempt representative PYQs and close high-impact gaps. Begin Paper II Algebra: groups/rings and core algebraic structures, notation and standard proofs. Update the error log from timed work."},
            {"Software Interview Prep", "Month 6: Tier 3/4 readiness gate + controlled applications",
                "Reduce new learning and use simulated coding, design, behavioural and manager rounds to test readiness. Deliverables: 3–4 complete mocks across formats, scored weak-area list, resume/role positioning and a factual EM story bank. Apply selectively when readiness gates are met; the calendar alone does not establish readiness."}
        },
        {
            {"General Studies", "Prelims conversion: static revision + PYQs",
                "April 2027 · No new static-source pages planned. Cycle through Polity, History, Environment/Art & Culture, Geography, Economy and Science/S&T using active recall, PYQs and the error log. Revise current-affairs links and prioritise the weakest subjects."},
            {"CSE Revision & Tests", "Prelims test conversion: GS mocks, CSAT and MCQs",
                "April target from the saved plan: 6–8 GS mocks, 4–6 CSAT tests and approximately 700–900 GS MCQs/PYQs. Review every wrong answer and distinguish knowledge gaps, misreading, elimination errors and time-pressure errors; adjust targets to actual performance."},
            {"Maths Optional", "Paper II: Algebra + Real Analysis",
                "Develop the first-pass checklist for groups/rings and real analysis, including sequences, series, convergence, continuity and standard proof techniques. Continue problem practice and retain a realistic recurring Maths block alongside Prelims-specific GS."},
            {"Software Interview Prep", "Month 7: Architecture depth + domain-aware design",
                "Deepen distributed consistency, resilience, partitioning and multi-region trade-offs where role-relevant. Practise architecture decisions, ADRs, standards and technical-debt prioritisation; use actual target-role JDs to select domain-specific design drills."}
        },
        {
            {"General Studies", "Prelims final revision: rapid recall + weak areas",
                "May 1–22, 2027 · No new static reading. Use short revision loops, PYQs, current-affairs consolidation and the error log. Prioritise recall speed, accuracy and recovery from repeated mistakes over making new notes."},
            {"CSE Revision & Tests", "Final Prelims simulations · exam target 23 May 2027",
                "Saved-plan target for 1–22 May: 4–5 GS mocks and 2–3 CSAT mocks, with final rapid revision and exam simulations. Complete and review each mock; protect sleep and logistics in the final days. The source plan lists UPSC CSE Prelims for 23 May 2027."},
            {"Maths Optional", "Paper II: Complex Analysis + Linear Programming",
                "Cover analytic functions, Cauchy results, singularities/residues and standard contour-integration questions; build the LP checklist for simplex, duality and related methods. These topics are in the longer CSE optional plan, not in the Prelims GS PDF."},
            {"Software Interview Prep", "Month 8: Tier 1/2 design + leadership evidence",
                "Use target-company playbooks and harder system-design cases to test architecture depth, reliability, migration choices and domain understanding. Refine hiring, coaching, conflict, delivery-risk and stakeholder stories using real evidence; do not invent metrics."}
        },
        {
            {"General Studies", "Mains GS-I: history, culture, society and geography",
                "June 2027 · Resume the wider Mains-oriented syllabus after the Prelims phase. Map Indian culture, modern/post-independence history, world history, society and geography against the official syllabus. Build concise topic sheets and begin structured answer practice; distinguish this phase from the completed Prelims static pass."},
            {"Maths Optional", "Paper II: Partial Differential Equations",
                "Cover standard PDE methods, canonical forms and representative problem types. Build a topic checklist, solve selected previous-year questions, and revisit Calculus/ODE prerequisites where errors recur."},
            {"Software Interview Prep", "Month 9: final interview-readiness evidence",
                "Polish the resume and capstone/project deep-dives; complete 4–6 realistic mocks across coding, design and behavioural/manager rounds. Produce a scored gap list and a role-specific preparation plan. Judge readiness by repeatable performance, not by month completion alone."}
        },
        {
            {"General Studies", "Mains GS-II: Constitution, governance, social justice + IR",
                "July 2027 · Work through Constitution/polity, governance, accountability, welfare and social-justice themes, India’s neighbourhood and international relations. Practise concise, structured Mains answers and connect institutions, current affairs and case examples."},
            {"Maths Optional", "Paper II: Numerical Analysis",
                "Cover interpolation, numerical integration, numerical solutions of equations and ODE methods from the selected syllabus/source. Practise representative questions and track method selection, numerical accuracy and time."},
            {"Software Interview Prep", "Interview execution: applications + feedback-led repairs",
                "Extension beyond the saved nine-month study plan. Track target roles/applications, debrief real interviews and mocks, and use the highest-impact observed gap to choose weekly coding/design/leadership drills. Keep Java/JVM, DSA and architecture warm rather than restarting the curriculum."}
        },
        {
            {"General Studies", "Mains GS-III: economy, agriculture, S&T, environment + security",
                "August 2027 · Cover economic development, agriculture, science and technology, environment/biodiversity, disaster management and internal security. Use issue-based notes and practise answers that link concepts, evidence, government action and trade-offs."},
            {"Maths Optional", "Paper II: Computer Programming",
                "Cover number systems, Boolean logic, algorithms/flowcharts and the listed programming fundamentals. Practise explaining algorithm steps and solving syllabus-aligned questions; treat this as one part of Paper II, not a substitute for mathematical problem practice."},
            {"Software Interview Prep", "Interview conversion: targeted company loops",
                "Extension beyond the saved nine-month study plan. Prepare for actual openings using their role requirements; maintain a lightweight log of coding, design and leadership feedback. Spend time on recurring weaknesses, not on adding technologies without a target-role reason."}
        },
        {
            {"General Studies", "Mains GS-IV: ethics, probity + Essay practice",
                "September 2027 · Cover ethics, values, attitude, emotional intelligence, probity, public-service dilemmas and case studies. Practise essay outlines and full answers under time limits; keep separate examples and concepts easy to recall."},
            {"Maths Optional", "Paper I: timed sectional tests + PYQ repair",
                "Attempt timed Paper I sections covering Linear Algebra, Calculus, Analytic Geometry, ODE, Dynamics/Statics and Vector Analysis. Review solution structure, accuracy and time; re-solve missed questions without the original solution."},
            {"Software Interview Prep", "Target-switch readiness review",
                "Extension beyond the saved nine-month study plan. Evaluate real interview outcomes, role fit and compensation constraints; maintain company-specific preparation where active processes exist. Decide the next cycle from evidence rather than extending preparation indefinitely."}
        },
        {
            {"General Studies", "Mains GS-I/II consolidation + answer writing",
                "October 2027 · Revisit History, Culture, Geography, Society, Constitution, Governance, Social Justice and IR. Use PYQs to select high-frequency themes, practise timed answers and repair weak content/structure."},
            {"Maths Optional", "Paper II: Mechanics + Fluid Dynamics",
                "Cover Lagrangian/Hamiltonian mechanics, continuity and standard fluid-flow concepts from the selected syllabus. Record essential assumptions and derivation steps; practise representative problems and identify remaining coverage gaps."},
            {"Software Interview Prep", "Role-specific system-design and leadership refresh",
                "Extension beyond the saved nine-month study plan. Refresh design cases and leadership examples for live target roles; prioritise repeatable interview performance, role-specific gaps and lessons from feedback."}
        },
        {
            {"General Studies", "Mains GS-III/IV consolidation + Essay",
                "November 2027 · Revise economy, agriculture, S&T, environment, security, ethics and probity. Practise timed GS-III/IV answers, ethics case studies and essay outlines; keep an error log of factual, analytical and structural weaknesses."},
            {"Maths Optional", "Paper II sectional tests + PYQ gap closure",
                "Attempt timed sections across Algebra, Real/Complex Analysis, Linear Programming, PDE, Numerical Analysis, Computer Programming, Mechanics and Fluid Dynamics. Re-solve incorrect questions and target the three largest recurring weaknesses."},
            {"Software Interview Prep", "Focused gap closure from actual interview evidence",
                "Extension beyond the saved nine-month study plan. Review the interview log and repair the most frequent high-impact failures in coding, architecture or leadership communication. Avoid broad new study unless target roles consistently require it."}
        },
        {
            {"General Studies", "Full CSE syllabus audit + mixed tests",
                "December 2027 · Audit tracked GS topics and the broader Mains syllabus, complete mixed PYQ/test sets and revise the weakest areas. Schedule any remaining first-pass work explicitly for the January checkpoint rather than treating unchecked topics as complete."},
            {"Maths Optional", "Mixed Paper I/II PYQs + first-pass closure",
                "Attempt mixed timed questions from both papers, consolidate formula/result sheets and close high-priority syllabus gaps. Compare topic mastery, solved PYQs and the error log; do not equate reading a topic with exam readiness."},
            {"Software Interview Prep", "Career pipeline review + targeted preparation",
                "Extension beyond the saved nine-month study plan. Review job-search results, interviews and offer fit; refresh the target-company list and maintain only the preparation that supports real opportunities. Keep documented ownership and impact examples factual."}
        },
        {
            {"General Studies", "1 January checkpoint: verify CSE syllabus coverage",
                "By 1 January 2028, audit every tracked GS and Mains syllabus item against actual coverage. Name unresolved topics explicitly, review test/PYQ evidence and set the next revision schedule. This checkpoint measures coverage; it does not automatically mark topics complete."},
            {"Maths Optional", "1 January checkpoint: verify Paper I + II first pass",
                "Audit both Maths Optional papers against the syllabus and the IMS-note plan. List topics mastered, topics needing revision, PYQs attempted and unresolved problem types; schedule remaining work explicitly. Use performance evidence, not page counts alone."},
            {"Software Interview Prep", "Career roadmap reset from outcomes",
                "Extension beyond the saved nine-month study plan. Review target roles, actual interview performance, compensation/role fit and the portfolio. Choose the next cycle based on evidence—continued search, targeted upskilling or a revised timeline—without treating completion of the checklist as an offer guarantee."}
        }
    };

    static int monthCount() {
        return MONTHS.length;
    }

    static String monthKeyAt(int index) {
        if (index < 0 || index >= MONTHS.length) return null;
        int startYear = Integer.parseInt(START_MONTH.substring(0, 4));
        int startMonth = Integer.parseInt(START_MONTH.substring(5, 7));
        int zeroBased = startMonth - 1 + index;
        int year = startYear + zeroBased / 12;
        int month = zeroBased % 12 + 1;
        return String.format(java.util.Locale.US, "%04d-%02d", year, month);
    }

    static String[][] rowsForMonth(String monthKey) {
        int index = monthIndex(monthKey);
        if (index < 0 || index >= MONTHS.length) return new String[0][];
        String[][] copy = new String[MONTHS[index].length][];
        for (int i = 0; i < MONTHS[index].length; i++) {
            copy[i] = Arrays.copyOf(MONTHS[index][i], MONTHS[index][i].length);
        }
        return copy;
    }

    static String[] findGoal(String monthKey, String track) {
        for (String[] row : rowsForMonth(monthKey)) {
            if (row.length >= 3 && row[0].equals(track)) return row;
        }
        return null;
    }

    private static int monthIndex(String monthKey) {
        if (monthKey == null || monthKey.length() != 7 || monthKey.charAt(4) != '-') return -1;
        try {
            int year = Integer.parseInt(monthKey.substring(0, 4));
            int month = Integer.parseInt(monthKey.substring(5, 7));
            if (month < 1 || month > 12) return -1;
            int startYear = Integer.parseInt(START_MONTH.substring(0, 4));
            int startMonth = Integer.parseInt(START_MONTH.substring(5, 7));
            return (year - startYear) * 12 + month - startMonth;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
