//
//  TrackingService.swift
//  vjagg
//
//  Created by administrator on 12/01/2017.
//
//  Copyright (C) 2019  Michael Camilleri
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <https://www.gnu.org/licenses/>.
//
//  Request Permissions: https://developer.apple.com/reference/corelocation/cllocationmanager
//  Not sure if it possible to change location update rate...
//  Consider deferred location updates


import CoreLocation
import CoreMotion
import Foundation
import MapKit

enum TrackerStatus
{
    case done;
    case wait;
    case fail;
}

protocol StateListener
{
    var  View : UIViewController { get }
    func OnStart(success:TrackerStatus);
    func OnPostProcess(success:TrackerStatus);
}

class TrackingService : NSObject, CLLocationManagerDelegate, TimerDelegate
{
    //========================== INTENT PARAMETERS ==========================//
    static let EXTRA_WAKEFUL = "vjagg.wakeful";
    
    //============================== CONSTANTS ==============================//
    static let GPS_LOG_FILE   = "VJAGG_GPS_RT.dat";   //!< File for collection of GPS data in real-time
    static let GPS_STA_FILE   = "VJAGG_GPS.";         //!< Stores all GPS data... (file-stub)
    static let GPS_RPP_FILE   = "VJAGG_GPS_PP.dat";   //!< Stores the Formatted GPS Data
    static let GPS_PER_FILE   = "VJAGG_JOUR_HIST.dat";//!< Stores the Journeys in the Personal History...
    
    //============================ STATE CONTROL ============================//
    enum TrackState : Int
    {
        case TS_ERR = -1; //!< Error in Tracking Service (undefined state)
        case TS_OFF =  0; //!< The Tracking Service is off
        case TS_GPS =  1; //!< The Tracking Service is actively using the GPS location controller
        case TS_ACC =  2; //!< The Tracking Service is actively using the Accelerometer Sensor
        case TS_RPP =  3; //!< The Tracking Service is performing post-processing operation.
        
        public static func > (a:TrackState, b:TrackState)->Bool { return a.rawValue > b.rawValue; }
        public static func < (a:TrackState, b:TrackState)->Bool { return a.rawValue < b.rawValue; }
    }
    
    private static let GPS_TO_ID = 0;   //!< GPS Timeout ID
    private static let JNF_TO_ID = 1;   //!< Journey inexistent timeout ID
    private static let ACC_TO_ID = 2;   //!< Accelerometer timeout ID
    
    //========================== SINGLETON CONSTRUCT ========================//
    private static let SINGLE = TrackingService();
    private        let TAG = "Tracker";
    
    static var Alive : Bool { return SINGLE.mState > TrackState.TS_OFF; }
    
    //============================ STATE VARIABLES =========================//
    private var mState  : TrackState;   //!< State of the Logger
    private var mGPS    : GPSLogger;    //!< The GPS Handler
    private var mAccel  : AccelLogger;  //!< The Accelerometer Handler
    private var mDeleg  : StateListener?; //!< State Listener object
    
    private var mGPSTimer : Watchdog!;   //!< GPS Timer Watchdog
    private var mJNFTimer : Watchdog!;   //!< Journey Timer
    //private var mACCTimer : Watchdog!;   //!< Accelerometer Timer
    
    //=============================== MANAGERS ============================//
    private var mLocations  : CLLocationManager;
    private var mMotion     : CMMotionManager;
    private var mAccelAvail : Bool;
    private var mStartReq   : Bool;

    private override init()
    {
        Debugger.Debug(TAG, "Init");
        
        //Set State
        mState = TrackState.TS_OFF;
        
        mGPS   = GPSLogger();
        mAccel = AccelLogger();
        mDeleg = nil;
        mStartReq = false;
        
        mLocations  = CLLocationManager();
        mMotion     = CMMotionManager();
        mAccelAvail = mMotion.isAccelerometerAvailable;
        
        //Initialise Super and then the remaining stuff which needs to have a valid self pointer;
        super.init();
        
        //Refresh Timers
        mGPSTimer = Watchdog(ID: TrackingService.GPS_TO_ID, TO: LoggingParams.GPS_NOSAT_TO, delegate: self);
        mJNFTimer = Watchdog(ID: TrackingService.JNF_TO_ID, TO: LoggingParams.GPS_STATE_TO, delegate: self);
        //mACCTimer = Watchdog(ID: TrackingService.ACC_TO_ID, TO: LoggingParams.ACCEL_CHECK_RATE, delegate: self);
        
        //Set Up Locations Parameters
        mLocations.desiredAccuracy = kCLLocationAccuracyBest;
        mLocations.allowsBackgroundLocationUpdates = true;
        mLocations.delegate = self;
        
        //Set Up Motions Parameters
        mMotion.accelerometerUpdateInterval = LoggingParams.ACCEL_SAMPLE_RATE
    }
    
    //static func NotifyMe(notifyMe : StateListener) { mDeleg = notifyMe; }
    
    static func Start(notifyMe : StateListener) -> TrackerStatus { return SINGLE.start(notifyMe: notifyMe); }
    private func start(notifyMe : StateListener) -> TrackerStatus
    {
        Debugger.Debug(TAG, "Start");
        
        //Ensure that we do not restart already running service...
        if (mState > TrackState.TS_OFF)
        {
            Debugger.Warn(TAG, "Already Running");
            return TrackerStatus.done;
        }
        
        //Start Req
        mStartReq = true;
        
        //Now start Authorisation Process 
        switch(CLLocationManager.authorizationStatus())
        {
        case .authorizedAlways:
            Debugger.Debug(TAG, "Already Authorised");
            //Start the GPS component & update state if ok...
            mState = startGPSLogger() ? TrackState.TS_GPS : TrackState.TS_OFF;
            return mState == TrackState.TS_GPS ? TrackerStatus.done : TrackerStatus.fail;
            
        case .notDetermined:
            Debugger.Debug(TAG, "2 B Determined");
            mDeleg = notifyMe;
            //mLocations.requestAlwaysAuthorization(); //Will Try to get it!
            return TrackerStatus.wait;
            
        case .authorizedWhenInUse, .restricted, .denied:
            Debugger.Debug(TAG, "Not Allowed");
            mDeleg = notifyMe;
            _ = Util.DisplayMsg(onview: notifyMe.View,
                            title: "Background Location Access Disabled",
                            showing: "Vjagg requires access to location services in the background. This means you must open this app's settings and set location access to 'Always'. Logging will not commence otherwise.",
                            okTxt: "Open Settings",
                            onOK: self.onAuthoriseLocation,
                            showCancel:true,
                            onCancel:self.onCancelAuthorisation);
            return TrackerStatus.wait;
        }

    }

    /**
     * \brief Override for Location Manager Authorisation Status Change
     */
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus)
    {
        Debugger.Debug(TAG, "Author. Change");
        
        //Ignore any Call to this when not requested
        if (!mStartReq) { return; }
        
        //Check Authorisation Status
        switch status
        {
        case .authorizedAlways:
            Debugger.Debug(TAG, "Authorised OK");
            //Start the GPS component & update state if ok...
            mState = startGPSLogger() ? TrackState.TS_GPS : TrackState.TS_OFF;
            mDeleg?.OnStart(success: mState == TrackState.TS_GPS ? TrackerStatus.done : TrackerStatus.fail);
            mDeleg = nil;
            
        case .notDetermined:
            Debugger.Warn(TAG, "Still Undetermined");
            mLocations.requestAlwaysAuthorization(); //Will Try to get it!
            
        case .authorizedWhenInUse, .restricted, .denied:
            Debugger.Debug(TAG, "Still Not Allowed");
            if let deleg = mDeleg {
            _ = Util.DisplayMsg(onview: deleg.View,
                            title: "Background Location Access Disabled",
                            showing: "Vjagg requires access to location services in the background. This means you must open this app's settings and set location access to 'Always'.",
                            okTxt: "Open Settings",
                            onOK: self.onAuthoriseLocation,
                            showCancel:true,
                            onCancel:self.onCancelAuthorisation);
            }
        }

    }

    private func onAuthoriseLocation(alert:UIAlertAction)
    {
        //Attempt to Authorise
        Debugger.Debug(TAG, "Authorising");
        if let url = NSURL(string:UIApplicationOpenSettingsURLString)
        {
            //Defer Execution:
            OperationQueue.current?.addOperation { UIApplication.shared.openURL(url as URL) }
        }
    }
    
    private func onCancelAuthorisation(alert:UIAlertAction)
    {
        Debugger.Debug(TAG, "User Cancel");
        mDeleg?.OnStart(success: TrackerStatus.fail);
        mDeleg = nil;
    }
    
    static func Stop(notifyMe:StateListener) -> TrackerStatus { return SINGLE.stop(notifyMe:notifyMe); }
    private func stop(notifyMe:StateListener) -> TrackerStatus
    {
        Debugger.Debug(TAG, "Stop");
        
        //Ensure not Off already
        guard (mState > TrackState.TS_OFF) else { Debugger.Warn(TAG, "Already Dead"); return TrackerStatus.done; }
        
        //Set Delegate
        mDeleg = notifyMe;
        
        //Ensure not in process of post-processing...
        guard (mState != TrackState.TS_RPP) else { Debugger.Warn(TAG, "Still PostProcessing"); return TrackerStatus.wait; }
        
        //Remove Start Requested Listener
        mStartReq = false;
        
        //Stop the respective Processes
        stopGPSLogger(userInitiated: true);
        stopAccLogger();
        
        //Update State
        mState = TrackState.TS_RPP;
        
        //Start Post Processing... and return immediately with Waiting
        Util.AsyncTask(postProcess, onDone);
        return TrackerStatus.wait;
    }
    
    private func postProcess() -> Bool
    {
        Debugger.Debug(TAG, "PP Start");
        
        //Attempt Load
        guard let rpp = RoutePostProcessor() else { Debugger.Debug(TAG, "PP File Empty"); return false; }
        
        //Now perform operations //TODO - remove comments
        rpp.ThresholdOnDistance(lessThan: LoggingParams.PP_CONS_DIST_THRESH);
        rpp.JoinJourneys(ifTimeLessThan: LoggingParams.PP_JOIN_TIME_THRESH, andVelocityWithin: LoggingParams.PP_JOIN_VEL_FACTOR);
        rpp.ThresholdOnDistance(lessThan:LoggingParams.PP_ABS_DIST_THRESH);
        rpp.AddDummy();
        rpp.TrimEnds(upTo: LoggingParams.PP_TRIM_MAX, underThreshold: LoggingParams.PP_TRIM_VEL_THRESH);
        
        //Finally Save the Results
        return rpp.toFile();
    }
    
    private func onDone(success:Bool)
    {
        Debugger.Debug(TAG, "PP Done");
        
        //Update state
        mState = TrackState.TS_OFF;
        
        //Inform delegate
        mDeleg?.OnPostProcess(success: success ? TrackerStatus.done : TrackerStatus.fail);
        mDeleg = nil;
    }
    
    
    //============================= GPS CONTROL =============================//
    
    /**
     * \brief Start the GPS Logging Subsystem
     * @return
     */
    private func startGPSLogger() -> Bool
    {
        Debugger.Debug(TAG, "Start GPS");
        
        //Initialise the GPS Logger Object
        guard mGPS.onStart() else { Debugger.Error(TAG, "GPS-Error"); return false; }
        
        //Commence Timers
        guard mGPSTimer.start() else
        {
            Debugger.Error(TAG, "GPS Timer Error");
            mGPS.onStop(userInitiated: false);
            return false;
        }
        
        if mAccelAvail
        {
            Debugger.Debug(TAG, "Acc Avail");
            guard mJNFTimer.start() else
            {
                Debugger.Error(TAG, "JNF Timer Error");
                _ = mGPSTimer.stop();
                mGPS.onStop(userInitiated: false);
                return false;
            }
        }
        
        //Start the Updation of Location
        mLocations.startUpdatingLocation();

        //Updates
        return true;
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation])
    {
        //Ensure we are in GPS Mode indeed
        guard mState == TrackState.TS_GPS else { Debugger.Warn(TAG, "GPS Fired in {Acc} Mode"); return; }
        
        for location in locations
        {
            //Update and process
            if (location.horizontalAccuracy < LoggingParams.GPS_UNC_MIN)
            {
                //Debug : Print
                Debugger.Debug(TAG, "Loc: \(location.Minimal)");
                
                //Then Ping the Satellite Timer:
                _ = mGPSTimer.ping();
                
                //Update and Process Location
                mGPS.onNewLocation(location);
                
                //Ping Journey Timer if a journey is active
                if (mGPS.active) { _ = mJNFTimer.ping(); }
            }
            else
            {
                Debugger.Debug(TAG, "Loc: N/A");
            }
        }
    }
    
    /**
     * TODO Revisit the user flag...
     * @param user
     */
    private func stopGPSLogger(userInitiated:Bool)
    {
        Debugger.Debug(TAG, "Stop GPS");
        
        //Stop GPS Logger
        mGPS.onStop(userInitiated: userInitiated);   //Stopping due to non-user intervention?
        
        //Stop Location/Satellite Updates
        mLocations.stopUpdatingLocation();
        
        //Stop Timers
        _ = mGPSTimer.stop();
        _ = mJNFTimer.stop();
    }
    

    //============================= ACC CONTROL =============================//
    /*
    @CheckResult
    private boolean StartAccTimer()
    {
        LogView.Debug(TAG, "acc-t-start");
        //Start the Timer and return the result
        return mTimer.StartWatchdog(mAccTimeout);
    }
    */
    
    private func startAccLogger() -> Bool
    {
        Debugger.Debug(TAG, "Start ACC"); //mTimer.PauseWatchdog(mAccTimeout);
        
        //Prepare Acceleration Handler:
        guard mAccel.onStart() else { Debugger.Error(TAG, "ACC Error"); return false; }
        
        //Register Listener
        mMotion.startAccelerometerUpdates(to: OperationQueue.current!, withHandler: self.onAcceleration);
        
        //Return indication of success
        return true;
    }
    
    private func stopAccLogger()
    {
        Debugger.Debug(TAG, "Stop ACC");
        
        //Unregister the Listener
        mMotion.stopAccelerometerUpdates();
        
        //Clean up the Logger
        mAccel.onStop();
    }
    
    /*
    private void StopAccTimer()
    {
        LogView.Debug(TAG, "acc-t-stop");
        //Pause the Timer
        mTimer.PauseWatchdog(mAccTimeout);
    }
    */
    
    func onAcceleration(data:CMAccelerometerData?, error:Error?)
    {
        //Ensure we are in Acceleration Mode
        guard mState == TrackState.TS_ACC else { Debugger.Warn(TAG, "Acc Fired in {GPS} Mode"); return; }
        
        //Ensure no Error!
        guard error == nil else { Debugger.Error(TAG, "Accel: \(error)"); return; }
        
        //Debug
        Debugger.Debug(TAG, "Accel: \(data?.Minimal)");
        
        //Only Process if pass is returned...
        if (mAccel.onNewAcceleration(acc: data!.acceleration) == AccelLogger.RET_PASS)
        {
            //Stop Acceleration Updates
            stopAccLogger();
            
            //Start GPS
            guard startGPSLogger() else { Debugger.Error(TAG, "GPS Start Failure"); mState = TrackState.TS_ERR; return; }
            
            //Update state
            self.mState = TrackState.TS_GPS;
        }
    }
    
    //============================ TIMER CONTROL ============================//
    
    func HandleTimeOut(from watchdog: Int)
    {
        switch (watchdog)
        {
        case TrackingService.GPS_TO_ID:
            Debugger.Debug(TAG, "GPS Timeout");
            guard (mState == TrackState.TS_GPS) else { Debugger.Warn(TAG, "State Mismatch (1)"); return; }
            mGPS.onSignalLoss();
            
        case TrackingService.JNF_TO_ID:
            Debugger.Debug(TAG, "JNF Timeout");
            guard (mState == TrackState.TS_GPS) else { Debugger.Warn(TAG, "State Mismatch (1)"); return; }
            stopGPSLogger(userInitiated: false);
            //If unable to start the Accelerometer, at least retry with GPS
            if (!startAccLogger())
            {
                Debugger.Error(TAG, "Accel Fail: Revert to GPS");
                mAccelAvail = false;
                guard startGPSLogger() else { Debugger.Error(TAG, "GPS Start Failure"); mState = TrackState.TS_ERR; return; }
                mState = TrackState.TS_GPS;
            }
            mState = TrackState.TS_ACC;
            
        default:
            Debugger.Warn(TAG, "Timer N/A (\(watchdog))");
        }
    }
}

/**
 * \brief the Non-Member GPS Logger class
 */
class GPSLogger : DownSampleDelegate
{
    //===================== STATE CONTROL =====================//
    private static let JOUR_INV   = -1; //!< Invalid State - Starts off here...
    private static let JOUR_IDLE  =  0; //!< Idle and not logging to persistent storage
    private static let JOUR_SRCH  =  1; //!< Searching for potential start point (as soon as two windows have been filled)
    private static let JOUR_FIND  =  2; //!< Found Journey start... waiting for buffer to fill up to TERM_NUM_SUC_WIN
    private static let JOUR_LOGD  =  3; //!< Fully Logging

    /*
    private enum State_t : Int
    {
        case JOUR_INV   = -1; //!< Invalid State - Starts off here...
        case JOUR_IDLE  =  0; //!< Idle and not logging to persistent storage
    }
 */
    
    private static let PNTBUFFER_SIZE = LoggingParams.GPS_WINDOW_SIZE * LoggingParams.GPS_DOWNSAMPLE;
    
    //======================= VARIABLES =======================//
    private var mState          : Int;            //!< The Journey State Controller
    private var mDownSampler    : DownSampler!;   //!< DownSampling framework
    private var mPointBuffer    : WindowBuffer;   //!< Individual Point Buffer
    private var mWindBuffer     : WindowBuffer;   //!< Average Point Buffer
    private var mStartSearch    : MarkovChain;    //!< Idle to Log state change markov buffer
    private var mStartPtr       : Int;            //!< Start Pointer for Journey (actual data point): Note that this is the normalised index into the mRawBuffer... not cyclic...
    private var mStorePtr       : Int;            //!< The next item to store... on a call to store pointer...
    private var mJourney        : Journey;        //!< The journey object
    
    private let TAG = "GPS";
    
    //Computed Properties
    var active : Bool { return mState > GPSLogger.JOUR_SRCH; }
    
    //!< Public Constructor...
    init()
    {
        Debugger.Debug(TAG, "Init");
        
        mState = GPSLogger.JOUR_INV;  //Start off Invalid
        
        //Initialise Variables
        mWindBuffer  = WindowBuffer(with: LoggingParams.GPS_WINDOW_SIZE);
        mPointBuffer = WindowBuffer(with: GPSLogger.PNTBUFFER_SIZE);
        mStartSearch = MarkovChain(LoggingParams.GPS_VEL_ST_TRIG, LoggingParams.GPS_VEL_ST_TOTAL, LoggingParams.GPS_VEL_ST_NUM, nil);
        mJourney     = Journey();
        mStorePtr    = 0;
        mStartPtr    = -1;
        
        //Has to be done last, otherwise it complains!
        mDownSampler = DownSampler(rate: LoggingParams.GPS_DOWNSAMPLE, handler: self);
    }
    
    
    //========================= State Control =========================//
    
    /**
     * \brief Should be called before the first location is given.. used to initialise everything...
     */
    func onStart() -> Bool
    {
        Debugger.Debug(TAG, "onStart");
        
        //Ensure we have indeed not started yet!
        guard mState == GPSLogger.JOUR_INV else { Debugger.Warn(TAG, "Already Active"); return false; }
        
        //Invalidate Pointers
        flushStart(startFrom: -1);
        
        //Set State and return...
        mState = GPSLogger.JOUR_IDLE;
        return true;
    }
    
    /**
     * \brief Called when the GPS signal is lost for an extended amount of time...
     */
    func onSignalLoss()
    {
        Debugger.Debug(TAG, "Signal Loss");
        
        //First Clean Up Journey if need be
        if (mState >= GPSLogger.JOUR_FIND)
        {
            //Find the Stop Point
            let stop_point = findStop(starting: getStartWindow()-LoggingParams.GPS_VEL_EN_NUM);
            
            //Now store
            if (!flushAllAndStore(upTo: stop_point, with: (stop_point > -1) ? Journey.TC_RE_SF : Journey.TC_RE_SL)) { Debugger.Error(TAG, "File-Write Error"); }
        }
        
        //Change State
        mState = GPSLogger.JOUR_IDLE;  //Start from the idle invalid state...
        
        //Prepare for it
        mWindBuffer.flush();
        mPointBuffer.flush();
        mDownSampler.flush();
        mJourney.flush();
        flushStart(startFrom: -1);
    }
   
    /**
     * \brief Called when stopping GPS logging (due to some reason or another)
     */
    func onStop(userInitiated : Bool)
    {
        Debugger.Debug(TAG, "onStop");
        
        if (mState >= GPSLogger.JOUR_FIND)
        {
            //Find the Stop Point
            let stop_point = findStop(starting: getStartWindow()-LoggingParams.GPS_VEL_EN_NUM);
            
            //Copy Journey up to stop point or 0
            if (!flushAllAndStore(upTo: stop_point, with: userInitiated ? Journey.TC_RE_US : Journey.TC_RE_OS)) { Debugger.Error(TAG, "File-Write Error"); }
        }
        
        mDownSampler.flush();
        mPointBuffer.flush();
        mWindBuffer.flush();
        mStartSearch.flush();
        mJourney.flush();
        
        mState = GPSLogger.JOUR_INV;
    }
    
    //======================= Tracking Implementation =======================//
    
    /**
     * \brief Envisioned to be called each time there is a new GPS point...
     * @param location The Location object to act upon
     */
    func onNewLocation(_ location : CLLocation)
    {
        let rp = RoutePoint(from: location);
        
        //First Store any pending data which would be lost with this iteration. This will be ignored if stateless...
        flushPending(rp: rp);
        
        //Add to Downsampler
        mDownSampler.add(sample: rp);
    }
    
    /**
     * \brief Internal
     * @param rp The Routepoint which is the averaged value passed to the downsampler
     */
    func OnDownSample(with : RoutePoint)
    {
        Debugger.Debug(TAG, "DownSample - \(with)");
        
        //Initialise prerequisites...
        var next_state = mState;    //State initialised to current state
        mWindBuffer.add(point: with);
        
        //State Change Logic
        switch (mState)
        {
        case GPSLogger.JOUR_IDLE:
            Debugger.Debug(TAG, "[IDLE]");
            if (mWindBuffer.getFilledFirst(numOf: 2))
            {
                Debugger.Debug(TAG, " > [SRCH]");
                
                //Change State
                next_state = GPSLogger.JOUR_SRCH;
                
                //Prepare for Next State
                mStartSearch.refresh(old_window: mWindBuffer[1]);
                _ = mStartSearch.CheckMinTrigger(getDistance(from: 1, to: 0), getTimeDiffer(from: 1, to: 0), new_window: mWindBuffer[0]);
                flushStart(startFrom: -1);
            }
            break;
            
        case GPSLogger.JOUR_SRCH:
            Debugger.Debug(TAG, "[SRCH]");
            if (mStartSearch.CheckMinTrigger(getDistance(from: 1,to: 0), getTimeDiffer(from: 1,to: 0), new_window: mWindBuffer[0]))
            {
                Debugger.Debug(TAG, " > [FIND]");
                
                //Change State
                next_state = GPSLogger.JOUR_FIND;
                
                //Prepare for Next State
                flushStart(startFrom: LoggingParams.GPS_VEL_ST_NUM); //These points are already part of the journey: since the velocities are one less than windows this coincides with 0-based indexing
                mJourney.flush(); //Prepare the Journey
            }
            break;
            
        case GPSLogger.JOUR_FIND:
            Debugger.Debug(TAG, "[FIND]");
            if (getStartWindow() >= LoggingParams.GPS_TERM_WIND - 1)
            {
                Debugger.Debug(TAG, " > [LOGD]");
                //Switch State
                next_state = GPSLogger.JOUR_LOGD;
            }
            break;
            
        case GPSLogger.JOUR_LOGD:
            Debugger.Debug(TAG, "[LOGD]");
            if (getDistance(from: LoggingParams.GPS_TERM_WIND - 1, to: 0) < Double(LoggingParams.GPS_TERM_DIST_TOT)) //If did not move enough
            {
                //Attempt to find Stop Point
                let stop_point = findStop(starting: LoggingParams.GPS_TERM_WIND - 1);
                
                //Branch
                if (stop_point > LoggingParams.GPS_VEL_EN_NUM)
                {
                    //Store Journey
                    if (!flushAllAndStore(upTo: stop_point, with: Journey.TC_RE_NM)) { Debugger.Error(TAG, "Storage Error"); }
                    
                    //Attempt to locate Start Point
                    let start_pt = findStart(start: stop_point - 1);
                    if (start_pt > 0)
                    {
                        Debugger.Debug(TAG, " > [FIND]");
                        //Change State
                        next_state = GPSLogger.JOUR_FIND;
                        
                        //Prepare for Next State
                        flushStart(startFrom: start_pt);
                    }
                    else
                    {
                        Debugger.Debug(TAG, " > [SRCH]");
                        //Change State
                        next_state = GPSLogger.JOUR_SRCH;
                        
                        //Prepare for State
                        flushStart(startFrom: -1); //Go to search but retain Markov Chain State which may have a partial trigger
                    }
                }
                else //TODO Consider just remaining in this state if this is the case...
                {
                    //Store entire Journey
                    if (!flushAllAndStore(upTo: 0, with: Journey.TC_RE_NM)) { Debugger.Error(TAG, "Storage Error"); }
                    
                    //Update State
                    Debugger.Debug(TAG, " > [SRCH]");
                    next_state = GPSLogger.JOUR_SRCH;
                    
                    //Prepare for Next State
                    flushStart(startFrom: -1);
                    mStartSearch.refresh(old_window: mWindBuffer[0]);
                }
            }
            break;
            
        default:
            break;
        }
        
        //Update State
        mState = next_state;
    }
    
    //======================== Storage Control ========================//
    func flushStart(startFrom window : Int)
    {
        if (window > -1) { mStartPtr = ((window+1)*LoggingParams.GPS_DOWNSAMPLE - 1);  mStorePtr = mStartPtr; } //Since for the zeroth window, the first (oldest) point is point DOWNSAMPLE_RATE-1
        else             { mStorePtr = 0; mStartPtr = -1; }
    }
    
    func flushPending(rp : RoutePoint)
    {
        //First Store any point which may be lost...
        if ((mStartPtr == GPSLogger.PNTBUFFER_SIZE-1) && (mStorePtr == mStartPtr))
        {
            mJourney.add(point : mPointBuffer[mStartPtr]);
            mStorePtr -= 1;
        }
        
        //Now Add point to buffer
        mPointBuffer.add(point: rp);
        
        //Now update the start/store pointers
        if (mStartPtr > -1)
        {
            mStorePtr = min(mStorePtr + 1, GPSLogger.PNTBUFFER_SIZE - 1); //Update the Storage pointer (which is the next point to be stored if needed...
            mStartPtr = min(mStartPtr + 1, GPSLogger.PNTBUFFER_SIZE - 1);
        }
    }
    
    private func flushAllAndStore(upTo:Int, with termRsn:Int) -> Bool
    {
        //Modify end_window to point to correct raw point
        let end_point = max(upTo,0)*LoggingParams.GPS_DOWNSAMPLE + mDownSampler.Processed; //Calculation involves the fact that if we are at window 1, then the last point is point 3, first point is point 5 (or 6-1)
        
        //Store accordingly to buffer
        if ((mStorePtr <= mStartPtr) && (mStorePtr >= end_point)) //Copy only if store pointer is between start pointer and end pointer
        {
            //Copy all from store pointer up to end pointer. Now the End pointer is a window... but since the call to StoreEntire can happen
            //  from the GPS Timeout Handler, which is asynchronous, other samples may have gotten in since the last window update.
            //  Hence, the window boundary will have moved as well... to this end, we need to move the lower limit by the number of samples
            //  processed so far, which can be anywhere from 0 up to the DOWNSAMPLE_RATE-2.
            for i in (end_point...mStorePtr).reversed()
            {
                mJourney.add(point : mPointBuffer[i]);
            }
            
            //Clean up
            mStorePtr = max(upTo,0)*LoggingParams.GPS_DOWNSAMPLE - 1;
        }
        
        //Now Terminate Journey
        mJourney.terminate(with: termRsn);
        
        //Finally Write to File
        guard let fs = FileStream(file: Util.GetAppFile(withName: TrackingService.GPS_LOG_FILE).path, with: FileStream.APPENDABLE) else { Debugger.Error(TAG, "File Creation Fail"); return false; }
        let ret = fs.WriteSerialiseable(value: mJourney);
        
        //Now Clean Up
        fs.close();
        mJourney.flush();
        return ret;
    }
    
    //============================= Utilities =============================//
    /**
     * \brief Calculates the distance between the 'start' window and 'end' window
     * @param start Start Window to consider (inclusive)
     * @param end   End Window to consider (inclusive)
     * @return      Distance in metres...
     */
    private func getDistance(from start : Int, to end : Int) -> Double
    {
        return mWindBuffer[start].distanceTo(point: mWindBuffer[end]);
    }
    
    /**
     * \brief Calculates the time difference between two windows as indexed (both inclusive)
     * @param start Start Window to consider
     * @param end   End Window to consider
     * @return      The Time Differences (in milliseconds)
     */
    private func getTimeDiffer(from start : Int, to end : Int) -> Int
    {
        return mWindBuffer[end].TimeStamp - mWindBuffer[start].TimeStamp;
    }
    
    /**
     * \brief  Retrieve the window containing valid points
     * @return Start Window
     */
    private func getStartWindow() -> Int
    {
        return Int(floor((Double(mStartPtr) + 1.0)/Double(LoggingParams.GPS_DOWNSAMPLE)) - 1); //TODO this can be causing problems! need to verify that working ok...
    }
    
    /**
     * \brief  Attempts to identify the stopping point of the journey...
     * \detail This uses an internal Markov Chain Buffer and does not modify the actual start chain buffer
     * @param start The index from where to start searching
     * @return      The index of the last journey point which is valid...This points to the window before the end-trigger... -1 if none could be identified
     */
    private func findStop(starting from : Int) -> Int
    {
        let buf     = MarkovChain(LoggingParams.GPS_VEL_EN_TRIG, LoggingParams.GPS_VEL_EN_TOTAL, LoggingParams.GPS_VEL_EN_NUM, mWindBuffer[from]);
        var fndIdx  = -1 - LoggingParams.GPS_VEL_EN_NUM;    //By default, will return -1 when not found....
        
        for i in (1...from).reversed()
        {
            if (buf.CheckMaxTrigger(getDistance(from: i, to: i - 1), getTimeDiffer(from: i, to: i - 1), new_window: mWindBuffer[i - 1])) { fndIdx = i-1; }
        }
        
        return fndIdx+LoggingParams.GPS_VEL_EN_NUM; //We need to point to the window just before the stop trigger...
    }
    
    /**
     * \brief  Attempts to identify a potential Journey-Start trigger
     * \detail Note that this uses the actual member Markov Chain buffer, hence if at return time no full trigger was found, its state reflects the current MC state
     * @param start The start position from which to search (will search until the most recent)
     * @return      The index of the tentative start point or -1 if fails to find a valid one
     */
    private func findStart(start from:Int) -> Int
    {
        mStartSearch.refresh(old_window: mWindBuffer[from]);  //Flush buffer since we do not know the last time we used it...
        var fndIdx  = -1;                           //!< Indicates that we found a potential starting point...
        
        for i in (1...from).reversed()
        {
            if (mStartSearch.CheckMinTrigger(getDistance(from: i, to: i - 1), getTimeDiffer(from: i, to: i - 1), new_window: mWindBuffer[i - 1])) { fndIdx = i+2; break; }
        }
        
        return fndIdx;
    }
}
 

class AccelLogger
{
    //===================== STATE CONTROL =====================//
    private static let ALS_UNDEF = 0;  //!< Undefined State...
    private static let ALS_CHECK = 1;  //!< Quick Sample Size (3/4 accelerometer samples)
    private static let ALS_ENSUR = 2;  //!< Extra Sample Set (10-15 accelerometer samples)
    
    //public  static let RET_SLEEP = 0;  //!< Go Back to sleep
    public static let RET_ERR  = -1;  //!< Error!
    public static let RET_WAIT =  1;  //!< Wait for another sample
    public static let RET_PASS =  2;  //!< We passed the thresholds...
    
    private        let TAG = "ACC";
    
    //======================= VARIABLES =======================//
    private var mState      : Int;     //!< State Control
    private var mAverage    : [Double];   //!< Average Calculation (window)
    
    init()
    {
        Debugger.Debug(TAG, "Init")
        mState = AccelLogger.ALS_UNDEF;
        mAverage = [Double]();
    }
    
    func onStart() -> Bool
    {
        Debugger.Debug(TAG, "onStart");
        if (mState > AccelLogger.ALS_UNDEF) { Debugger.Warn(TAG, "Already Active"); return false; }
    
        mAverage.removeAll();
        mState = AccelLogger.ALS_CHECK;
    
        return true;
    }
    
    func onNewAcceleration(acc : CMAcceleration) -> Int
    {
        guard mState > AccelLogger.ALS_UNDEF else { return AccelLogger.RET_ERR; }
        
        //Calculate Magnitude and store in window...
        mAverage.append(sqrt(pow(acc.x, 2) + pow(acc.y, 2) + pow(acc.z, 2)));
        
        //If not enough filled in (identified by sample size respectively with state)
        if (mAverage.count < (mState == AccelLogger.ALS_CHECK ? LoggingParams.QUICK_SAMPLE_SIZE : LoggingParams.EXTRA_SAMPLE_SIZE)) { return AccelLogger.RET_WAIT; }
    
        //else, compute average
        var average = 0.0; for value in mAverage { average += pow(value - 1, 2); }
        Debugger.Debug(TAG, "Average: \(average)");
        
        //Branch based on state
        if (mState == AccelLogger.ALS_CHECK)
        {
            //Check if we passed the threshold and update state accordingly:
            mState = (average > LoggingParams.QUICK_SAMPLE_THRESH) ? AccelLogger.ALS_ENSUR : AccelLogger.ALS_CHECK;
            
            //In any case, flush and return wait...
            mAverage.removeAll();
            return AccelLogger.RET_WAIT;
        }
        else
        {
            //In any case, state will go back to ALS_CHECK...
            mState = AccelLogger.ALS_CHECK;
            mAverage.removeAll();
            
            //Return Pass if we passed the threshold
            return (average > LoggingParams.EXTRA_SAMPLE_THRESH) ? AccelLogger.RET_PASS : AccelLogger.RET_WAIT;
        }
    }
    
    func onStop()
    {
        mState = AccelLogger.ALS_UNDEF;
        mAverage.removeAll();
    }
}
