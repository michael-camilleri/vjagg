//
//  WindowBuffer.swift
//  vjagg
//
//  Created by Michael Camilleri on 04/01/2017.
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
import Foundation

class WindowBuffer
{
    //!< Member Constants
    let BUFFER_SIZE : Int;
    
    //!< Member Variables
    private var mWindow : [RoutePoint];   //!< The actual circular buffer
    private var mWindNxt: Int;            //!< Buffer Start index: this points to the next value to fill
    private var mWindFl : Int;            //!< Indicates whether the buffer is filled (or rather how many are filled)
    
    subscript(index: Int) -> RoutePoint { return mWindow[(mWindNxt + index + 1) % BUFFER_SIZE]; }
    
    init(with size: Int)
    {
        BUFFER_SIZE = size;
        
        //Now Deal with actual Window
        mWindow  = [RoutePoint](); for _ in 0..<BUFFER_SIZE { mWindow.append(RoutePoint()); }//Recreate Window
        mWindNxt = BUFFER_SIZE - 1; //Start off at the maximum
        mWindFl  = 0;

    }
    
    /**
     * \brief Add a Route Point
     * \detail The function adds a new route-point to the buffer (circling as necessary). It enforces copying of the routepoint through using the copy function
     * @param rp
     */
    func add(point : RoutePoint)
    {
        //Add the point (and wrap)
        mWindow[mWindNxt].from(point);
        mWindNxt -= 1; if (mWindNxt < 0) { mWindNxt = BUFFER_SIZE-1; }
        mWindFl   = min(mWindFl + 1, BUFFER_SIZE);
    }

    
    /**
     * Accessor for the mFilled Flag
     */
    func getFilledFirst(numOf points: Int) -> Bool
    {
        return mWindFl >= points;
    }
    
    /**
     * \brief Clear (flush) the buffer
     */
    func flush()
    {
        //Deal with Window first
        mWindow  = [RoutePoint](); for _ in 0..<BUFFER_SIZE { mWindow.append(RoutePoint()); }//Recreate Window
        mWindNxt = BUFFER_SIZE - 1; //Start off at the maximum
        mWindFl  = 0;
    }
}
