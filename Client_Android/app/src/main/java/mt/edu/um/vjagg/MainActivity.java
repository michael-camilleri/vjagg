/**
 *   Copyright (C) 2019  Michael Camilleri
 *
 * 	This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 *	License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * 	version.
 *	This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 *	warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 	You should have received a copy of the GNU General Public License along with this program. If not, see
 *	http://www.gnu.org/licenses/.
 *
 *	Author: Michael Camilleri
 *
 */

package mt.edu.um.vjagg;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

/**
 * Created by Michael Camilleri
 *
 * [Converted to using primitives]
 */

public class MainActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback, DialogInterface.OnClickListener, TrackingService.StateListener
{
    public static final int REQ_SIGNUP      = 0;
    public static final int REQ_PERMIS      = 1;
    public static final int REQ_DOZE        = 2;

    public static final int REQUEST_EXIT = 1;

    private static final int AS_BUQ = -3; //!< Application State: Undefined
    private static final int AS_QID = -2; //!< Querying if IDLE
    private static final int AS_QLG = -1; //!< Querying if LOG
    private static final int AS_IDL =  0; //!< Application State: Idle
    private static final int AS_LOG =  1; //!< Application State: Logging

    private static final String[] LOG_STATUS = {"Undefined", "Undefined", "Undefined", "Idle", "Logging"};

    private static final String TAG = "MA";

    private int       mAppStatus;       //!< Application State
    private TextView  mLogMsgView;
    
    private View        mMainView;
    private View        mSplashVw;

    private SendLogFile mUploader;
    private Dialog      mSendLogDialog;
    private Dialog      mEnableLocDialog;
    private Dialog      mImproveLocDialog;
    private Dialog      mInformDoze;

    private Button      mStartLogBtn;
    private Button      mEndLogBtn;
    private Button      mViewJourBtn;
    private Button      mPingBtn;

    private TextView    mStateView;

    private SettingsActivity.VjaggSettings mSettings;

    /**
     * @param savedInstanceState Bundle
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setIcon(R.drawable.vjagg_icon);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        //Initially undefined status
        mAppStatus = AS_BUQ;
        mSettings  = null;

        //Debugging
        LogView.SetContext(getApplication());
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //Set Worker to null
        mUploader       = null;

        //Set Dialogs to null...
        mSendLogDialog      = null;
        mEnableLocDialog    = null;
        mImproveLocDialog   = null;
        mInformDoze         = null;

        //Set Views (global, text and buttons)
        mMainView = findViewById(R.id.layout_main);
        mSplashVw = findViewById(R.id.layout_splash);

        mStateView= ((TextView)findViewById(R.id.logging_status_text));

        mStartLogBtn = (Button) findViewById(R.id.start_log_btn);
        mEndLogBtn   = (Button) findViewById(R.id.end_log_btn);
        mViewJourBtn = (Button) findViewById(R.id.send_log_btn);
        mPingBtn     = (Button)findViewById(R.id.ping_button);

        LogView.Debug(TAG, "Create OK");

        //Check if version 6 or above...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) //Handle Versions with Doze
        {
            if (Utilities.DEBUG_MODE && Utilities.DEBUG_TYPE == 0)  //Then we do not want DOZE!
            {
                if (((PowerManager)getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
                {
                    mInformDoze = Utilities.GenerateQuery(this, "Enable Optimisation!", "The Application MUST NOT be whitelisted for IDLE Consumption Tests!", this, this);
                    mInformDoze.show();
                }
                else
                {
                    AttemptSignUp();
                }
            }
            else
            {
                if (!((PowerManager)getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()))
                {
                    mInformDoze = Utilities.GenerateQuery(this, "WhiteList us!", "This application requires the phone to stay awake in order to sample the accelerometer (the screen will still be able to turn-off). Do you wish to be directed to the SettingsActivity Menu to do this? Otherwise, the application will close", this, this);
                    mInformDoze.show();
                }
                else
                {
                    AttemptSignUp();
                }
            }

        }
        else
        {
            AttemptSignUp();
        }
    }

    public void AttemptSignUp()
    {
        Intent intent = new Intent(this, SignUpActivity.class);
        startActivityForResult(intent, REQ_SIGNUP);  //Start intent
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public void onResume()
    {
        LogView.Debug(TAG, "onResume");
        super.onResume();           // Always call the superclass method first

        //Register Listener
        TrackingService.RegisterListener(this);

        //Get Settings...
        mSettings = SettingsActivity.GetSettings(this);

        //Resolve Status
        if (TrackingService.IsAlive()) { mAppStatus = AS_LOG; }
        else                           { mAppStatus = AS_IDL; }
        UpdateGUI(true);
    }

    @Override
    public void onPause()
    {
        LogView.Debug(TAG, "onPause");
        super.onPause();            // Always call the superclass method first

        //Unregister Listener
        TrackingService.UnregisterListener();

        //Just in case, force a flush to store everything!
        LogView.ForceFlush();
    }

    @Override
    public void onDestroy()
    {
        LogView.Debug(TAG, "onDestroy");
        super.onDestroy();          // Always call the superclass method first
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        LogView.Debug(TAG, "onActRes");
        switch (requestCode)
        {
            case REQ_SIGNUP:
                if (resultCode == REQUEST_EXIT) { LogView.Debug(TAG, "SignUp x"); this.finish(); }
                break;

            case REQ_DOZE:
                if (Utilities.DEBUG_MODE && Utilities.DEBUG_TYPE == 0)
                {
                    if (((PowerManager)getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName())) { finish(); }
                    else                                                                                                          { AttemptSignUp(); }
                }
                else
                {
                    if (!((PowerManager)getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName())) { finish(); }
                    else                                                                                                           { AttemptSignUp(); }
                }

                break;

            default:
                //Do nothing
                break;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which)
    {
        LogView.Debug(TAG, "onClick");
        if (dialog == mSendLogDialog)
        {
            LogView.Debug(TAG, "SendLogDlg");
            if (which == DialogInterface.BUTTON_POSITIVE)
            {
                if (mUploader == null) //This should handle attempting to start service multiple times
                {
                    LogView.Debug(TAG, "Wrk OK");
                    UpdateGUI(false);
                    mUploader = new SendLogFile();
                    mUploader.execute(mLogMsgView.getText().toString());
                }
                else
                {
                    LogView.Warn(TAG, "Wrk Err");
                }
            }
            mSendLogDialog = null;
        }
        else if (dialog == mEnableLocDialog)
        {
            LogView.Debug(TAG, "EnableLocDlg");
            if (which == DialogInterface.BUTTON_POSITIVE)
            {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
            mEnableLocDialog = null;
        }
        else if (dialog == mImproveLocDialog)
        {
            LogView.Debug(TAG, "ImproveLocDlg");
            if (which == DialogInterface.BUTTON_POSITIVE)
            {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
            else
            {
                StartTracking();
            }
            mImproveLocDialog = null;
        }
        else if  (dialog == mInformDoze)
        {
            LogView.Debug(TAG, "InformDoze");
            if (which == Dialog.BUTTON_POSITIVE) { startActivityForResult(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), REQ_DOZE); }
            else                                 { finish(); }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        LogView.Debug(TAG, "onReqPermRes");
        if ((requestCode == REQ_PERMIS) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
        {
            CheckGPSAvailability();
        }
    }

    public void StartLogging(View view)
    {
        LogView.Debug(TAG, "StartLog");
        if (mAppStatus == AS_IDL) //Can only start logging if currently idle
        {
            if (ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED)
            {
                LogView.Debug(TAG, "Perm OK");
                CheckGPSAvailability();
            }
            else
            {
                LogView.Debug(TAG, "Perm Error");
                ActivityCompat.requestPermissions(this, new String[] {"android.permission.ACCESS_FINE_LOCATION"}, REQ_PERMIS);
            }
        }
        else { LogView.Debug(TAG, "State Error"); }
    }

    public void StopLogging(View view)
    {
        LogView.Debug(TAG, "EndLog");

        mAppStatus = AS_QID;            //Query if IDLE ok

        //Stop the Service
        stopService(new Intent(getApplicationContext(), TrackingService.class));

        //Resolve State after report (if still QID)
        UpdateGUI(mAppStatus > AS_QID);               //Show splash
    }

    @Override
    public void OnStateUpdate(boolean alive)
    {
        LogView.Debug(TAG, "state-update");
        switch (mAppStatus)
        {
            case AS_BUQ:
                mAppStatus = alive ? AS_LOG : AS_IDL;
                break;

            case AS_QID:
                if (alive)  { LogView.Error(TAG, "Track Term Error"); Toast.makeText(this, "Stopping Service Failed", Toast.LENGTH_LONG).show();  mAppStatus = AS_LOG; }
                else        { LogView.Info(TAG, "Tracking Stopped");  Toast.makeText(this, "Logging Service Stopped", Toast.LENGTH_SHORT).show(); mAppStatus = AS_IDL; }
                break;

            case AS_QLG:
                if (alive) { LogView.Info(TAG, "Tracking Started");     Toast.makeText(this, "Logging Active", Toast.LENGTH_SHORT).show();         mAppStatus = AS_LOG; }
                else       { LogView.Error(TAG, "Service Start Error"); Toast.makeText(this, "Starting Service Failed", Toast.LENGTH_LONG).show(); mAppStatus = AS_IDL; }
                break;

            default:
                Toast.makeText(this, alive ? "Auto-Started" : "Auto-Stopped", Toast.LENGTH_LONG).show();
                LogView.Debug(TAG, "Auto-State Change");
                mAppStatus = alive ? AS_LOG : AS_IDL;
                break;
        }

        UpdateGUI(true);
    }

    public void CheckGPSAvailability()
    {
        LogView.Debug(TAG, "CheckGPS");
        try
        {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) //Handle Version below kitkat
            {
                LogView.Debug(TAG, "<KitKat");
                if (((LocationManager)getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER))
                {
                    LogView.Debug(TAG, "GPS OK");
                    StartTracking();
                }
                else
                {
                    LogView.Debug(TAG, "GPS Needed");
                    mEnableLocDialog = Utilities.GenerateQuery(this, "Location Services Not Enabled.", "Location Services are not enabled. Do you wish to be redirected to the settings menu to enable them? Data logging cannot proceed without location services.", this, this);
                    mEnableLocDialog.show();
                }
            }
            else
            {
                LogView.Debug(TAG, ">Kitkat");
                int location_mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
                if (location_mode == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY) //If high accuracy, then just proceed
                {
                    LogView.Debug(TAG, "GPS OK");
                    StartTracking();
                }
                else if (location_mode > Settings.Secure.LOCATION_MODE_OFF)
                {
                    LogView.Debug(TAG, "GPS LOW");
                    mImproveLocDialog = Utilities.GenerateQuery(this, "Location Services in Low Accuracy.", "Location Services are enabled but in low accuracy mode. Data collection may be affected. Do you wish to be redirected to the settings menu to enable high accuracy?", this, this);
                    mImproveLocDialog.show();
                }
                else
                {
                    LogView.Debug(TAG, "GPS Needed");
                    mEnableLocDialog = Utilities.GenerateQuery(this, "Location Services Not Enabled.", "Location Services are not enabled. Do you wish to be redirected to the settings menu to enable them? Data logging cannot proceed without location services.", this, this);
                    mEnableLocDialog.show();
                }
            }
        }
        catch (Exception e)
        {
            Utilities.DisplayException(this, e, null, null);
        }
    }

    private void StartTracking()
    {
        mAppStatus = AS_QLG;        //First Set State to avoid Race Conditions. This way if onStateUpdate is called, then it will already be QLG...

        //Attempt to start... (without wakeful service)
        if (startService(new Intent(this, TrackingService.class)) == null) { LogView.Error(TAG, "serv inexistent"); Toast.makeText(this, "Starting Service Failed", Toast.LENGTH_LONG).show(); mAppStatus = AS_IDL; }

        //Update Display, showing splash if still querying...
        UpdateGUI(mAppStatus > AS_QLG);
    }

    public void ViewJourneys(View view)
    {
        if ((new File(getFilesDir(), TrackingService.GPS_RPP_FILE)).exists())
        {
            startActivity((new Intent(this, JourneyListActivity.class)).putExtra(JourneyListActivity.LIST_TYPE, false));//Start the Data Sending Activity but not in personal mode
        }
        else
        {
            Toast.makeText(this, "No Valid Trips Found", Toast.LENGTH_LONG).show();
        }
    }

    protected void UpdateGUI(boolean main_view)
    {
        LogView.Debug(TAG, "UpdateGUI");
        if (main_view)
        {
            //Enable Main View
            mMainView.setVisibility(View.VISIBLE);
            mSplashVw.setVisibility(View.GONE);

            //Update Buttons
            mStartLogBtn.setEnabled(mAppStatus == AS_IDL);
            mEndLogBtn.setEnabled(mAppStatus == AS_LOG);
            mViewJourBtn.setEnabled(mAppStatus == AS_IDL);

            //Selectively enable the Ping Button
            if (mSettings.mStoreEverything)
            {
                mPingBtn.setVisibility(View.VISIBLE);
                mPingBtn.setEnabled(mAppStatus == AS_LOG);
            }
            else
            {
                mPingBtn.setVisibility(View.GONE);
            }

            mStateView.setText(LOG_STATUS[mAppStatus - AS_BUQ]);
        }
        else
        {
            mMainView.setVisibility(View.GONE);
            mSplashVw.setVisibility(View.VISIBLE);
        }
    }

    public void onPing(View v)
    {
        TrackingService.Write("P " + Long.toString(System.currentTimeMillis()));
        Toast.makeText(this, "Journey Event", Toast.LENGTH_SHORT).show();
    }

    public void onPersonal(MenuItem item)
    {
        if ((new File(getFilesDir(), TrackingService.GPS_PER_FILE)).exists())
        {
            startActivity((new Intent(this, JourneyListActivity.class)).putExtra(JourneyListActivity.LIST_TYPE, true));//Start the Data Sending Activity
        }
        else
        {
            Toast.makeText(this, "There are no Trips in your Personal History", Toast.LENGTH_LONG).show();
        }
    }

    public void onSendLogFile(MenuItem item)
    {
        LogView.Debug(TAG, "onSendLgFl");
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);

        dialog.setTitle("Enter a message indicating your query.");
        dialog.setView(getLayoutInflater().inflate(R.layout.dialog_send_log, null));
        dialog.setPositiveButton("Send", this);
        dialog.setNegativeButton("Cancel", this);
        mSendLogDialog = dialog.create();
        mSendLogDialog.show();
        mLogMsgView = (TextView)mSendLogDialog.findViewById(R.id.log_message);
    }

    public void onSettings(MenuItem item)
    {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    public void onDeleteLogFile(MenuItem item)
    {
        LogView.DeleteLogs();
    }

    public void onHelp(MenuItem item) { LogView.Debug(TAG, "onHelp"); startActivity(new Intent(this, HelpActivity.class));}

    protected void afterSendLogFile(Exception result)
    {
        LogView.Debug(TAG, "aft SndLgF");
        UpdateGUI(true);
        if (result != null) { Utilities.DisplayException(this, result, null, null); }
        else                { Utilities.GenerateMessage(this, "Success", "Log data was uploaded to our server.", null).show(); }
    }

    /**
     * AsyncTask for sending the Data to the Server...
     */
    private class SendLogFile extends AsyncTask<String, Void, Exception>
    {
        @Override
        protected void onPreExecute()
        {
            //Empty
        }

        @Override
        protected Exception doInBackground(String... message)
        {
            LogView.Debug(TAG, "SendLogFile");
            Exception       result; //For control
            SecureConnector connector = new SecureConnector(getApplicationContext());

            //Extract all available data
            ArrayList<String> log_data = LogView.RetrieveLoggedData();
            if (log_data == null) { connector.Disconnect(); return new NullPointerException("No Data Logged"); }

            //Attempt to Connect
            result = connector.Connect();
            if (result != null) { LogView.PrependUnsentLogs(log_data); return result; }

            //Send all available data, block by block
            result = connector.SendLogData(message[0], log_data);
            if (result != null)   { connector.Disconnect(); LogView.PrependUnsentLogs(log_data); return result; }

            //Disconnect
            return connector.Disconnect();
        }

        @Override
        protected void onPostExecute(final Exception result)
        {
            mUploader = null; //Nullify Worker
            afterSendLogFile(result);
        }
    }

}
