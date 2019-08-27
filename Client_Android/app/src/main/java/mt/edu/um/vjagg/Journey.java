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

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * Created by Michael Camilleri on 03/02/2016.
 *
 * Encapsulates a Journey object, and handles all serialisation and display operations required
 *
 * [Converted to primitive datatypes]
 */
public class Journey
{
    //!< Member Constants
    public static final int BUFFER_CAPACITY = 2000;   //!< Initial capacity of the buffer

    //!< Flags
    protected static final int TC_UNSPEC = 0;    //Unspecified
    protected static final int TC_RT_CN = 1;    //Route Continues
    protected static final int TC_RE_NM = 2;    //Termination Code: Route Ends because there was no significant motion
    protected static final int TC_RE_SL = 3;    //Termination Code: Route Ends because there was an extended loss of signal...
    protected static final int TC_RE_SF = 4;    //Termination Code: Route Ends because there was an extended loss of signal but a tentative termination point was found...
    protected static final int TC_RE_US = 5;    //Termination Code: Route ended by the user (through the destroy function)
    protected static final int TC_RE_OS = 6;    //Termination Code: Route ended by the OS or an error

    protected static final int   MD_UNSPEC     = 0;   //!< Unspecified
    public static final String[] MODE_SELECT_STR = {"Car Driver", "Car Passenger", "Bus (Public Transport)", "Coach/Minibus", "Motorbike", "On Foot", "Bicycle", "Ferry", "Taxi", "Other"};
    public static final int[]    MODE_SELECT_VAL = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512};

    protected static final int   TP_UNSPEC = 0;  //!< Unspecified
    public static final String[] PURP_SELECT_STR = {"Undisclosed", "Going Home", "Visiting Someone", "Going to Work", "Work-Related", "Medical", "Personal Errand", "Shopping", "Education", "Accompanying", "Sports", "Other"};
    public static final int[]    PURP_SELECT_VAL = {0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024 };

    //!< Member Variables
    private ArrayList<RoutePoint> mBuffer;  //!< Stores the route buffer - this is used both during journey buildup and storage
    private int  mTermRsn;                  //!< Termination Reason for the journey
    private long mIdent;                    //!< The unique Identifier of a Journey - basically, this is currently set to the creation time of the object
    private int  mMode;                     //!< Mode of transport
    private int  mPurpose;                  //!< Purpose of Journey

    //!< Visualisation Variables
    private LatLngBounds mBounds;    //!< Journey Bounds

    //!< Debugging
    public static final String TAG = "J";

    /**
     * Public Initialiser Constructor
     */
    public Journey() {
        flush();
    }

    ////============================ Buffer Access Functions ===========================////

    /**
     * Add a journey point to the current buffer...
     *
     * @param point
     * @return
     */
    public boolean addPoint(RoutePoint point) {
        if (point.mTimeSt > 0) {
            return mBuffer.add(point);
        }     //Just in case to avoid storing 0 values...
        else {
            return false;
        }
    }

    /**
     * \brief Appends the points of the specified journey, and also sets the end reason to be that of the second journey (all other values retained)
     *
     * @param journey
     * @return
     */
    public boolean appendJourney(Journey journey) {
        LogView.Debug(TAG, "append");
        //Deal with the Termination Condition first
        int otr_reason = journey.mTermRsn;
        while (otr_reason > 0)          //Shift to the left to accomodate new journey
        {
            otr_reason /= 8;
            mTermRsn *= 8;
        }
        mTermRsn += journey.mTermRsn;   //Add the new journeys' termination reason

        //Then append the journey
        for (RoutePoint rp : journey.mBuffer) {
            if (!this.mBuffer.add(rp)) {
                return false;
            }
        }

        //Finally recalculate Bounds
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (RoutePoint rp : this.mBuffer) {
            bounds.include(new LatLng(rp.mLatitude, rp.mLongitude));
        }
        mBounds = bounds.build();

        return true;
    }

    /**
     * Returns a specific point in the route
     *
     * @param index The index to retrieve
     * @return
     */
    public RoutePoint getPt(int index) {
        return mBuffer.get(index);
    }

    /**
     * \brief   Returns the Route End Point
     *
     * @return The Route End Point
     */
    public RoutePoint getEnd() {
        return mBuffer.get(mBuffer.size() - 1);
    }

    public RoutePoint RemovePt(int index) {
        return mBuffer.remove(index);
    }

    /**
     * \brief   Flush the buffer, effectively creating a new journey
     */
    public void flush()
    {
        LogView.Debug(TAG, "flush");
        mBuffer = new ArrayList<>(BUFFER_CAPACITY);
        mTermRsn = TC_UNSPEC;
        mIdent = System.currentTimeMillis();

        mMode    = MD_UNSPEC;
        mPurpose = TP_UNSPEC;
    }

    ////============================ Buffer Serialization Functions ===========================////

    /**
     * Ends the journey (being buffered) and writes it to disk. The append operation is performed...
     *
     * @param reason The reason for termination
     * @return Null on success, exception on failure
     */
    public Exception storeRoute(int reason, File file)
    {
        LogView.Debug(TAG, "store");
        try
        {
            DataOutputStream strm = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file, true)));

            if (mBuffer.size() < 1)
            {
                LogView.Error(TAG, "Size=0");
                return null;    //Do not store
            }

            //Write the Header first
            strm.writeLong(mIdent); //The unique identifier
            strm.writeInt(mBuffer.size());
            if (reason != TC_UNSPEC)
            {
                mTermRsn = reason;
            }
            strm.writeInt(mTermRsn);
            strm.writeInt(mMode);    //Write the mode of travel
            strm.writeInt(mPurpose); //Write the purpose for travel

            //Now write in the data
            for (int i = 0; i < mBuffer.size(); ++i)
            {
                mBuffer.get(i).serialize(strm);
            }

            //Clean up
            strm.flush();
            strm.close();
        }
        catch (Exception e)
        {
            return e;
        }
        return null;
    }

    /**
     * Loads the route from file
     *
     * @param reader The reason the reader is used is to allow storage of multiple journeys in same file, and hence reader is required...
     * @return
     */
    public Exception loadRoute(BufferedInputStream reader)
    {
        LogView.Debug(TAG, "load");
        try
        {
            DataInputStream strm = new DataInputStream(reader);
            int len;

            //Read in the header
            mIdent = strm.readLong();  //Read in the journey identifier
            len = strm.readInt();   //Read in length
            mTermRsn = strm.readInt();   //Read in the termination reason
            mMode = strm.readInt();
            mPurpose = strm.readInt();

            //Read in the data (and also build bounds for later use)
            mBuffer = new ArrayList<>(len);
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (int i = 0; i < len; ++i)
            {
                mBuffer.add(new RoutePoint(strm)); //Add point
                bounds.include(new LatLng(mBuffer.get(i).mLatitude, mBuffer.get(i).mLongitude));
            }

            mBounds = bounds.build();
        }
        catch (Exception e)
        {
            return e;
        }

        return null;
    }


    ////============================ Buffer Display Functions ===========================////

    /**
     * \brief   Return the Bounds of the journey
     *
     * @return A LatLngBounds object
     */
    LatLngBounds getBounds()
    {
        return mBounds;
    }

    PolylineOptions getPolyLine(int width, int clr)
    {
        PolylineOptions options = new PolylineOptions();
        for (RoutePoint rp : mBuffer)
        {
            options.add(new LatLng(rp.mLatitude, rp.mLongitude));
        }
        options.width(width).color(clr);

        return options;
    }

    public String getTitle() {
        if (mBuffer.size() < 1) {
            return "Invalid Journey";
        } else {
            return Utilities.ConvertDate(mBuffer.get(0).mTimeSt);
        }
    }

    public String getTimes() {
        if (mBuffer.size() < 1) {
            return "Invalid Journey";
        } else {
            return "[" + Utilities.ConvertTime(mBuffer.get(0).mTimeSt) + "] - ["
                    + Utilities.ConvertTime(mBuffer.get(mBuffer.size() - 1).mTimeSt)
                    + "]";
        }
    }

    public String getStrMode()
    {
        if (mMode == MD_UNSPEC) { return "Mode (Undisclosed)"; }
        else
        {
            StringBuilder sb = new StringBuilder();
            boolean       one_mode = false;
            for (int i = 0; i< MODE_SELECT_STR.length; ++i)
            {
                if ((mMode & MODE_SELECT_VAL[i]) > 0)
                {
                    if (one_mode) { sb.append(",..."); break; }
                    else          { sb.append(MODE_SELECT_STR[i]); one_mode = true; }
                }
            }
            return  sb.toString();
        }
    }

    public boolean[] getValMode()
    {
        boolean[] mode = new boolean[MODE_SELECT_VAL.length];
        for (int i=0; i<MODE_SELECT_VAL.length; ++i) { mode[i] = (mMode & MODE_SELECT_VAL[i]) > 0; }
        return mode;
    }

    public String getStrPurp()
    {
        if (mPurpose == TP_UNSPEC) { return "Purpose (Undisclosed)"; }
        else
        {
            for (int i = 1; i< PURP_SELECT_VAL.length; ++i)
            {
                if ((mPurpose & PURP_SELECT_VAL[i]) > 0)
                {
                    return PURP_SELECT_STR[i];
                }
            }
            return "Purpose (Error)";
        }
    }

    public int getValPurp()
    {
        if (mPurpose == TP_UNSPEC) { return 0; }
        else
        {
            for (int i = 1; i< PURP_SELECT_VAL.length; ++i)
            {
                if ((mPurpose & PURP_SELECT_VAL[i]) > 0)
                {
                    return i;
                }
            }
            return -1;  //Error
        }
    }

    //// ========================== Accessor Functions =======================////

    /**
     * \brief Set the Purpose
     * @param purpose
     */
    public void setPurpose(int purpose)
    {
        if (purpose == 0) { mPurpose = 0; }
        else              { mPurpose = (int)Math.pow(2, purpose-1); }
    }

    /**
     * \brief Set the Transport Mode
     * @param mode
     */
    public void setMode(boolean[] mode)
    {
        mMode = 0;
        for (int i=0; i<mode.length; ++i) { mMode += MODE_SELECT_VAL[i]*(mode[i] ? 1 : 0); }
    }

//    public void setTermReason(int reason)
//    {
//        mTermRsn = reason;
//    }

    /**
     * \brief Returns the unique identifier of this journey
     * @return  String representation of the unique identifier
     */
    public String getIdentifier()
    {
        return Long.toString(mIdent);
    }

    public long getLngIdent() { return mIdent; }

    /**
     * \brief   Returns the Header Information associated with the journey
     * @return  String representation of the [Length] [Termination Code] [Transport Mode] [Trip Purpose]
     */
    public String getTripHeader()
    {
        return Integer.toString(mMode) + " " + Integer.toString(mPurpose) + " " + Integer.toString(mTermRsn) + " " + Integer.toString(mBuffer.size());
    }

    /**
     * \brief Return the buffer size
     * @return
     */
    public int getNumPoints()
    {
        return mBuffer.size();
    }

    /**
     * \brief Returns the Journey Points
     * @param  start T he start index to retrieve:
     * @param  length The number of points to retrieve
     * @return The Journey Points (separated by spaces) in the format: [Time] [Latitude] [Longitude] The end of journey will be a single negative number (-1)
     */
    public String getTripPoints(int start, int length)
    {
        if (mBuffer.size()< 1) { return "Invalid Journey"; }
        else
        {
            String output_string = new String("");  //Start with Empty String

            for (int i=start; i<start+length; ++i)
            {
                output_string += Long.toString(mBuffer.get(i).mTimeSt) + " " + Double.toString(mBuffer.get(i).mLatitude) + " " + Double.toString(mBuffer.get(i).mLongitude) + " ";
            }
            return output_string;
        }
    }
}
