package com.mihirkumar.dayflow;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Lightweight device-level coverage for the app entry point and launcher widget layout. */
@RunWith(AndroidJUnit4.class)
public class DayFlowP0SmokeTest {
    @Test public void launcherActivityStartsAndRendersContent() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Application must expose a launcher activity", launch);
        assertEquals("Launcher intent must resolve to MainActivity",
                MainActivity.class.getName(), launch.getComponent().getClassName());

        // ActivityScenario waits on lifecycle callbacks instead of requiring the whole main
        // message queue to become idle. MainActivity owns periodic/background work, so an
        // ActivityMonitor plus startActivitySync can time out even when the launch is valid.
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View content = activity.findViewById(android.R.id.content);
                assertNotNull("Activity must attach its content root", content);
                assertTrue("Launcher must render a screen rather than an empty container",
                        content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0);
            });
        }
    }

    @Test public void widgetRemoteViewsInflateAndExposeItsPrimaryControls() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_dayflow);
        View rendered = remoteViews.apply(context, new FrameLayout(context));

        assertNotNull("Widget root must inflate", rendered.findViewById(R.id.widget_container));
        assertNotNull("Routine/status label must inflate", rendered.findViewById(R.id.widget_status));
        assertNotNull("Current task must inflate", rendered.findViewById(R.id.widget_task));
        assertNotNull("Task action must inflate", rendered.findViewById(R.id.widget_action));
        assertNotNull("Commute action must inflate", rendered.findViewById(R.id.widget_commute));
    }
}
