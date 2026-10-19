package com.glyphcountdown.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Shared behaviour for the three widget sizes; each size is its own provider. */
public class WidgetBase extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        WidgetUpdater.updateAll(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        WidgetUpdater.updateAll(c);
    }
}
