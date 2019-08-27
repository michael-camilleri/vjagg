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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static android.app.AlarmManager.INTERVAL_FIFTEEN_MINUTES;
import static android.content.Context.BATTERY_SERVICE;

public class BatteryLogger extends BroadcastReceiver
{
    private static BufferedWriter mBatteryFile = null;  //!< Storage File: Greedy (Early) Initialisation
    private static final String   BATTERY_FILE = "VJAGG_BAT_";
    private static final long     SAMPLE_RATE  = 60000L;    //!< Every 1 minute under normal circumstances: otherwise INTERVAL_FIFTEEN_MINUTES;

    private static final String TAG = "BL";

    private synchronized static void Open()
    {
        if (mBatteryFile == null)
        {
            try
            {
                if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))  //Ensure that mounted
                {
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vjagg").mkdirs();
                    mBatteryFile = new BufferedWriter(new FileWriter(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vjagg/" + BATTERY_FILE + Long.toString(System.currentTimeMillis() / 100000) + ".txt"), true));
                    LogView.Debug(TAG, "open-ok");
                }
                else
                {
                    LogView.Error(TAG, "media-error");
                }
            }
            catch (IOException e) { LogView.Error(TAG, e.toString()); }
        }
    }

    public static synchronized void pingBatteryLevel(Context context)
    {
        //Open if null
        if (mBatteryFile == null) { return; }

        //Now write
        StringBuilder     sb = new StringBuilder().append(System.currentTimeMillis());

        //# == T1
//        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//        sb.append(" ").append(batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
//        sb.append(" ").append(batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
//        sb.append(" 0");
//        sb.append(" 0");

        //# == T2/S1
//        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//        BatteryManager bm = (BatteryManager)context.getSystemService(BATTERY_SERVICE);
//        sb.append(" ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
//        sb.append(" ").append(batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
//        sb.append(" ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
//        sb.append(" 0");

        //S2
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        BatteryManager bm = (BatteryManager)context.getSystemService(BATTERY_SERVICE);
        sb.append(" ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        sb.append(" ").append(batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
        sb.append(" ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        sb.append(" ").append(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE));

        try
        {
            mBatteryFile.write(sb.append("\n").toString());
        }
        catch (Exception e) { LogView.Warn(TAG, "write-error"); }
    }

    private synchronized static void Close()
    {
        if (mBatteryFile != null)
        {
            LogView.Debug(TAG, "store-close");
            try { mBatteryFile.close(); mBatteryFile = null; } catch (IOException e) { LogView.Error(TAG, "IO-Except " + e.toString()); }
        }
    }

    public BatteryLogger()
    {

    }

    public static void StartLogging(Context context)
    {
        Open();
        SetAlarm(context, SAMPLE_RATE);
    }

    public static void StopLogging(Context context)
    {
        //First cancel any pending alarms
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(PendingIntent.getBroadcast(context, 0, new Intent(context, BatteryLogger.class), 0));

        Close();
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        //Set the Alarm for the next wakeup
        SetAlarm(context, SAMPLE_RATE);

        //Write Battery Level
        pingBatteryLevel(context);
    }

    private static void SetAlarm(Context application, long millis)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) //Handle Version below kitkat
        {
            ((AlarmManager) application.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + millis, PendingIntent.getBroadcast(application, 0, (new Intent(application, BatteryLogger.class)), 0));
        }
        else
        {
            ((AlarmManager) application.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + millis, PendingIntent.getBroadcast(application, 0, (new Intent(application, BatteryLogger.class)), 0));
        }
    }

}
