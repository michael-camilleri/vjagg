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

/**
 *
 * Performs a Downsampling operation.
 *
 * [Converted to using primitives]
 */
public class DownSampler
{
    /**
     * \brief Interface for Handling Down-Sampled Values
     */
    public interface DownSampleHandler
    {
        /**
         * \brief Must be implemented by a class wishing to use the DownSample Functionality
         * @param rp The Routepoint which is the averaged value passed to the downsampler
         */
        void OnDownSample(RoutePoint rp);
    }

    //!< Member Constants
    public final int DOWNSAMPLING_RATE; //!< The rate of downsampling: a 1 indicates no downsampling, 2 implies taking average over two samples etc...

    //!< Member Variables
    private RoutePoint[]        mWindow;    //!< The actual circular buffer
    private int                 mFilled;    //!< How many are actually filled
    private DownSampleHandler   mHandler;   //!< Reference to the downsampler

    /**
     * \brief Constructor
     * @param rate      The rate of downsampling
     * @param handler   Reference to the handler implementing DownSampleHandler
     */
    public DownSampler(int rate, DownSampleHandler handler)
    {
        DOWNSAMPLING_RATE = rate;
        mHandler          = handler;
        flush();
    }

    /**
     * \brief Add a Route Point
     * \detail The function adds a new route-point to the downsampler: once specified amount reached, then call downsampler handler
     * @param rp The RoutePoint to add
     */
    public void AddPoint(RoutePoint rp)
    {
        //Add the point
        mWindow[mFilled++] = rp;

        //If specified amount filled
        if (mFilled == DOWNSAMPLING_RATE)
        {
            //Calculate Average
            RoutePoint average = new RoutePoint();
            for (RoutePoint pt : mWindow) { average.Add(pt); }
            average.MultEq(1.0/mFilled);

            //Reset Filled Flag
            mFilled = 0;

            //Call Handler
            mHandler.OnDownSample(average);
        }
    }

    /**
     * \brief Returns the number of samples processed so far in this window
     * @return Number of samples
     */
    public int GetProcessed()
    {
        return mFilled;
    }

    /**
     * \brief Clear (flush) the buffer
     */
    public void flush()
    {
        LogView.Debug("DS", "Flush");
        //Deal with Window first
        mWindow = new RoutePoint[DOWNSAMPLING_RATE];  //Recreate Window
        for (int i=0; i<DOWNSAMPLING_RATE; i++) { mWindow[i] = new RoutePoint(); }
        mFilled = 0;
    }

}
