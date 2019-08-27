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

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TimePicker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class SettingsActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener, TimePickerDialog.OnTimeSetListener, ActivityCompat.OnRequestPermissionsResultCallback
{
    static final String SETTINGS_FILE = "VJAGG_SETTINGS.dat";
    private static final int    REQ_RW = 100;

    private static final String TAG = "S";

    static class DailyTime
    {
        public DailyTime()
        {
            mHrs = mMin = 0;   //!< This would be equivalent to midnight
        }

        public DailyTime(int hours, int mins)
        {
            mHrs = hours;
            mMin = mins;
        }

        int mHrs;
        int mMin;
    }

    /**
     * \brief Package Private SettingsActivity Class
     */
    static class VjaggSettings
    {
        boolean mStoreEverything;   //!< Store Everything Flag

        boolean     mAutoLog;           //!< Automatically start and stop logging at specified times.
        DailyTime   mStartTime;         //!< Time to start logging
        DailyTime   mStopTime;          //!< Time to stop logging

        boolean     mAutoSend;          //!< Automatically send all data
        boolean     mWifiOnly;          //!< Upload only via Wi-Fi
        boolean     mKeepPers;          //!< Keep copy in personal history

        VjaggSettings()
        {
            mStoreEverything = false;

            mAutoLog    = false;
            mStartTime  = new DailyTime(7, 0);  //!< 07:00 AM
            mStopTime   = new DailyTime(22,0);  //!< 10:00 PM

            mAutoSend = false;
            mWifiOnly = true;
            mKeepPers = true;
        }
    }

    /**
     * \brief Static Function to read the storage settings from file
     * @param application
     * @return
     */
    static VjaggSettings GetSettings(Context application)
    {
        LogView.Debug(TAG, "get-settings");
        try
        {
            //Create new settings
            VjaggSettings settings = new VjaggSettings();

            //Open File
            File file = new File(application.getFilesDir(), SETTINGS_FILE);

            //If it exists, then read...
            if (file.exists())
            {
                //Open Data Stream
                DataInputStream dis = new DataInputStream(new FileInputStream(file));

                //Read in the values
                settings.mStoreEverything = dis.readBoolean();
                settings.mAutoLog = dis.readBoolean();
                settings.mStartTime.mHrs = dis.readInt();
                settings.mStartTime.mMin = dis.readInt();
                settings.mStopTime.mHrs = dis.readInt();
                settings.mStopTime.mMin = dis.readInt();
                settings.mAutoSend = dis.readBoolean();
                settings.mWifiOnly = dis.readBoolean();
                settings.mKeepPers = dis.readBoolean();

                //Close file
                dis.close();
            }
            else
            {
                //Open Data Stream
                DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));

                //Write the Values
                dos.writeBoolean(settings.mStoreEverything);
                dos.writeBoolean(settings.mAutoLog);
                dos.writeInt(settings.mStartTime.mHrs);
                dos.writeInt(settings.mStartTime.mMin);
                dos.writeInt(settings.mStopTime.mHrs);
                dos.writeInt(settings.mStopTime.mMin);
                dos.writeBoolean(settings.mAutoSend);
                dos.writeBoolean(settings.mWifiOnly);
                dos.writeBoolean(settings.mKeepPers);

                //Close file
                dos.close();
            }

            //Return if ok
            return settings;
        }
        catch (Exception e)
        {
            LogView.Error(TAG, e.toString());
            return new VjaggSettings(); //Return default value
        }
    }

    static boolean SetSettings(Context application, VjaggSettings settings)
    {
        LogView.Debug(TAG, "set-settings");
        try
        {
            //Open Data Stream
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(new File(application.getFilesDir(), SETTINGS_FILE)));

            //Write the Values
            dos.writeBoolean(settings.mStoreEverything);
            dos.writeBoolean(settings.mAutoLog);
            dos.writeInt(settings.mStartTime.mHrs);
            dos.writeInt(settings.mStartTime.mMin);
            dos.writeInt(settings.mStopTime.mHrs);
            dos.writeInt(settings.mStopTime.mMin);
            dos.writeBoolean(settings.mAutoSend);
            dos.writeBoolean(settings.mWifiOnly);
            dos.writeBoolean(settings.mKeepPers);

            //Close file
            dos.close();
            return true;
        }
        catch (Exception e)
        {
            LogView.Error(TAG, e.toString());
            return false;
        }
    }

    private VjaggSettings mSettings;

    CheckBox mAutoLog;
    EditText mStartTime;
    EditText mStopTime;
    CheckBox mAutoSend;
    CheckBox mWifiOnly;
    CheckBox mKeepBup;
    CheckBox mStoreAll;

    View     mTimePicker;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        LogView.Debug(TAG, "create");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setIcon(R.drawable.vjagg_icon);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Enforce Portrait
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //Load Views
        mAutoLog    = (CheckBox) findViewById(R.id.Auto_WU_TO_Check);
        mStartTime  = (EditText) findViewById(R.id.WakeUpTime);
        mStopTime   = (EditText) findViewById(R.id.TurnOffTime);
        mAutoSend   = (CheckBox) findViewById(R.id.Auto_Upload_Check);
        mWifiOnly   = (CheckBox) findViewById(R.id.Upload_WiFi_Check);
        mKeepBup    = (CheckBox) findViewById(R.id.Keep_Backup_Check);
        mStoreAll   = (CheckBox) findViewById(R.id.offload_all_check);

        //Load the Settings...
        mSettings = GetSettings(getApplicationContext());

        //Tie tags to them...
        mStartTime.setTag(mSettings.mStartTime);
        mStopTime.setTag(mSettings.mStopTime);

        //Now Selectively enable parts of the views,,,
        UpdateView();

        //Set Listeners...
        mAutoLog.setOnCheckedChangeListener(this);
        mAutoSend.setOnCheckedChangeListener(this);
        mWifiOnly.setOnCheckedChangeListener(this);
        mKeepBup.setOnCheckedChangeListener(this);
        mStoreAll.setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        LogView.Debug(TAG, "change");
        if (buttonView == mAutoLog)
        {
            mSettings.mAutoLog = buttonView.isChecked();
            UpdateView();
        }
        else if (buttonView == mAutoSend)
        {
            mSettings.mAutoSend = buttonView.isChecked();
            UpdateView();
        }
        else if (buttonView == mWifiOnly)
        {
            mSettings.mWifiOnly = buttonView.isChecked();
        }
        else if (buttonView == mKeepBup)
        {
            mSettings.mKeepPers = buttonView.isChecked();
        }
        else if (buttonView == mStoreAll)
        {
            if (buttonView.isChecked())
            {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_RW);
                    mSettings.mStoreEverything = false; //for now...
                }
                else
                {
                    mSettings.mStoreEverything = true;
                }
            }
            else
            {
                mSettings.mStoreEverything = false;
            }

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        LogView.Debug(TAG, "perm-result");
        if ((requestCode == REQ_RW) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
        {
            mSettings.mStoreEverything = true;
        }
    }

    private void UpdateView()
    {
        LogView.Debug(TAG, "update");
        mAutoLog.setChecked(mSettings.mAutoLog);
        mStartTime.setEnabled(mSettings.mAutoLog); mStartTime.setText(Utilities.FormatDayTime(mSettings.mStartTime));
        mStopTime.setEnabled(mSettings.mAutoLog);  mStopTime.setText(Utilities.FormatDayTime(mSettings.mStopTime));

        mAutoSend.setEnabled(mSettings.mAutoLog);  mAutoSend.setChecked(mSettings.mAutoSend);
        mWifiOnly.setEnabled(mSettings.mAutoLog && mSettings.mAutoSend); mWifiOnly.setChecked(mSettings.mWifiOnly);
        mKeepBup.setEnabled(mSettings.mAutoLog && mSettings.mAutoSend);  mKeepBup.setChecked(mSettings.mKeepPers);

        mStoreAll.setChecked(mSettings.mStoreEverything);
    }

    public void onConfirm(View view)
    {
        LogView.Debug(TAG, "confirm");
        //First store settings
        SetSettings(getApplicationContext(), mSettings);

        //Set the Alarms appropriately
        AutoStarter.SetAlarms(getApplicationContext());

        //Stop Activity
        finish();
    }

    public void onCancel(View view)
    {
        finish();
    }

    public void onTimeSelect(View view)
    {
        LogView.Debug(TAG, "sel-time");
        mTimePicker = view;
        (new TimePickerDialog(this, this, ((DailyTime)view.getTag()).mHrs, ((DailyTime)view.getTag()).mMin, true)).show();
    }

    public void onTimeSet(TimePicker view, int hourOfDay, int minute)
    {
        LogView.Debug(TAG, "set-time");
        //Set the Text in the respective view
        ((EditText) mTimePicker).setText(String.format("%02d:%02d", hourOfDay, minute));

        //Update the Respective time
        ((DailyTime)mTimePicker.getTag()).mHrs = hourOfDay;
        ((DailyTime)mTimePicker.getTag()).mMin = minute;

        //Remove this reference...
        mTimePicker = null;
    }
}

