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
 * Created by Michael Camilleri on 15/03/2016.
 *
 * [Converted to using primitives]
 */
public class WindowBuffer
{
    //!< Member Constants
    public final int     BUFFER_SIZE;

    //!< Member Variables
    private RoutePoint[] mWindow;   //!< The actual circular buffer
    private int          mWindNxt;  //!< Buffer Start index: this points to the next value to fill
    private int          mWindFl;   //!< Indicates whether the buffer is filled (or rather how many are filled)

    public WindowBuffer(int size)
    {
        BUFFER_SIZE = size;
        flush();
    }

    /**
     * \brief Add a Route Point
     * \detail The function adds a new route-point to the buffer (circling as necessary)
     * @param rp
     */
    public void AddPoint(RoutePoint rp)
    {
        //Add the point (and wrap)
        mWindow[mWindNxt] = rp;
        mWindNxt--; if (mWindNxt < 0) { mWindNxt = BUFFER_SIZE-1; }
        mWindFl = Math.min(mWindFl + 1, BUFFER_SIZE);
    }

    /**
     * Function automatically converts indices to the underlying structure
     * @param index
     * @return
     */
    public RoutePoint getPoint(int index)
    {
        return mWindow[(mWindNxt + index + 1) % BUFFER_SIZE];
    }

    /**
     * Accessor for the mFilled Flag
     */
    public boolean getFilledFirst(int num)
    {
        return mWindFl >= num;
    }

    /**
     * \brief Clear (flush) the buffer
     */
    public void flush()
    {
        //Deal with Window first
        mWindow = new RoutePoint[BUFFER_SIZE];  //Recreate Window
        for (int i=0; i<BUFFER_SIZE; i++) { mWindow[i] = new RoutePoint(); }
        mWindNxt = BUFFER_SIZE - 1; //Start off at the maximum
        mWindFl  = 0;
    }
}
