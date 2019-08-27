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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.CheckResult;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Iterator;

/**
 * Created by drt_researcher on 05/10/2016.
 */

public class TrackingService extends Service implements LocationListener, ConnectionCallbacks, OnConnectionFailedListener, GpsStatus.Listener, SensorEventListener, Watchdog.TimeOut
{
    //========================== INTENT PARAMETERS ==========================//
    static final String EXTRA_WAKEFUL = "vjagg.wakeful";

    //============================== CONSTANTS ==============================//
    static final String GPS_LOG_FILE   = "VJAGG_GPS_RT.dat";   //!< File for collection of GPS data in real-time
    static final String GPS_STA_FILE   = "VJAGG_GPS.";         //!< Stores all GPS data... (file-stub)
    static final String GPS_RPP_FILE   = "VJAGG_GPS_PP.dat";   //!< Stores the Formatted GPS Data
    static final String GPS_PER_FILE   = "VJAGG_JOUR_HIST.dat";//!< Stores the Journeys in the Personal History...

    //============================ STATE CONTROL ============================//
    private static final int TS_OFF = 0;    //!< The Tracking Service is off
    private static final int TS_GPS = 1;    //!< The Tracking Service is actively using the GPS location controller
    private static final int TS_ACC = 2;    //!< The Tracking Service is actively using the Accelerometer Sensor

    private static int mState = TS_OFF;
    private boolean    mConnx;              //!< Indicate if Google API is connected...

    //============================ INTERACTIONS ============================//
    private static WeakReference<StateListener> mRegistered = null; //!< Registers interest in a state listener for state updates
    private PowerManager.WakeLock               mWakeLock;          //!< Partial WakeLock... will have to maintain this...
    private Intent                              mIntent;            //!< Intent (wakeful) if this is the case...

    //=============================== TIMERS ===============================//
    private Watchdog mTimer;         //!< The internal timer..
    private int      mGPSTimeout;    //!< The timer index associated with the GPS timeout
    private int      mJourTimeout;   //!< The timer index associated with a journey timeout
    private int      mAccTimeout;    //!< The timer index associated with an accelerometer timeout

    //========================== GOOGLE SERVICES ==========================//
    private GoogleApiClient mAPIClient;   //!< Google API Client
    private LocationManager mLocations;   //!< Location Manager
    private SensorManager   mSensors;     //!< Sensor Manager
    private boolean         mAccelAvail;  //!< Accelerometer is present on the device...

    //=========================== LOGGING CONTROL ===========================//
    private static BufferedWriter   mWriteFile = null;  //!< Storage File: Greedy (Early) Initialisation
    private boolean                 mStoreAll;          //!< Store Everything...
    private GPSLogger               mGPSLogger;         //!< The GPS Logging Framework
    private AccelLogger             mAccLogger;         //!< The Accelerometer Logging Framework
    private long                    mStartTime;         //!< Start Accelerometer Run

    private static final String TAG    = "TS"; //!< Debug Name

    /**
     * \brief Interface to which an Activity registers...
     */
    public interface StateListener
    {
        void OnStateUpdate(boolean active);
    }

    private synchronized static void Open()
    {
        if (mWriteFile == null)
        {
            try
            {
                if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))  //Ensure that mounted
                {
                    new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vjagg").mkdirs();
                    mWriteFile = new BufferedWriter(new FileWriter(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vjagg/" + GPS_STA_FILE + Long.toString(System.currentTimeMillis() / 100000) + ".txt"), true));
                    LogView.Debug(TAG, "open-ok");
                }
                else
                {
                    LogView.Error(TAG, "media-error");
                }
            }
            catch (IOException e) { LogView.Error(TAG, e.toString()); }
        }
        else
        {
            LogView.Warn(TAG, "open-already");
        }
    }

    public synchronized static void Write(String str)
    {
        try
        {
            if (mWriteFile != null)
            {
                mWriteFile.write(str + "\n");
            }
        }
        catch (Exception e) { LogView.Warn(TAG, "SA I/O Error"); }
    }

    private synchronized  static void Close()
    {
        if (mWriteFile != null)
        {
            LogView.Debug(TAG, "store-close");
            try { mWriteFile.close(); mWriteFile = null; } catch (IOException e) { LogView.Error(TAG, "IO-Except " + e.toString()); }
        }
    }

    @Override
    public void onCreate()
    {
        LogView.Debug(TAG, "create");

        mWakeLock   = null;

        mGPSLogger  = null;
        mAccLogger  = null;

        mAPIClient = null;
        mLocations = null;
        mSensors   = null;

        mAccelAvail = false;
        mConnx      = false;

        mStoreAll   = false;

        mTimer = null;
        mGPSTimeout = mJourTimeout = mAccTimeout -1;
    }

    @Override
    public IBinder onBind(Intent intent) { throw new UnsupportedOperationException("Not yet implemented"); }


    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        LogView.Debug(TAG, "start");

        //Start Battery Logger
        if (Utilities.DEBUG_MODE)
        {
            BatteryLogger.StartLogging(this.getApplicationContext());

            if (Utilities.DEBUG_TYPE == 0)
            {
                BatteryLogger.pingBatteryLevel(this.getApplicationContext());

                //Inform
                if ((mRegistered != null) && (mState == TS_OFF)) { mRegistered.get().OnStateUpdate(true); } //update only if previously off...

                mState = TS_GPS;
            }
            else if (Utilities.DEBUG_TYPE == 1)
            {
                //Keep Backup if wakeful...
                if (intent.getBooleanExtra(EXTRA_WAKEFUL, false)) { LogView.Debug(TAG, "start-wakeful"); mIntent = intent; }

                //Ensure that we do not restart already running service...
                if (mState > TS_OFF)
                {
                    LogView.Warn(TAG, "already-start");
                    if (mRegistered != null) { mRegistered.get().OnStateUpdate(true); }
                    return START_STICKY;
                }

                //Acquire System Services...
                mAPIClient  = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(LocationServices.API).build();
                mLocations  = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

                //By Default no Store All
                mStoreAll = true;

                //Register just the GPS Timer
                mTimer      = new Watchdog(10);
                mGPSTimeout = mTimer.RegisterWatchdog(this, LoggingParams.GPS_NOSAT_TO);

                //Create Logger(s)
                mGPSLogger = new GPSLogger(this);
                mAccLogger = null;

                //Open Storage
                if (mStoreAll) { Open(); }

                //Connect API
                mAPIClient.connect();
            }
            else if (Utilities.DEBUG_TYPE == 2)
            {
                //Keep Backup if wakeful...
                if (intent.getBooleanExtra(EXTRA_WAKEFUL, false)) { LogView.Debug(TAG, "start-wakeful"); mIntent = intent; }

                //Ensure that we do not restart already running service...
                if (mState > TS_OFF)
                {
                    LogView.Warn(TAG, "already-start");
                    if (mRegistered != null) { mRegistered.get().OnStateUpdate(true); }
                    return START_STICKY;
                }

                //Get Sensors
                mSensors    = (SensorManager)   getSystemService(Context.SENSOR_SERVICE);
                mAccelAvail = (((SensorManager) getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);

                //Register Timers (some are optional)
                mTimer      = new Watchdog(10);
                mAccTimeout  = mTimer.RegisterWatchdog(this, LoggingParams.ACCEL_CHECK_RATE);

                //Start Logger
                mAccLogger = new AccelLogger();
                mTimer.StartWatchdog(mAccTimeout);

                //Write Battery Status
                BatteryLogger.pingBatteryLevel(this);
                Open();

                //Acquire WakeLock
                mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vjagg"); mWakeLock.acquire();
                mState = TS_ACC;

                //Inform that ok!
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(true); }
            }
            else if (Utilities.DEBUG_TYPE == 3)
            {
                //Ensure that we do not restart already running service...
                if (mState > TS_OFF)
                {
                    LogView.Warn(TAG, "already-start");
                    if (mRegistered != null) { mRegistered.get().OnStateUpdate(true); }
                    return START_STICKY;
                }

                //Get Sensors
                mSensors    = (SensorManager)   getSystemService(Context.SENSOR_SERVICE);
                mAccelAvail = (((SensorManager) getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);

                //Start Logger
                mAccLogger = new AccelLogger();
                mSensors.registerListener(this, mSensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), LoggingParams.ACCEL_SAMPLE_RATE);
                mStartTime = -1;

                //Enforce Storage
                mStoreAll = true;

                //Initialise the Logger
                mAccLogger.onStart();

                //Write Battery Status
                Open();
                BatteryLogger.pingBatteryLevel(this);

                //Acquire WakeLock
                mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vjagg"); mWakeLock.acquire();
                mState = TS_ACC;

                //Inform that ok!
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(true); }
            }
            else if (Utilities.DEBUG_TYPE == 4)
            {
                //Ensure that we do not restart already running service...
                if (mState > TS_OFF)
                {
                    LogView.Warn(TAG, "already-start");
                    if (mRegistered != null) { mRegistered.get().OnStateUpdate(true); }
                    return START_STICKY;
                }

                //Write Battery Status
                BatteryLogger.pingBatteryLevel(this);

                //Acquire WakeLock
                mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vjagg"); mWakeLock.acquire();
                mState = TS_ACC;

                //Inform that ok!
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(true); }
            }
        }
        else
        {
            //Keep Backup if wakeful...
            if ((intent != null) && (intent.getBooleanExtra(EXTRA_WAKEFUL, false))) { LogView.Debug(TAG, "start-wakeful"); mIntent = intent; }

            //Ensure that we do not restart already running service...
            if (mState > TS_OFF)
            {
                LogView.Warn(TAG, "already-start");
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(true); }
                return START_STICKY;
            }

            //All the same start logging
            BatteryLogger.StartLogging(this.getApplicationContext());

            //Acquire System Services...
            mAPIClient  = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(LocationServices.API).build();
            mLocations  = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            mSensors    = (SensorManager)   getSystemService(Context.SENSOR_SERVICE);
            mAccelAvail = (((SensorManager) getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);

            //Get Settings
            mStoreAll = SettingsActivity.GetSettings(getApplicationContext()).mStoreEverything;

            //Register Timers (some are optional)
            mTimer      = new Watchdog(10);
            mGPSTimeout = mTimer.RegisterWatchdog(this, LoggingParams.GPS_NOSAT_TO);
            if (!mStoreAll && mAccelAvail)
            {
                LogView.Debug(TAG, "accel-use");
                mJourTimeout = mTimer.RegisterWatchdog(this, LoggingParams.GPS_STATE_TO);
                mAccTimeout  = mTimer.RegisterWatchdog(this, LoggingParams.ACCEL_CHECK_RATE);
            }

            //Create Logger(s)
            mGPSLogger = new GPSLogger(this);
            if (!mStoreAll) { mAccLogger = new AccelLogger(); }
            else            { mAccLogger = null; }

            //Check Settings Flag
            if (mStoreAll || Utilities.DEBUG_BATTERY)
            {
                LogView.Debug(TAG, "store-all");
                Open();
            }

            //Write Battery Status
            BatteryLogger.pingBatteryLevel(this);

            //Acquire WakeLock - TODO Could this be causing issues?
            mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vjagg"); mWakeLock.acquire();

            //Connect API
            mAPIClient.connect();
        }

        //Start Sticky...
        return START_STICKY;
    }

    @Override
    public void onConnected(Bundle bundle)
    {
        LogView.Debug(TAG, "connected");

        mConnx = true;   //We are now connected

        if (Utilities.DEBUG_MODE)
        {
            if (Utilities.DEBUG_TYPE != 1) { LogView.Warn(TAG, "Wrong State"); }

            else
            {
                if ((mState == TS_OFF) || (mState == TS_GPS)) //We were previously off or in GPS Mode: only in these cases we need to start GPS Listener...
                {
                    LogView.Debug(TAG, mState == TS_OFF ? "off-state" : "gps-state");

                    //Start the GPS component & update state if ok...
                    if (StartGPSLogger(false))
                    {
                        LogView.Debug(TAG, "gps-on-ok");

                        //Inform the StateListener
                        if ((mRegistered != null) && (mState == TS_OFF)) { mRegistered.get().OnStateUpdate(true); } //update only if previously off...

                        //Update State
                        mState = TS_GPS;
                    }
                    else
                    {
                        LogView.Error(TAG, "gps-fail");
                        stopSelf();
                    }
                }
            }

        }
        else
        {
            if ((mState == TS_OFF) || (mState == TS_GPS)) //We were previously off or in GPS Mode: only in these cases we need to start GPS Listener...
            {
                LogView.Debug(TAG, mState == TS_OFF ? "off-state" : "gps-state");

                //Start the GPS component & update state if ok...
                if (StartGPSLogger(mAccelAvail))
                {
                    LogView.Debug(TAG, "gps-on-ok");

                    //Inform the StateListener
                    if ((mRegistered != null) && (mState == TS_OFF)) { mRegistered.get().OnStateUpdate(true); } //update only if previously off...

                    //Optionally enable Accelerometer if this is the Full-Logging Version (no optimisations)
                    if (mStoreAll) { mStartTime = -1; mSensors.registerListener(this, mSensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), LoggingParams.ACCEL_SAMPLE_RATE); }

                    //Update State
                    mState = TS_GPS;
                }
                else
                {
                    LogView.Error(TAG, "gps-fail");
                    stopSelf();
                }
            }
        }
    }

    @Override
    public void onConnectionSuspended (int cause)
    {
        LogView.Debug(TAG, "suspended");
        mConnx = false;       // We are now disconnected...
        if (mState == TS_GPS) // We were in GPS Mode, and hence we need to stop journey...but remain in the same state...
        {
            LogView.Debug(TAG, "stop-gps");
            StopGPSLogger(false);
        }
    }

    @Override
    public void onConnectionFailed (ConnectionResult result)
    {
        LogView.Error(TAG, "connx-fail");
        mConnx = false;
        stopSelf();
    }

    @Override
    public void onDestroy()
    {
        LogView.Debug(TAG, "destroy");

        if (Utilities.DEBUG_MODE)
        {
            if (Utilities.DEBUG_TYPE == 0)
            {
                //Stop Battery Logger
                BatteryLogger.pingBatteryLevel(getApplicationContext());
                BatteryLogger.StopLogging(getApplicationContext());

                //Inform
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(false); }

                mState = TS_OFF;
            }
            else if (Utilities.DEBUG_TYPE == 1)
            {
                BatteryLogger.pingBatteryLevel(getApplicationContext());
                switch (mState)
                {
                    case TS_OFF:
                        LogView.Debug(TAG, "state-off");
                        break;

                    case TS_GPS:
                        LogView.Debug(TAG, "state-gps");
                        StopGPSLogger(true);
                        break;

                    case TS_ACC:
                        LogView.Warn(TAG, "wrong state");
                        break;
                }

                //Kill all Watchdogs
                mTimer.KillWatchdog(mGPSTimeout);
                mTimer = null;

                //Nullify Controllers
                mGPSLogger = null;

                //Disconnect from Google Play Services
                mAPIClient.disconnect();
                mLocations = null;
                mAPIClient = null;

                //Clean up Storage File if need be
                Close();

                //Stop Battery Logging
                BatteryLogger.pingBatteryLevel(getApplicationContext());
                BatteryLogger.StopLogging(this.getApplicationContext());

                //Now State is off!
                mState = TS_OFF;

                //Finally indicate to the user that we have stopped
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(false); }

                //Finally release WakeLock(s)
                if (mIntent != null) { LogView.Debug(TAG, "stop-wakeful"); AutoStarter.completeWakefulIntent(mIntent); mIntent = null; }
            }
            else if (Utilities.DEBUG_TYPE == 2)
            {
                StopAccLogger(false);
                StopAccTimer();

                mTimer.KillWatchdog(mAccTimeout);
                mTimer = null;

                mAccLogger = null;
                mSensors   = null;

                Close();

                mState = TS_OFF;

                //Finally indicate to the user that we have stopped
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(false); }

                //Stop Battery Logging
                BatteryLogger.pingBatteryLevel(getApplicationContext());
                BatteryLogger.StopLogging(this.getApplicationContext());

                //Finally release WakeLock(s)
                if (mIntent != null) { LogView.Debug(TAG, "stop-wakeful"); AutoStarter.completeWakefulIntent(mIntent); mIntent = null; }
                mWakeLock.release(); mWakeLock = null;
            }
            else if (Utilities.DEBUG_TYPE == 3)
            {
                StopAccLogger(false);

                mAccLogger = null;
                mSensors   = null;

                Close();

                mState = TS_OFF;

                //Finally indicate to the user that we have stopped
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(false); }

                //Stop Battery Logging
                BatteryLogger.pingBatteryLevel(getApplicationContext());
                BatteryLogger.StopLogging(this.getApplicationContext());

                //Finally release WakeLock(s)
                if (mIntent != null) { LogView.Debug(TAG, "stop-wakeful"); AutoStarter.completeWakefulIntent(mIntent); mIntent = null; }
                mWakeLock.release(); mWakeLock = null;
            }

            else if (Utilities.DEBUG_TYPE == 4)
            {
                //Stop Battery Logging
                BatteryLogger.pingBatteryLevel(getApplicationContext());
                BatteryLogger.StopLogging(this.getApplicationContext());

                //Finally release WakeLock(s)
                mWakeLock.release(); mWakeLock = null;

                //Finally indicate to the user that we have stopped
                if (mRegistered != null) { mRegistered.get().OnStateUpdate(false); }
            }
        }
        else
        {
            //Ping Battery Level before starting cleaning up...
            BatteryLogger.pingBatteryLevel(getApplicationContext());

            switch (mState)
            {
                case TS_OFF:
                    LogView.Debug(TAG, "state-off");
                    break;

                case TS_GPS:
                    LogView.Debug(TAG, "state-gps");
                    StopGPSLogger(true);
                    //if (mStoreAll) { mSensors.unregisterListener(this); } //TODO Remove Comment under normal operation
                    break;

                case TS_ACC:
                    LogView.Debug(TAG, "state-acc");
                    StopAccLogger(false);
                    StopAccTimer();
                    break;
            }

            //Kill all Watchdogs
            mTimer.KillWatchdog(mGPSTimeout);
            if (mAccelAvail)
            {
                mTimer.KillWatchdog(mJourTimeout);
                mTimer.KillWatchdog(mAccTimeout);
            }
            mTimer = null;

            //Nullify Controllers
            mGPSLogger = null;
            mAccLogger = null;

            //Disconnect from Google Play Services
            mAPIClient.disconnect();
            mSensors   = null;
            mLocations = null;
            mAPIClient = null;

            //Clean up Storage File if need be
            Close();

            //Do Post-Processing...
            RoutePostProcessor rpp = new RoutePostProcessor();
            if (rpp.LoadLoggedJourneys(getApplicationContext()) != null) { LogView.Error(TAG, "PP load"); }
            else
            {
                //First eliminate all journeys whose bounds are less than 50 metres
                rpp.ThresholdDistance(LoggingParams.PP_CONS_DIST_THRESH);

                //Now Join Journeys
                rpp.JoinJourneys(LoggingParams.PP_JOIN_TIME_THRESH, LoggingParams.PP_JOIN_VEL_FACTOR);

                //Then truncate journeys less than 500m
                rpp.ThresholdDistance(LoggingParams.PP_ABS_DIST_THRESH);

                //Finally Trim ends of journeys to prevent spurious bursts (faster than 5m/s)
                rpp.TrimEnds(LoggingParams.PP_TRIM_MAX, LoggingParams.PP_TRIM_VEL_THRESH);

                //Save Journeys
                if (rpp.SaveJourneys(getApplicationContext()) != null) { LogView.Error(TAG, "PP save"); }
            }

            //Now State is off!
            mState = TS_OFF;

            //Finally indicate to the user that we have stopped
            if (mRegistered != null) { mRegistered.get().OnStateUpdate(false); }

            //Stop Battery Logging
            BatteryLogger.pingBatteryLevel(getApplicationContext());
            BatteryLogger.StopLogging(getApplicationContext());

            //Finally release WakeLock(s)
            if (mIntent != null) { LogView.Debug(TAG, "stop-wakeful"); AutoStarter.completeWakefulIntent(mIntent); mIntent = null; }
            mWakeLock.release(); mWakeLock = null;
        }
    }

    //============================= INTERACTION =============================//
    @CheckResult
    public static boolean IsAlive()
    {
        return mState > TS_OFF;
    }

    public static void RegisterListener(StateListener listener) { LogView.Debug(TAG, "register"); mRegistered = new WeakReference<>(listener); }

    public static void UnregisterListener() { LogView.Debug(TAG, "unregister"); mRegistered = null; }

    //============================= GPS CONTROL =============================//

    /**
     * \brief Start the GPS Logging Subsystem
     * @param timeout Indicates whether we should enable the Journey Timeout
     * @return
     */
    @CheckResult
    private boolean StartGPSLogger(boolean timeout)
    {
        LogView.Debug(TAG, "gps-start");
        try
        {
            //Create New Location Request
            LocationServices.FusedLocationApi.requestLocationUpdates(mAPIClient, (new LocationRequest().setInterval(LoggingParams.GPS_SAMPLE_RATE).setFastestInterval(LoggingParams.GPS_SAMPLE_RATE).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)), this);

            //Add GPS Status Listener
            mLocations.addGpsStatusListener(this);

            //Start GPS Logger
            mGPSLogger.OnStart();

            //Start Watchdogs
            mTimer.StartWatchdog(mGPSTimeout);
            if (timeout) { mTimer.StartWatchdog(mJourTimeout); }

            //If ok...
            if (Utilities.DEBUG_BATTERY) { Write((new StringBuilder("N ")).append(System.currentTimeMillis()).toString()); } //GPS is turned on
            return true;
        }
        catch (SecurityException e)
        {
            LogView.Error(TAG, e.toString());

            //Stop Self - will call onDestroy
            stopSelf();

            //Indicate failure
            return false;
        }
    }

    /**
     * TODO Revisit the user flag...
     * @param user
     */
    private void StopGPSLogger(boolean user)
    {
        LogView.Debug(TAG, "gps-stop");

        //Stop GPS Logger
        mGPSLogger.onStop(user);   //Stopping due to non-user intervention

        //Stop Location/Satellite Updates
        LocationServices.FusedLocationApi.removeLocationUpdates(mAPIClient, this);
        mLocations.removeGpsStatusListener(this);

        //Stop Timers
        mTimer.PauseWatchdog(mGPSTimeout);
        mTimer.PauseWatchdog(mJourTimeout);

        if (Utilities.DEBUG_BATTERY) { Write((new StringBuilder("O ")).append(System.currentTimeMillis()).toString()); } //GPS is turned off
    }

    @Override
    public void onLocationChanged(Location location)
    {
        //First Attempt write(s)
        if (mStoreAll) { Write((new StringBuilder("L ")).append(location.getTime()).append(" ").append(location.getLatitude()).append(" ").append(location.getLongitude()).toString()); }

        //Now call the gps logger method
        mGPSLogger.onNewLocation(location);

        //Also ping the State Timer if valid journey
        if (mGPSLogger.IsActive()) { LogView.Debug(TAG, "ping-jour"); mTimer.PingOK(mJourTimeout); }
    }

    @Override
    public void onGpsStatusChanged (int event)
    {
        if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS)
        {
            int usedInFix = 0;
            try
            {
                Iterator<GpsSatellite> iter = mLocations.getGpsStatus(null).getSatellites().iterator();
                while (iter.hasNext()) { if (iter.next().usedInFix()) { usedInFix++; } }
            }
            catch (SecurityException e)
            {
                LogView.Error(TAG, "permission " + e.toString());
                stopSelf();
            }

            //Log to File...
            if (mStoreAll) { Write((new StringBuilder("S ")).append(System.currentTimeMillis()).append(" ").append(usedInFix).toString()); }

            //Ping Appropriately
            if (usedInFix >= LoggingParams.GPS_MIN_SATS) { mTimer.PingOK(mGPSTimeout); } //Ping Watchdog to prevent timeout
        }
    }

    //============================= ACC CONTROL =============================//

    @CheckResult
    private boolean StartAccTimer()
    {
        LogView.Debug(TAG, "acc-t-start");
        //Start the Timer and return the result
        return mTimer.StartWatchdog(mAccTimeout);
    }

    private void StartAccLogger()
    {
        LogView.Debug(TAG, "acc-start");
        //Pause the timer itself..
        mTimer.PauseWatchdog(mAccTimeout);

        //Register Listener
        mSensors.registerListener(this, mSensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), LoggingParams.ACCEL_SAMPLE_RATE);
        mStartTime = -1;

        //Initialise the Logger
        mAccLogger.onStart();
    }

    private void StopAccLogger(boolean start)
    {
        LogView.Debug(TAG, "acc-stop");
        //Stop the Logger itself...
        mAccLogger.onStop();

        //Stop the updates...
        mSensors.unregisterListener(this);

        if (start) { mTimer.StartWatchdog(mAccTimeout); }
    }

    private void StopAccTimer()
    {
        LogView.Debug(TAG, "acc-t-stop");
        //Pause the Timer
        mTimer.PauseWatchdog(mAccTimeout);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) { /*Nothing to do here*/ }

    @Override
    public final void onSensorChanged(SensorEvent event)
    {
        long curr_time = System.currentTimeMillis();

        //Guard against excessive frequency of update
        if (curr_time - mStartTime < LoggingParams.ACCEL_SAMPLE_RATE_T) { return; }

        LogView.Debug(TAG, "sense");
        mStartTime = curr_time;

        //Note that this generates a lot of data... with a 100ms update rate, this amounts to ~8.2MB/Hour
        if (mStoreAll) { Write((new StringBuilder("A ")).append(curr_time).append(" ").append(event.values[0]).append(" ").append(event.values[1]).append(" ").append(event.values[2]).toString()); }

        if (!mStoreAll)
        {
            switch (mAccLogger.onNewAcceleration(event.values))
            {
                case AccelLogger.RET_SLEEP:
                    if(Utilities.DEBUG_MODE && Utilities.DEBUG_TYPE == 3)
                    {
                        mState = TS_ACC;
                        mAccLogger.onStop();
                        mAccLogger.onStart();
                    }
                    else
                    {
                        StopAccLogger(true);
                    }
                    break;

                case AccelLogger.RET_WAIT:
                    break; //Nothing to do...

                case AccelLogger.RET_PASS:
                    if (Utilities.DEBUG_MODE)
                    {
                        if(Utilities.DEBUG_TYPE == 2)
                        {
                            mState = TS_ACC;
                            StopAccLogger(true);
                        }
                        else if (Utilities.DEBUG_TYPE == 3)
                        {
                            mState = TS_ACC;
                            mAccLogger.onStop();
                            mAccLogger.onStart();
                        }
                    }
                    else
                    {
                        StopAccLogger(false);
                        StopAccTimer();
                        mState = TS_GPS;    //Indicate need to go to gps
                        //Use Side-Effects (short-circuit)
                        if (mConnx && !StartGPSLogger(mAccelAvail)) { stopSelf(); }
                    }
                    break;
            }
        }
    }

    //============================ TIMER CONTROL ============================//
    @Override
    public void HandleTimeOut(int wd)
    {
        LogView.Debug(TAG, "timeout");
        BatteryLogger.pingBatteryLevel(this);

        if (wd == mGPSTimeout)
        {
            LogView.Debug(TAG, "gps-to");
            if (mState != TS_GPS)
            {
                LogView.Warn(TAG, "not-gps");
            }
            else
            {
                mGPSLogger.OnSignalLoss();
            }
        }
        else if ((!mStoreAll) && (wd == mJourTimeout))
        {
            LogView.Debug(TAG, "jour-to");
            if (mState != TS_GPS)
            {
                LogView.Warn(TAG, "not-gps");
            }
            else
            {
                StopGPSLogger(false);
                if (StartAccTimer()) { mState = TS_ACC; }
                else                  { LogView.Error(TAG, "acc-start"); stopSelf(); }
            }
        }
        else if ((!mStoreAll) && (wd == mAccTimeout))
        {
            LogView.Debug(TAG, "acc-to");
            //ensure correct state
            if (mState != TS_ACC) { LogView.Warn(TAG, "not-acc"); }
            else
            {
                StartAccLogger();
            }
        }
    }
}

/**
 * \brief the Non-Member GPS Logger class
 */
class GPSLogger implements DownSampler.DownSampleHandler
{
    //===================== STATE CONTROL =====================//
    private static final int JOUR_INV   = -1; //!< Invalid State - Starts off here...
    private static final int JOUR_IDLE  =  0; //!< Idle and not logging to persistent storage
    private static final int JOUR_SRCH  =  1; //!< Searching for potential start point (as soon as two windows have been filled)
    private static final int JOUR_FIND  =  2; //!< Found Journey start... waiting for buffer to fill up to TERM_NUM_SUC_WIN
    private static final int JOUR_LOGD  =  3; //!< Fully Logging

    private static final int PNTBUFFER_SIZE = LoggingParams.GPS_WINDOW_SIZE * LoggingParams.GPS_DOWNSAMPLE;
    private static final String TAG         = "GL";

    //======================= VARIABLES =======================//
    private int             mState;         //!< The Journey State Controller
    private DownSampler     mDownSampler;   //!< DownSampling framework
    private WindowBuffer    mPointBuffer;   //!< Individual Point Buffer
    private WindowBuffer    mWindBuffer;    //!< Average Point Buffer
    private MarkovChain     mStartSearch;   //!< Idle to Log state change markov buffer
    private int             mStartPtr;      //!< Start Pointer for Journey (actual data point): Note that this is the normalised index into the mRawBuffer... not cyclic...
    private int             mStorePtr;      //!< The next item to store... on a call to store pointer...
    private Journey         mJourney;       //!< The journey object

    private Context         mApplication;   //!< The Application Context

    //!< Public Constructor...
    GPSLogger(Context context)
    {
        mState = JOUR_INV;  //Start off Invalid

        //Initialise Variables
        mDownSampler = null;
        mPointBuffer = null;
        mWindBuffer  = null;
        mStartSearch = null;
        mJourney     = null;
        FlushStart(-1);

        //Keep Track of Application Context
        mApplication = context.getApplicationContext();
    }

    //========================= State Control =========================//

    /**
     * \brief Should be called before the first location is given.. used to initialise everything...
     */
    boolean OnStart()
    {
        LogView.Debug(TAG, "start");
        //Ensure we have indeed not started yet!
        if (mState > JOUR_INV) { LogView.Warn(TAG, "already-active"); return false; }

        //Invalidate Pointers
        FlushStart(-1);

        //Initialise buffers and down-sampler
        mDownSampler = new DownSampler(LoggingParams.GPS_DOWNSAMPLE, this);
        mWindBuffer  = new WindowBuffer(LoggingParams.GPS_WINDOW_SIZE);
        mPointBuffer = new WindowBuffer(PNTBUFFER_SIZE);

        //Note that I do not initialise the journey or the start-search here: this is done within the respective state changes....

        //Set State
        mState = JOUR_IDLE;

        //If OK so far, return true...
        return true;
    }

    /**
     * \brief Called when the GPS signal is lost for an extended amount of time...
     */
    void OnSignalLoss()
    {
        LogView.Debug(TAG, "loss");
        //First Clean Up Journey if need be
        if (mState >= JOUR_FIND)
        {
            LogView.Info(TAG, "Jour active");

            //Find the Stop Point
            int stop_point = findStop(GetStartWindow()-LoggingParams.GPS_VEL_EN_NUM);
            LogView.Debug(TAG, "stop @ " + Integer.toString(stop_point));

            //Copy Journey up to stop point or 0
            StoreEntire(Math.max(stop_point, 0));
            mJourney.storeRoute((stop_point >= 0) ? Journey.TC_RE_SF : Journey.TC_RE_SL, new File(mApplication.getFilesDir(), TrackingService.GPS_LOG_FILE));
        }

        //Change State
        mState = JOUR_IDLE;  //Start from the idle invalid state...

        //Prepare for it
        mWindBuffer.flush();
        mPointBuffer.flush();
        mDownSampler.flush();
        FlushStart(-1);
    }

    /**
     * \brief Called when stopping GPS logging (due to some reason or another)
     */
    void onStop(boolean user)
    {
        LogView.Debug(TAG, "stop");
        if (mState >= JOUR_FIND)
        {
            LogView.Info(TAG, "Jour active");

            //Find the Stop Point
            int stop_point = findStop(GetStartWindow()-LoggingParams.GPS_VEL_EN_NUM);
            LogView.Debug(TAG, "stop @ " + Integer.toString(stop_point));

            //Copy Journey up to stop point or 0
            StoreEntire(Math.max(stop_point, 0));
            mJourney.storeRoute(user ? Journey.TC_RE_US : Journey.TC_RE_OS, new File(mApplication.getFilesDir(), TrackingService.GPS_LOG_FILE));
        }

        mDownSampler = null;
        mPointBuffer = null;
        mWindBuffer  = null;
        mStartSearch = null;
        mJourney     = null;

        mState = JOUR_INV;
    }

    /**
     * \brief Indicates if a journey is active...
     * @return
     */
    public boolean IsActive()
    {
        return mState > JOUR_SRCH;
    }

    //======================= Tracking Implementation =======================//

    /**
     * \brief Envisioned to be called each time there is a new GPS point...
     * @param location The Location object to act upon
     */
    public void onNewLocation(Location location)
    {
        //First Store any pending data which would be lost with this iteration. This will be ignored if stateless...
        StorePending(new RoutePoint(location));

        //Add to Downsampler
        mDownSampler.AddPoint(new RoutePoint(location));
    }

    /**
     * \brief Internal
     * @param rp The Routepoint which is the averaged value passed to the downsampler
     */
    public void OnDownSample(RoutePoint rp)
    {
        //Set Battery Level
        BatteryLogger.pingBatteryLevel(mApplication);

        //Initialise prerequisites...
        int next_state = mState;    //State initialised to current state
        mWindBuffer.AddPoint(rp);

        //State Change Logic
        switch (mState)
        {
            case JOUR_IDLE:
                LogView.Debug(TAG, "IDLE");
                if (mWindBuffer.getFilledFirst(2))
                {
                    //Change State
                    LogView.Debug(TAG, "Fill 2");
                    next_state = JOUR_SRCH;

                    //Prepare for Next State
                    mStartSearch = new MarkovChain(LoggingParams.GPS_VEL_ST_TRIG, LoggingParams.GPS_VEL_ST_TOTAL, LoggingParams.GPS_VEL_ST_NUM, mWindBuffer.getPoint(1));
                    mStartSearch.CheckMinTrigger(getDistance(1, 0), getTimeDiffer(1, 0), mWindBuffer.getPoint(0));
                    FlushStart(-1);
                }
                break;

            case JOUR_SRCH:
                LogView.Debug(TAG, "SRCH");
                if (mStartSearch.CheckMinTrigger(getDistance(1,0), getTimeDiffer(1,0), mWindBuffer.getPoint(0)))
                {
                    //Change State
                    LogView.Debug(TAG, "Found");
                    next_state = JOUR_FIND;

                    //Prepare for Next State
                    FlushStart(LoggingParams.GPS_VEL_ST_NUM); //These points are already part of the journey: since the velocities are one less than windows this coincides with 0-based indexing
                    mJourney = new Journey(); //Prepare the Journey
                }
                break;

            case JOUR_FIND:
                LogView.Debug(TAG, "FIND");
                if (GetStartWindow() >= LoggingParams.GPS_TERM_WIND - 1)
                {
                    //Switch State
                    LogView.Debug(TAG, "Accumulated");
                    next_state = JOUR_LOGD;
                }
                break;

            case JOUR_LOGD:
                LogView.Debug(TAG, "LOGD");
                if (getDistance(LoggingParams.GPS_TERM_WIND -1, 0) < LoggingParams.GPS_TERM_DIST_TOT) //If did not move enough
                {
                    //Attempt to find Stop Point
                    int stop_point = findStop(LoggingParams.GPS_TERM_WIND - 1);

                    //Branch
                    if (stop_point > LoggingParams.GPS_VEL_EN_NUM)
                    {
                        LogView.Info(TAG, "Stop @ " + stop_point);

                        //Store Journey
                        StoreEntire(stop_point);

                        //Attempt to locate Start Point
                        int start_pt = findStart(stop_point - 1);
                        if (start_pt > 0)
                        {
                            //Change State
                            LogView.Debug(TAG, "End @ ".concat(Integer.toString(stop_point)).concat(" & start @ win").concat(Integer.toString(start_pt)));
                            next_state = JOUR_FIND;

                            //Prepare for Next State
                            FlushStart(start_pt);
                        }
                        else
                        {
                            //Change State
                            LogView.Debug(TAG, "End @ ".concat(Integer.toString(stop_point)));
                            next_state = JOUR_SRCH;

                            //Prepare for State
                            FlushStart(-1); //Go to search but retain Markov Chain State which may have a partial trigger
                        }
                    }
                    else //TODO Consider just remaining in this state if this is the case...
                    {
                        LogView.Info(TAG, "No Stop");

                        //Store entire Journey
                        StoreEntire(0);

                        //Update State
                        next_state = JOUR_SRCH;

                        //Prepare for Next State
                        FlushStart(-1);
                        mStartSearch.Refresh(mWindBuffer.getPoint(0));
                    }

                    //In any case store journey
                    mJourney.storeRoute(Journey.TC_RE_NM, new File(mApplication.getFilesDir(), TrackingService.GPS_LOG_FILE));
                    if (next_state == JOUR_FIND) { mJourney = new Journey(); }//Prepare the Journey
                }
                break;
        }

        //Update State
        mState = next_state;
    }

    //======================== Storage Control ========================//
    private void FlushStart(int window)
    {
        LogView.Debug(TAG, "flush");
        if (window > -1) { mStorePtr = mStartPtr = (window+1)*LoggingParams.GPS_DOWNSAMPLE - 1;  } //Since for the zeroth window, the first (oldest) point is point DOWNSAMPLE_RATE-1
        else             { mStorePtr = 0; mStartPtr = -1; }
    }

    private void StorePending(RoutePoint rp)
    {
        LogView.Debug(TAG, "store-pend");
        //First Store any point which may be lost...
        if ((mStartPtr == PNTBUFFER_SIZE-1) && (mStorePtr == mStartPtr))
        {
            mJourney.addPoint(mPointBuffer.getPoint(mStartPtr).copy());
            mStorePtr--;
        }

        //Now Add point to buffer
        mPointBuffer.AddPoint(rp);

        //Now update the start/store pointers
        if (mStartPtr > -1)
        {
            mStorePtr = Math.min(mStorePtr + 1, PNTBUFFER_SIZE - 1); //Update the Storage pointer (which is the next point to be stored if needed...
            mStartPtr = Math.min(mStartPtr + 1, PNTBUFFER_SIZE - 1);
        }
    }

    private void StoreEntire(int end_window)
    {
        LogView.Debug(TAG, "store-all");
        //Modify end_window to point to correct raw point
        int end_point = end_window*LoggingParams.GPS_DOWNSAMPLE + mDownSampler.GetProcessed(); //Calculation involves the fact that if we are at window 1, then the last point is point 3, first point is point 5 (or 6-1)

        if ((mStorePtr <= mStartPtr) && (mStorePtr >= end_point)) //Copy only if store pointer is between start pointer and end pointer
        {
            //Copy all from store pointer up to end pointer. Now the End pointer is a window... but since the call to StoreEntire can happen
            //  from the GPS Timeout Handler, which is asynchronous, other samples may have gotten in since the last window update.
            //  Hence, the window boundary will have moved as well... to this end, we need to move the lower limit by the number of samples
            //  processed so far, which can be anywhere from 0 up to the DOWNSAMPLE_RATE-2.
            for (int i=mStorePtr; i >= end_point; i--)
            {
                mJourney.addPoint(mPointBuffer.getPoint(i).copy());
            }

            //Clean up
            mStorePtr = end_window*LoggingParams.GPS_DOWNSAMPLE - 1;
        }
    }

    //============================= Utilities =============================//
    /**
     * \brief Calculates the distance between the 'start' window and 'end' window
     * @param start Start Window to consider (inclusive)
     * @param end   End Window to consider (inclusive)
     * @return      Distance in metres...
     */
    private float getDistance(int start, int end)
    {
        float dist[] = new float[1];
        RoutePoint startPt, endPt;

        //Calculate Distanc/Velocity
        startPt = mWindBuffer.getPoint(start);
        endPt   = mWindBuffer.getPoint(end);
        Location.distanceBetween(startPt.mLatitude, startPt.mLongitude, endPt.mLatitude, endPt.mLongitude, dist);

        return dist[0];
    }

    /**
     * \brief Calculates the time difference between two windows as indexed
     * @param start Start Window to consider
     * @param end   End Window to consider
     * @return      The Time Differences (in milliseconds)
     */
    private long getTimeDiffer(int start, int end)
    {
        return mWindBuffer.getPoint(end).mTimeSt - mWindBuffer.getPoint(start).mTimeSt;
    }

    /**
     * \brief  Retrieve the window containing valid points
     * @return Start Window
     */
    private int GetStartWindow()
    {
        return (int)(Math.floor((mStartPtr + 1.0d)/LoggingParams.GPS_DOWNSAMPLE) - 1); //TODO this can be causing problems! need to verify that working ok...
    }

    /**
     * \brief  Attempts to identify the stopping point of the journey...
     * \detail This uses an internal Markov Chain Buffer and does not modify the actual start chain buffer
     * @param start The index from where to start searching
     * @return      The index of the last journey point which is valid...This points to the window before the end-trigger... -1 if none could be identified
     */
    private int findStop(int start)
    {
        /*LogView.Debug(TAG, "Searching for stop starting at Window " + Integer.toString(start));*/
        MarkovChain buf = new MarkovChain(LoggingParams.GPS_VEL_EN_TRIG, LoggingParams.GPS_VEL_EN_TOTAL, LoggingParams.GPS_VEL_EN_NUM, mWindBuffer.getPoint(start));
        int fndIdx  = -1 - LoggingParams.GPS_VEL_EN_NUM;    //By default, will return -1 when not found....

        for (int i = start; i > 0; i--)
        {
            if (buf.CheckMaxTrigger(getDistance(i, i - 1), getTimeDiffer(i, i - 1), mWindBuffer.getPoint(i - 1))) { fndIdx = i-1; }
        }

        return fndIdx+LoggingParams.GPS_VEL_EN_NUM; //We need to point to the window just before the stop trigger...
    }

    /**
     * \brief  Attempts to identify a potential Journey-Start trigger
     * \detail Note that this uses the actual member Markov Chain buffer, hence if at return time no full trigger was found, its state reflects the current MC state
     * @param start The start position from which to search (will search until the most recent)
     * @return      The index of the tentative start point or -1 if fails to find a valid one
     */
    private int findStart(int start)
    {
        mStartSearch.Refresh(mWindBuffer.getPoint(start)); //Flush buffer since we do not know the last time we used it...
        int fndIdx  = -1;    //!< Indicates that we found a potential starting point...

        for (int i = start; i>0; i--)
        {
            if (mStartSearch.CheckMinTrigger(getDistance(i, i - 1), getTimeDiffer(i, i - 1), mWindBuffer.getPoint(i - 1))) { fndIdx = i+2; break; }
        }

        return fndIdx;
    }
}

class AccelLogger
{
    //===================== STATE CONTROL =====================//
    private static final int ALS_UNDEF = 0;  //!< Undefined State...
    private static final int ALS_CHECK = 1;  //!< Quick Sample Size (3/4 accelerometer samples)
    private static final int ALS_ENSUR = 2;  //!< Extra Sample Set (10-15 accelerometer samples)

    public  static final int RET_SLEEP = 0;  //!< Go Back to sleep
    public  static final int RET_WAIT  = 1;  //!< Wait for another sample
    public  static final int RET_PASS  = 2;  //!< We passed the thresholds...

    private static final String TAG = "AL";  //!< Debugging Tag

    //======================= VARIABLES =======================//
    private int         mState;     //!< State Control
    private double[]    mAverage;   //!< Average Calculation (window)
    private int         mCount;     //!< Sample Count...


    public AccelLogger()
    {
        mState = ALS_UNDEF;
        mCount = -1;
        mAverage = null;
    }

    public boolean onStart()
    {
        LogView.Debug(TAG, "start");
        if (mState > ALS_UNDEF) { LogView.Warn(TAG, "already-start"); return false; }

        mCount   = LoggingParams.QUICK_SAMPLE_SIZE;
        mAverage = new double[mCount];
        mState   = ALS_CHECK;

        //If everything still here..
        return true;
    }

    public int onNewAcceleration(float[] acceleration)
    {
        LogView.Debug(TAG, "new " + Integer.toString(mCount));

        //Calculate Magnitude and store in window...
        mAverage[mAverage.length - (mCount--)] = Math.sqrt(Math.pow(acceleration[0], 2) + Math.pow(acceleration[1], 2) + Math.pow(acceleration[2], 2));

        //If count is greater than 0, i.e. we can put in more samples...
        if (mCount > 0) { return RET_WAIT; }

        //else
        double average = 0;
        for (double reading : mAverage) { average += Math.pow(reading - 9.81, 2); }

        //Branch based on state
        if (mState == ALS_CHECK)
        {
            //Check if we passed the threshold
            if (average < LoggingParams.QUICK_SAMPLE_THRESH) { mState = ALS_CHECK; return RET_SLEEP; }
            else
            {
                mState = ALS_ENSUR;
                mCount = LoggingParams.EXTRA_SAMPLE_SIZE;
                mAverage = new double[mCount];
                return RET_WAIT;
            }
        }
        else
        {
            //Check if we passed the threshold
            if (average < LoggingParams.EXTRA_SAMPLE_THRESH) { mState = ALS_CHECK; return RET_SLEEP; }
            else                                             { mState = ALS_UNDEF; return RET_PASS;  }
        }
    }

    public void onStop()
    {
        mState = ALS_UNDEF;
        mCount = -1;
        mAverage = null;
    }
}
