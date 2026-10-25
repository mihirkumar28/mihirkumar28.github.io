package com.mihirkumar.dayflow;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Lightweight device-level coverage for the launch contract and launcher widget layout. */
@RunWith(AndroidJUnit4.class)
public class DayFlowP0SmokeTest {
    @Test public void launcherIntentResolvesToMainActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("Application must expose a launcher activity", launch);
        assertNotNull("Launcher intent must have an explicit component", launch.getComponent());
        assertEquals("Launcher intent must resolve to MainActivity",
                MainActivity.class.getName(), launch.getComponent().getClassName());
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
