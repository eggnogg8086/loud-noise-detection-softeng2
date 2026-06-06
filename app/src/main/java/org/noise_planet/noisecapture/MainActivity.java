/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */

package org.noise_planet.noisecapture;


import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends AppCompatActivity {
    AtomicBoolean doRequestPermission = new AtomicBoolean(false);
    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }
    // Color for noise exposition representation
    public int[] NE_COLORS;
    public static final String RESULTS_RECORD_ID = "RESULTS_RECORD_ID";
    protected static final Logger MAINLOGGER = LoggerFactory.getLogger(MainActivity.class);
    private static final int NOTIFICATION_MAP = 101;

    // For the list view
    public ListView mDrawerList;
    public DrawerLayout mDrawerLayout;
    public String[] mMenuLeft;
    public ActionBarDrawerToggle mDrawerToggle;
    private ProgressDialog progress;

    public static final int PERMISSION_RECORD_AUDIO = 1;
    public static final int PERMISSION_WIFI_STATE = 2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        NE_COLORS = new int[]{res.getColor(R.color.R1_SL_level),
                res.getColor(R.color.R2_SL_level),
                res.getColor(R.color.R3_SL_level),
                res.getColor(R.color.R4_SL_level),
                res.getColor(R.color.R5_SL_level)};
    }


    /**
     * If necessary request user to acquire permissions for critical resources (gps and microphone)
     * @return True if service can be bind immediately. Otherwise, the bind should be done using the
     * @see #onRequestPermissionsResult
     */
    protected boolean checkAndAskPermissions() {
        if(doRequestPermission.get()) {
            return false;
        }
        try {
            doRequestPermission.set(true);
            List<String> permissionsToRequest = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE);
                }
            }

            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_RECORD_AUDIO);
                return false;
            }
            return true;
        } finally {
            doRequestPermission.set(false);
        }
    }


    @Override
    protected void onPause() {
        if(progress != null && progress.isShowing()) {
            try {
                progress.dismiss();
            } catch (IllegalArgumentException ex) {
                //Ignore
            }
        }
        super.onPause();
    }

    /**
     * @return Version information on this application
     * @throws PackageManager.NameNotFoundException
     */
    public static String getVersionString(Activity activity) throws PackageManager.NameNotFoundException {
        String versionName = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        Date buildDate = new Date(BuildConfig.TIMESTAMP);
        String gitHash = BuildConfig.GITHASH;
        if(gitHash == null || gitHash.isEmpty()) {
            gitHash = "";
        } else {
            gitHash = gitHash.substring(0, 7);
        }
        return activity.getString(R.string.title_appversion, versionName,
                DateFormat.getDateInstance().format(buildDate), gitHash);
    }


    void initDrawer(Integer recordId) {
        try {
            // List view
            mMenuLeft = getResources().getStringArray(R.array.dm_list_array);
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            mDrawerList = (ListView) findViewById(R.id.left_drawer);
            // Set the adapter for the list view
            mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                    R.layout.drawer_list_item, mMenuLeft));
            // Set the list's click listener
            mDrawerList.setOnItemClickListener(new DrawerItemClickListener(recordId));
            // Display the List view into the action bar
            mDrawerToggle = new ActionBarDrawerToggle(
                    this,                  /* host Activity */
                    mDrawerLayout,         /* DrawerLayout object */
                    R.string.drawer_open,  /* "open drawer" description */
                    R.string.drawer_close  /* "close drawer" description */
            ) {
                /**
                 * Called when a drawer has settled in a completely closed state.
                 */
                public void onDrawerClosed(View view) {
                    super.onDrawerClosed(view);
                    getSupportActionBar().setTitle(getTitle());
                }

                /**
                 * Called when a drawer has settled in a completely open state.
                 */
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);
                    getSupportActionBar().setTitle(getString(R.string.title_menu));
                }
            };
            // Set the drawer toggle as the DrawerListener
            mDrawerLayout.setDrawerListener(mDrawerToggle);
            if(getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setHomeButtonEnabled(true);
            }
        } catch (Exception e) {
            MAINLOGGER.error(e.getLocalizedMessage(), e);
        }
    }
    // Drawer navigation
    void initDrawer() {
        initDrawer(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if(!(this instanceof MeasurementActivity)) {
            if(mDrawerLayout != null && mDrawerList != null) {
                mDrawerLayout.closeDrawer(mDrawerList);
            }
            super.onBackPressed();
        } else {
            finish();
            // Show home
            Intent im = new Intent(Intent.ACTION_MAIN);
            im.addCategory(Intent.CATEGORY_HOME);
            im.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(im);
        }
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {

        Integer recordId;

        public DrawerItemClickListener(Integer recordId) {
            this.recordId = recordId;
        }

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            switch(position) {
                case 0:
                    // Measurement
                    Intent im = new Intent(getApplicationContext(),MeasurementActivity.class);
                    im.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(im);
                    finish();
                    break;
                case 1:
                    // Comment
                    Intent ir = new Intent(getApplicationContext(), Results.class);
                    if(recordId != null && recordId >= 0) {
                        ir.putExtra(RESULTS_RECORD_ID, recordId);
                    }
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ir);
                    finish();
                    break;

                case 2:
                    Intent ics = new Intent(getApplicationContext(), CalibrationMenu.class);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ics);
                    finish();
                    break;
                case 3:
                    Intent ih = new Intent(getApplicationContext(),ViewHtmlPage.class);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    ih.putExtra("pagetosee",
                            getString(R.string.url_help));
                    ih.putExtra("titletosee",
                            getString(R.string.title_activity_help));
                    startActivity(ih);
                    finish();
                    break;
                case 4:
                    Intent ia = new Intent(getApplicationContext(),ViewHtmlPage.class);
                    ia.putExtra("pagetosee",
                            getString(R.string.url_about));
                    ia.putExtra("titletosee",
                            getString(R.string.title_activity_about));
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ia);
                    finish();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        CharSequence mTitle = title;
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setTitle(mTitle);
        }
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if(mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if(mDrawerLayout != null) {
            mDrawerLayout.closeDrawers();
        }

        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent is = new Intent(getApplicationContext(),SettingsActivity.class);
                startActivity(is);
            return true;
            /*
            case R.id.action_about:
                Intent ia = new Intent(getApplicationContext(),ViewHtmlPage.class);
                pagetosee=getString(R.string.url_about);
                titletosee=getString((R.string.title_activity_about));
                startActivity(ia);
                return true;
                */
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    /***
     * Checks that application runs first time and write flags at SharedPreferences
     * Need further codes for enhancing conditions
     * @return true if 1st time
     * see : http://stackoverflow.com/questions/9806791/showing-a-message-dialog-only-once-when-application-is-launched-for-the-first
     * see also for checking version (later) : http://stackoverflow.com/questions/7562786/android-first-run-popup-dialog
     * Can be used for checking new version
     */

    // Choose color category in function of sound level
    public static int getNEcatColors(double SL) {

        int NbNEcat;

        if (SL > 75.) {
            NbNEcat = 0;
        } else if (SL > 65) {
            NbNEcat = 1;
        } else if (SL > 55) {
            NbNEcat = 2;
        } else if (SL > 45) {
            NbNEcat = 3;
        } else {
            NbNEcat = 4;
        }
        return NbNEcat;
    }


    public static double getDouble(SharedPreferences sharedPref, String key, double defaultValue) {
        try {
            return Double.valueOf(sharedPref.getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static int getInteger(SharedPreferences sharedPref, String key, int defaultValue) {
        try {
            return Integer.valueOf(sharedPref.getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
