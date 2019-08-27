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

import android.content.Context;
import android.location.Location;

import com.google.android.gms.maps.model.LatLngBounds;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.InvalidParameterException;
import java.util.ArrayList;

/**
 * Created by Michael Camilleri on 28/03/2016.
 *
 * Serves to post-process the routes after the user presses Stop Logging
 */
public class RoutePostProcessor
{
    public static final int      AVERAGE_SIZE = 6;                   //!< Averaging size for velocity calculations

    private ArrayList<Journey> mJourneys;   //!< List of Journeys

    public static final String TAG = "RPP";

    /**
     * \brief Loads the Journeys logged by the route-logger
     * TODO Revise Return Type!
     * @return
     */
    public Exception LoadLoggedJourneys(Context c)
    {
        LogView.Debug(TAG, "LoadLog");
        try
        {
            BufferedInputStream gps_reader  = new BufferedInputStream(new FileInputStream(new File(c.getFilesDir(), TrackingService.GPS_LOG_FILE))); //TODO change
            mJourneys                       = new ArrayList<>();  //create a list

            while (true)    //Will only exit once an exception is thrown or we break out
            {
                //Load Journey
                Journey tmp_journey = new Journey();
                Exception result = tmp_journey.loadRoute(gps_reader);

                //Handle Result
                if (result == null)                                   { LogView.Debug(TAG, "Found"); mJourneys.add(tmp_journey); }
                else if (result instanceof EOFException)              { LogView.Debug(TAG, "EOF"); break; }
                else if (result instanceof InvalidParameterException) { continue; }
                else                                                  { throw result; }
            }
        }
        catch (FileNotFoundException f)
        {
            LogView.Warn(TAG, "No Jour"); //TODO revise return types...
            return f;
        }
        catch (Exception e)
        {
            return e;
        }

        return null;
    }

//    public void LoadLoggedJourneys(ArrayList<Journey> journeys)
//    {
//        mJourneys = journeys;
//    }


    /**
     * \brief Save the Journeys to the Post-Processed file (in append mode)
     * @param c Context (for retrieving files directory)
     * @return
     */
    public Exception SaveJourneys(Context c)
    {
        LogView.Debug(TAG, "Save");

        //NowStore all journeys still active...
        for(Journey journey : mJourneys)
        {
            Exception result = journey.storeRoute(Journey.TC_UNSPEC, (new File(c.getFilesDir(), TrackingService.GPS_RPP_FILE)));
            if (result != null) { return result; }
        }

        //If successful, clean up the other file and return
        (new File(c.getFilesDir(), TrackingService.GPS_LOG_FILE)).delete(); //TODO change
        return null;
    }

    /**
     * \brief Trims the journey start/end points for velocities exceeding a certain speed...
     * @param num   : Maximum number of points to trim (to avoid trimming the entire journey)
     * @param vel_threshold : Velocity Threshold to trim above
     */
    void TrimEnds(int num, float vel_threshold)
    {
        LogView.Debug(TAG, "TrimEnds");
        for (int i=0; i<mJourneys.size(); ++i)
        {
            LogView.Debug(TAG, "Jour "+Integer.toString(i));
            int k=0; //Need to keep track of two indices: one is the actual index into the array (k): the other is for the maximum number of counts to remove (j)...

            //Start by Trimming the Start-Point(s)
            for (int j=0; j<num; j++, k++)
            {
                if (EstimateVelocity(mJourneys.get(i).getPt(k), mJourneys.get(i).getPt(k+1)) > vel_threshold) { LogView.Debug(TAG, "Trim " + Integer.toString(k)); mJourneys.get(i).RemovePt(k); k--; } //Need to decrement k since indices will be invalidated
            }

            //Now Trim end-points
            int jour_length = mJourneys.get(i).getNumPoints(); //Keep track of this since it will change
            k = jour_length - 1;
            for (int j=0; j < num; j++, k--)
            {
                if (EstimateVelocity(mJourneys.get(i).getPt(k-1), mJourneys.get(i).getPt(k)) > vel_threshold) { LogView.Debug(TAG, "Trim " + Integer.toString(k)); mJourneys.get(i).RemovePt(k); }
            }
        }
    }

    /**
     * \brief Removes journeys from the list whose bounds are less than the distance threshold specified.
     * @param dist_threshold
     */
    void ThresholdDistance(float dist_threshold)
    {
        LogView.Debug(TAG, "ThresDist");
        for (int i = mJourneys.size()-1; i >= 0; i--)  //Start from the last element to avoid invalidating indices
        {
            //Calculate Distance between bounds
            float[]      dist = new float[1];
            LatLngBounds bounds = mJourneys.get(i).getBounds();
            Location.distanceBetween(bounds.northeast.latitude, bounds.northeast.longitude, bounds.southwest.latitude, bounds.southwest.longitude, dist);

            //If it does not pass threshold, then remove
            if (dist[0] < dist_threshold) { LogView.Debug(TAG, "Rem " + Integer.toString(i)); mJourneys.remove(i); }
        }
    }

    /**
     * \brief Removes Journeys based on time duration
     * @param time_threshold
     */
    /*public void ThresholdTime(long time_threshold)
    {
        for (int i = mJourneys.size()-1; i >= 0; i--)  //Start from the last element to avoid invalidating indices
        {
            //Calculate Time difference
            RoutePoint startPt = mJourneys.get(i).getPt(0);
            RoutePoint endPt   = mJourneys.get(i).getEnd();

            //If it does not pass threshold, then remove
            if ((endPt.mTimeSt - startPt.mTimeSt) < time_threshold) { mJourneys.remove(i); }
        }
    }*/

    /**
     * \brief Joins successive journeys if the time difference between them is within the threshold
     *          and the distance is within a velocity factor
     * TODO consider making the check based on bearing as well...
     */
    void JoinJourneys(long time_threshold, float vel_factor)
    {
        LogView.Debug(TAG, "Join");
        if (mJourneys.size() > 1)   //IF 2 or more
        {
            for (int i = mJourneys.size() - 2; i >= 0; i--)  //Start from the next to last element
            {
                LogView.Debug(TAG, "Jour " + Integer.toString(i) + " - " + Integer.toString(i+1));
                //Common Vars
                float  dist[] = new float[1];
                float  time_dif = 0.0f;
                RoutePoint startPt, endPt;

                //TODO ?include check on the type of journey end...?

                //First check that the difference in time indeed is not too excessive...
                if (Math.abs((mJourneys.get(i+1).getPt(0).mTimeSt) - (mJourneys.get(i).getEnd().mTimeSt)) > time_threshold) { LogView.Debug(TAG, ">Time"); continue; }

                //Now compute the velocity between the last points in the first journey
                double end_velocity = 0.0;
                for (int j=mJourneys.get(i).getNumPoints()-AVERAGE_SIZE; j<mJourneys.get(i).getNumPoints(); j++)
                {
                    end_velocity += EstimateVelocity(mJourneys.get(i).getPt(j-1), mJourneys.get(i).getPt(j));
                }
                end_velocity /= AVERAGE_SIZE; //Divide by number of points to find average

                //Now find velocity between last point and first point of next journey
                startPt = mJourneys.get(i).getEnd();
                endPt = mJourneys.get(i+1).getPt(0);
                Location.distanceBetween(startPt.mLatitude, startPt.mLongitude, endPt.mLatitude, endPt.mLongitude, dist);
                time_dif = endPt.mTimeSt - startPt.mTimeSt;

                //Check that within velocity tolerances
                if ((dist[0]/time_dif) > vel_factor*end_velocity) { LogView.Debug(TAG, ">Velo"); continue; }  //Did not pass tolerance threshold

                //Else, join journeys & remove second one...
                mJourneys.get(i).appendJourney(mJourneys.get(i+1));
                mJourneys.remove(i + 1);
                LogView.Debug(TAG, "JoinOK");
            }
        }
    }

    /**
     * \brief Helper function for estimating the velocity between two points
     * @param start
     * @param end
     * @return
     */
    private float EstimateVelocity(RoutePoint start, RoutePoint end)
    {
        float[] dist = new float[1];
        Location.distanceBetween(start.mLatitude, start.mLongitude, end.mLatitude, end.mLongitude, dist);

        return (dist[0])/(end.mTimeSt - start.mTimeSt);
    }

}
