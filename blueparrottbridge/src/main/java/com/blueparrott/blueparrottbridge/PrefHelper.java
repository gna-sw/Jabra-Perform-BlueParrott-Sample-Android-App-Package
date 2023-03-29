package com.blueparrott.blueparrottbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


public class PrefHelper {

    public static String getStringPref(Context context, String prefKey, String defaultValue){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(prefKey,defaultValue);
    };

    public static Boolean getBooleanPref(Context context, String prefKey, Boolean defaultValue){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(prefKey, defaultValue);
    };

    private static void setBooleanPref(Context context, String prefKey,  Boolean newValue){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor=prefs.edit();
        editor.putBoolean(prefKey,newValue).commit();
    };

    public static void setStringPref(Context context, String prefKey,  String newValue){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor=prefs.edit();
        editor.putString(prefKey, newValue).commit();

    };

}

