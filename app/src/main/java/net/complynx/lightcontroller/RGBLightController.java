package net.complynx.lightcontroller;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.widget.ImageView;
import android.widget.RemoteViews;

/**
 * Implementation of App Widget functionality.
 */
public class RGBLightController extends AppWidgetProvider {
    static SharedPreferences state;

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.rgblight_controller);
        views.setImageViewResource(R.id.img_view, R.drawable.lighter_widget);
//        views.setInt(R.id.img_view, "setAlpha", 127);
        views.setImageViewResource(R.id.img_view_main_light, R.drawable.lighter_widget_main_light);
        int main_state = state.getInt("main", 0);
        views.setInt(R.id.img_view_main_light, "setAlpha", main_state > 0 ? 255 : 0);
//        views.setInt(R.id.img_view_main_light, "setColorFilter", Color.rgb(255,255,255));
        views.setImageViewResource(R.id.img_view_rim_light, R.drawable.lighter_widget_rim_light);
        int seconds = state.getInt("seconds", 0);
        int position = state.getInt("position", 1000000);
        int color;
        if(seconds > 2 && position < 800000){ // transition longer than 2 sec and pos < 80%
            color = state.getInt("color", 0);
        }else{
            color = state.getInt("target", 0);
        }
        color |= 0xff000000;// add opacity
        float[] hsv=new float[3];
        Color.colorToHSV(color, hsv);
        int val = (int)(hsv[2]*255);
        hsv[2] = 1;
        color = Color.HSVToColor(hsv);

        views.setInt(R.id.img_view_rim_light, "setColorFilter", color);
        views.setInt(R.id.img_view_rim_light, "setAlpha", val);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        state = context.getSharedPreferences("state", Context.MODE_PRIVATE);
        long lu = state.getLong("last update", 0);
        long now = System.currentTimeMillis();
        if(now - lu > state.getLong("Update invalidation time", 5*60*1000)) {
            Intent intent = new Intent(context, Requester.class);
            intent.putExtra("T", Requester.UPDATE_STATE);
            context.startService(intent);
        }

        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

