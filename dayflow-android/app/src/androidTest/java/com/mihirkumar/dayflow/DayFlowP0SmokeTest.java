package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Lightweight device-level coverage for the app entry point and launcher widget layout. */
@RunWith(AndroidJUnit4.class)
public class DayFlowP0SmokeTest {
    @Test public void launcherActivityStartsAndRendersContent() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Application must expose a launcher activity", launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Do not call startActivitySync(): it waits for the entire main queue to become idle,
        // which can time out on MainActivity even after Android has resumed the screen.
        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(MainActivity.class.getName(), null, false);
        try {
            context.startActivity(launch);
            Activity activity = instrumentation.waitForMonitorWithTimeout(monitor, 45000);
            assertNotNull("Launcher activity did not reach its lifecycle monitor", activity);
            try {
                final View[] content = new View[1];
                instrumentation.runOnMainSync(() -> content[0] = activity.findViewById(android.R.id.content));
                assertNotNull("Activity must attach its content root", content[0]);
                assertTrue("Launcher must render a screen rather than an empty container",
                        content[0] instanceof ViewGroup && ((ViewGroup) content[0]).getChildCount() > 0);
            } finally {
                instrumentation.runOnMainSync(activity::finish);
            }
        } finally {
            instrumentation.removeMonitor(monitor);
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
