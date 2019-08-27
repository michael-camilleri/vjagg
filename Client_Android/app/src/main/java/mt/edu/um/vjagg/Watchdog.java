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

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import java.util.HashMap;

/**
 * Created by michael on 18/02/2016.
 * Implements a watchdog timer, for the same thread in which it exists!
 *
 * Issues:
 *  > This version of the Watchdog timer suffers from an issue with Deep Sleep. Basically, since it uses handlers, which are based on the UpTimeMillis() value,
 *    the time stops counting in deep sleep! This must be factored in the usage of the timer...
 *    This should not be a problem if the watchdog is used:
 *    1) Within an activity, since in any case, the screen would be on and hence not in deep sleep!
 *    2) If something else is keeping CPU awake... for example during GPS logging!
 *
 * [Converted to using primitives]
 */
public class Watchdog extends Handler
{
    //!< Member classes/Interfaces
    public interface TimeOut
    {
        /**
         * \brief Must be implemented by any class wishing to use the time-out functionality of the watchdog
         */
        void HandleTimeOut(int wd);
    }

    private class WDTimer
    {
        public long    mTimeOut;    //!< The specified timeout
        public TimeOut mObject;     //!< The object which requests callback
        public boolean mActive;     //!< Indicates whether it is active

        public WDTimer (long timeout, TimeOut obj)
        {
            mTimeOut = timeout;
            mObject  = obj;
            mActive  = false;
        }
    }

    //!< Member Variables
    private HashMap<Integer, WDTimer> msgRegistry;
    private final int mIdxRange;

    //============= Initialisers ================//
    public Watchdog(int start_idx)
    {
        mIdxRange = start_idx;
        msgRegistry = new HashMap<>(3, 1); //Typically won't need to be larger than this
    }

    /**
     * \brief Register a new callback by a particular class
     * @param obj       Reference to the object used: must implement TimeOut interface
     * @param timeout   The time for triggering the watchdog
     * @return          If successful, returns the unique identifier: if not, returns -1
     */
    public int RegisterWatchdog(TimeOut obj, long timeout)
    {
        int cmd = Utilities.GenerateUnique(msgRegistry.keySet(), mIdxRange);   //Generate Unique identifier
        msgRegistry.put(cmd, new WDTimer(timeout, obj));
        return cmd;
    }

    /**
     * \brief Starts the watchdog associated with a particular code
     * @param code The code associated with the watchdog: this is returned by the RegisterWatchdog Function
     * @return     True on success, false on failure (for example WD with code does not exist)
     */
    public boolean StartWatchdog(int code)
    {
        if (msgRegistry.containsKey(code) && !(msgRegistry.get(code).mActive)) //If exists and inactive
        {
            msgRegistry.get(code).mActive = true;
            sendEmptyMessageAtTime(code, SystemClock.uptimeMillis() + msgRegistry.get(code).mTimeOut);
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * \brief Ping to the Watchdog that still alive
     * @param code  The code associated with the watchdog
     * @return
     */
    public boolean PingOK(int code)
    {
        if (msgRegistry.containsKey(code) && (msgRegistry.get(code).mActive)) //If exists and active
        {
            removeMessages(code);   //Remove the current message
            sendEmptyMessageAtTime(code, SystemClock.uptimeMillis() + msgRegistry.get(code).mTimeOut);
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * \brief Ping to the Watchdog that still alive
     * @param code      The code associated with the watchdog
     * @param timeout   The specific timeout to use for this case...
     * @return
     */
    public boolean PingOK(int code, long timeout)
    {
        if (msgRegistry.containsKey(code) && (msgRegistry.get(code).mActive)) //If exists and active
        {
            removeMessages(code);   //Remove the current message
            sendEmptyMessageAtTime(code, SystemClock.uptimeMillis() + timeout);
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * \brief Pauses the watchdog associated with a code: the watchdog remains available and can be restarted using StartWatchdog()
     * @param code  The code identifier
     * @return      Indication of success or failure
     */
    public boolean PauseWatchdog(int code)
    {
        if (msgRegistry.containsKey(code) && (msgRegistry.get(code).mActive)) //If exists and active
        {
            removeMessages(code);   //Remove the current message
            msgRegistry.get(code).mActive = false;
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * \brief Kill the watchdog associated with the code and clean resources associated with it...
     * @param code  The code identifier
     * @return      Indication of success/failure
     */
    public boolean KillWatchdog(int code)
    {
        if (msgRegistry.containsKey(code)) //If exists
        {
            removeMessages(code);       //Remove any messages pertaining to this code
            msgRegistry.remove(code);   //Remove the watchdog associated with the code
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * \brief Handle a timeout message
     * @param msg Message indicating the timer which timed out...
     */
    @Override
    public void handleMessage (Message msg)
    {
        if (msgRegistry.get(msg.what).mActive)
        {
            sendEmptyMessageAtTime(msg.what, SystemClock.uptimeMillis() + msgRegistry.get(msg.what).mTimeOut);
            msgRegistry.get(msg.what).mObject.HandleTimeOut(msg.what); //Call the respective function (after sending message, so if need be we can pause or kill etc..)
        }
    }
}
