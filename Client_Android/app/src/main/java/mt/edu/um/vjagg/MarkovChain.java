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

import android.location.Location;

/**
 * Created by michael on 22/02/2016.
 * A Markov Chain Type
 *
 * [Converted to primitive types]
 */
class MarkovChain
{
    //!< Member Variables
    private final float  mVeloTresh;  //!<Velocity Treshold
    private final float  mCumuTresh;  //!<Cumulative Velocity Treshold
    private final int    mWindSize;  //!< The window size (i.e. the number of successive velocities which must pass threshold)

    ////Core Buffer Variables
    private WindowBuffer mBuffer;   //!< Buffer for the actual window positions

    ////Statistics
    private int          mNumSucVel;    //!< The number of successive velocities which match

    //!< Public Constructor
    MarkovChain(float vel, float cumu, int size, RoutePoint old_window)
    {
        mVeloTresh = vel;
        mCumuTresh = cumu;
        mWindSize = size;
        flush();

        //Start off with the old window
        mBuffer.AddPoint(old_window.copy());
    }

    void Refresh(RoutePoint old_window)
    {
        flush();
        mBuffer.AddPoint(old_window.copy());
    }

    /**
     * \brief Add a new distance and check against the minimum treshold(s)
     * @param dist  The Distance value to check
     * @param diff  The Time difference, used to calculate velocity
     * @param new_window The window which is being checked for the trigger...
     * @return      True if the Markov Chain indicates that we passed all tresholds, false otherwise
     */
    boolean CheckMinTrigger(float dist, long diff, RoutePoint new_window)
    {
        //Branch on whether we satisfy value
        if (dist/diff > mVeloTresh)
        {
            mNumSucVel = Math.min(mNumSucVel + 1, mWindSize);   //Update the number of successive velocities passing the treshold
        }
        else
        {
            flush();    //Restart anew

        }

        //In any case, add this window... since even if we flush, this may be the first of a set of valid windows...
        mBuffer.AddPoint(new_window.copy());

        //Return indication of whether we succeed
        return (mNumSucVel >= mWindSize) && (getCumulativeVelocity() > mCumuTresh);
    }

    Boolean CheckMaxTrigger(float dist, long diff, RoutePoint new_window)
    {
        LogView.Debug("MC", "Look 4 Stp " + dist + " " + diff);
        //Branch on whether we satisfy value
        if (dist/diff < mVeloTresh)
        {
            mNumSucVel = Math.min(mNumSucVel + 1, mWindSize);   //Update the number of successive velocities passing the treshold (below)
            LogView.Debug("MC", "inc");
        }
        else
        {
            LogView.Debug("MC", "fl");
            flush();    //Restart anew
        }

        //In any case, add this window... since even if we flush, this may be the first of a set of valid windows...
        mBuffer.AddPoint(new_window.copy());

        //Return indication of whether we succeed
        return (mNumSucVel >= mWindSize) && (getCumulativeVelocity() < mCumuTresh);
    }

    /**
     * \brief Clear (flush) the buffer
     */
    private void flush()
    {
        //Next the buffer:
        mBuffer = new WindowBuffer(mWindSize+1);    //Size must hold one more than the window size

        //Create Total
        mNumSucVel = 0;
    }

    /**
     * \brief Helper function to compute the distance between two extreme points
     */
    private float getCumulativeVelocity() //TODO consider using bounds instead of just velocity!
    {
        float dist[] = new float[1];

        //Calculate Distance: Note that we use mWindSize, not mWindSize-1 because indeed, mBuffer is of size mWindSize+1
        Location.distanceBetween(mBuffer.getPoint(mWindSize).mLatitude, mBuffer.getPoint(mWindSize).mLongitude, mBuffer.getPoint(0).mLatitude, mBuffer.getPoint(0).mLongitude, dist);

        //Calculate Time Difference
        long time_dif = mBuffer.getPoint(0).mTimeSt - mBuffer.getPoint(mWindSize).mTimeSt;

        return dist[0]/time_dif;
    }
}
