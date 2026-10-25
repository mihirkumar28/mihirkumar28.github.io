package com.mihirkumar.dayflow;

import android.app.Activity;
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
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Application must expose a launcher activity", launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(launch);
        try {
            View content = activity.findViewById(android.R.id.content);
            assertNotNull("Activity must attach its content root", content);
            assertTrue("Launcher must render a screen rather than an empty container",
                    content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    @Test public void widgetRemoteViewsInflateAndExposeItsPrimaryControls() throws Exception {
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
