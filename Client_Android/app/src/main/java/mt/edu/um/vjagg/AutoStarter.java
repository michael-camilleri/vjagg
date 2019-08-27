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
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Calendar;

public class AutoStarter extends WakefulBroadcastReceiver
{
    private static final long WINDOW_SIZE_MS = 30000;   //!< Half a Minute of leeway

    private static final String REQUEST_CODE = "vjagg.request_code";    //!< Request Code Extra

    //===================== Request Codes =====================//
    private static final int ALARM_START_LOG = 100;
    private static final int ALARM_STOP_LOG  = 101;

    private static final int ALARM_SEND_LOG  = 110;

    private static final String TAG = "AS";

    /**
     * \brief Set the Alarms
     * \detail This is envisioned to be used when the settings change....
     * @param application   The Application Context
     * @return              True if successful, false otherwise
     */
    public static boolean SetAlarms(Context application)
    {
        //Read in all settings and get Alarm Manager
        SettingsActivity.VjaggSettings settings = SettingsActivity.GetSettings(application);
        AlarmManager manager = (AlarmManager) application.getSystemService(Context.ALARM_SERVICE);

        //Clear any alarms
        manager.cancel(PendingIntent.getBroadcast(application, ALARM_START_LOG, new Intent(application, AutoStarter.class), 0));
        manager.cancel(PendingIntent.getBroadcast(application, ALARM_STOP_LOG,  new Intent(application, AutoStarter.class), 0));
        manager.cancel(PendingIntent.getBroadcast(application, ALARM_SEND_LOG,  new Intent(application, AutoStarter.class), 0));

        //Now create new alarms
        if (settings.mAutoLog)
        {
            LogView.Debug(TAG, "set-auto-log");
            Calendar calendar = Calendar.getInstance();
            int current = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

            //Start with the Starting Time...
            if ((settings.mStartTime.mHrs * 60 + settings.mStartTime.mMin) < current) { LogView.Debug(TAG, "add-day"); calendar.add(Calendar.DATE, 1); } //Then alarm needs to be for the following day
            calendar.set(Calendar.HOUR_OF_DAY, settings.mStartTime.mHrs);
            calendar.set(Calendar.MINUTE, settings.mStartTime.mMin);
            calendar.set(Calendar.SECOND, 0);
            SetWakeup(manager, application, ALARM_START_LOG, calendar.getTimeInMillis());

            //Now do the Stopping Time
            calendar = Calendar.getInstance();  //Refresh
            if ((settings.mStopTime.mHrs * 60 + settings.mStopTime.mMin) < current) { LogView.Debug(TAG, "add-day"); calendar.add(Calendar.DATE, 1); } //Alarm for following day
            calendar.set(Calendar.HOUR_OF_DAY, settings.mStopTime.mHrs);
            calendar.set(Calendar.MINUTE, settings.mStopTime.mMin);
            calendar.set(Calendar.SECOND, 0);
            SetWakeup(manager, application, ALARM_STOP_LOG, calendar.getTimeInMillis());
        }

        //Return true if ok
        return true;
    }

    /**
     * \brief Generic Function for creating an alarm
     * \detail Had to add PendingIntent.FLAG_UPDATE_CURRENT, otherwise, intent extras were not being delivered...
     * @return              True if successful, false otherwise
     */
    private static void SetWakeup(AlarmManager manager, Context application, int action, long millis)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) //Handle Version below kitkat
        {
            LogView.Debug(TAG, "<19");
            manager.set(AlarmManager.RTC_WAKEUP, millis, PendingIntent.getBroadcast(application, action, (new Intent(application, AutoStarter.class)).putExtra(REQUEST_CODE, action), PendingIntent.FLAG_UPDATE_CURRENT));
        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            LogView.Debug(TAG, "<23");
            manager.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_SIZE_MS, PendingIntent.getBroadcast(application, action, (new Intent(application, AutoStarter.class)).putExtra(REQUEST_CODE, action), PendingIntent.FLAG_UPDATE_CURRENT));
        }
        else
        {
            LogView.Debug(TAG, "23+");
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, PendingIntent.getBroadcast(application, action, (new Intent(application, AutoStarter.class)).putExtra(REQUEST_CODE, action), PendingIntent.FLAG_UPDATE_CURRENT));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        //Check if this is a boot complete broadcast
        if ((intent.getAction() != null) && (intent.getAction().equalsIgnoreCase("android.intent.action.BOOT_COMPLETED")))
        {
            LogView.SetContext(context.getApplicationContext());
            LogView.Debug(TAG, "on-boot");
            SetAlarms(context.getApplicationContext());
        }
        else
        {
            //Initialise
            int action = intent.getIntExtra(REQUEST_CODE, -1);
            AlarmManager manager  = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Calendar     calendar = Calendar.getInstance();
            SettingsActivity.VjaggSettings settings = SettingsActivity.GetSettings(context.getApplicationContext());

            //Branch based on alarm
            switch (action)
            {
                case ALARM_START_LOG:
                {
                    LogView.Debug(TAG, "start-alarm");
                    //Start Service with Wakeful Intent
                    if (!TrackingService.IsAlive()) { LogView.Debug(TAG, "not-on"); startWakefulService(context.getApplicationContext(), new Intent(context.getApplicationContext(), TrackingService.class).putExtra(TrackingService.EXTRA_WAKEFUL, true)); }
                    //Set Calendar
                    calendar.set(Calendar.HOUR_OF_DAY, settings.mStartTime.mHrs);
                    calendar.set(Calendar.MINUTE, settings.mStartTime.mMin);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.add(Calendar.DATE, 1);
                    SetWakeup(manager, context.getApplicationContext(), ALARM_START_LOG, calendar.getTimeInMillis());
                }
                break;

                case ALARM_STOP_LOG:
                {
                    LogView.Debug(TAG, "stop-alarm");
                    //Stop Service
                    if (TrackingService.IsAlive())
                    {
                        LogView.Debug(TAG, "not-off");
                        context.stopService(new Intent(context.getApplicationContext(), TrackingService.class));
                        //If we opted for Auto-Send, set wakeup for 1 minute from now (to ensure that pp is done and also that alarm is called...
                        if (settings.mAutoSend) { LogView.Debug(TAG, "register-send"); SetWakeup(manager, context.getApplicationContext(), ALARM_SEND_LOG, System.currentTimeMillis() + 60000); }
                    }
                    //Set Calendar
                    calendar.set(Calendar.HOUR_OF_DAY, settings.mStopTime.mHrs);
                    calendar.set(Calendar.MINUTE, settings.mStopTime.mMin);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.add(Calendar.DATE, 1);
                    SetWakeup(manager, context.getApplicationContext(), ALARM_STOP_LOG, calendar.getTimeInMillis());
                }
                break;

                case ALARM_SEND_LOG:
                {
                    LogView.Debug(TAG, "send-alarm");

                    //If the TrackingService is on, then try again in one hour...
                    if (TrackingService.IsAlive())
                    {
                        LogView.Warn(TAG, "tracking-alive");
                        SetWakeup(manager, context.getApplicationContext(), ALARM_SEND_LOG, System.currentTimeMillis() + 3600000);
                        break;
                    }

                    //Resolve Network State...
                    ConnectivityManager conn_manager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
                    NetworkInfo         networkInfo  = conn_manager.getActiveNetworkInfo();
                    boolean             connected    = (networkInfo != null) && (networkInfo.isConnectedOrConnecting());

                    //If not connected, wait until a connection present... (repeat in 30 minutes)
                    if (!connected)
                    {
                        LogView.Warn(TAG, "no-connect");
                        SetWakeup(manager, context.getApplicationContext(), ALARM_SEND_LOG, System.currentTimeMillis() + 1800000);
                        break;
                    }

                    //If not wi-fi and user explicitly indicated wi-fi only... (try in 30 minutes)
                    if (settings.mWifiOnly && (networkInfo.getType() != ConnectivityManager.TYPE_WIFI))
                    {
                        LogView.Warn(TAG, "no-wifi");
                        SetWakeup(manager, context.getApplicationContext(), ALARM_SEND_LOG, System.currentTimeMillis() + 1800000);
                        break;
                    }

                    //Otherwise, start sending service...
                    startWakefulService(context.getApplicationContext(), new Intent(context.getApplicationContext(), SendJourneysService.class));
                }
                break;

                default:
                    LogView.Warn(TAG, "wrong-alarm");
                    break;
            }
        }
    }

    public static class SendJourneysService extends IntentService
    {
        private static final String TAG = "SJS";

        public SendJourneysService() { super("SendJourneyService"); }

        public void onHandleIntent (Intent intent)
        {
            LogView.Debug(TAG, "start-send");
            try
            {
                //Create Journey List...
                ArrayList<Journey> journeys = new ArrayList<>();
                File file                   = new File(getFilesDir(), TrackingService.GPS_RPP_FILE);
                BufferedInputStream reader  = new BufferedInputStream(new FileInputStream(file));

                while (true)    //Will only exit if an exception or we break...
                {
                    //Load Journey
                    Journey tmp_journey = new Journey();
                    Exception result = tmp_journey.loadRoute(reader);

                    //Handle Result
                    if (result == null)                         { journeys.add(tmp_journey); }
                    else if (result instanceof EOFException)    { LogView.Debug(TAG, "EOF."); break; }
                    else                                        { LogView.Error(TAG, result.toString()); return; }
                }

                //If we wish to store all journeys...
                if (SettingsActivity.GetSettings(getApplicationContext()).mKeepPers) { JourneyListAdapter.AppendJourneysToFile(this, true, journeys); } //Store to Personal History first

                //Now Attempt Sending...
                Exception result;
                SecureConnector connector = new SecureConnector(getApplicationContext());
                if ((result = connector.Connect()) != null) { LogView.Error(TAG, result.toString()); return; }
                for (Journey journey : journeys)
                {
                    if ((result = connector.SendJourney(journey)) != null) { LogView.Error(TAG, result.toString()); continue; }
                }
                connector.Disconnect();

                //Now delete the journey file
                reader.close();
                file.delete();
            }
            catch (Exception e)
            {
                LogView.Error(TAG, e.toString());
            }
            finally
            {
                AutoStarter.completeWakefulIntent(intent);
            }

        }
    }

}

