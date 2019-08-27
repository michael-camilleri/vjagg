//
//  MarkovChain.swift
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

class MarkovChain
{
    //!< Member Variables
    private let mVeloTresh : Double;  //!<Velocity Treshold
    private let mCumuTresh : Double;  //!<Cumulative Velocity Treshold
    private let mWindSize  : Int;     //!<The window size (i.e. the number of successive velocities which must pass threshold)
    
    ////Core Buffer Variables
    private var mBuffer     : WindowBuffer; //!< Buffer for the actual window positions
    private var mNumSucVel  : Int;          //!< The number of successive velocities which match
    
    private let TAG = "MC";
    
    //!< Public Constructor
    init(_ vthres : Double, _ cthres : Double, _ size : Int, _ old_window : RoutePoint?)
    {
        mVeloTresh = vthres;
        mCumuTresh = cthres;
        mWindSize = size;
        
        //Next the buffer:
        mBuffer = WindowBuffer(with: mWindSize+1);    //Size must hold one more than the window size
        
        //Create Total
        mNumSucVel = 0;
        
        //Start off with the old window (if passed)
        if let ow = old_window { mBuffer.add(point: ow); }
    }

    func refresh(old_window : RoutePoint)
    {
        flush();
        mBuffer.add(point : old_window);
    }
    
    /**
     * \brief Add a new distance and check against the minimum treshold(s)
     * @param dist  The Distance value to check
     * @param diff  The Time difference, used to calculate velocity
     * @param new_window The window which is being checked for the trigger...
     * @return      True if the Markov Chain indicates that we passed all tresholds, false otherwise
     */
    func CheckMinTrigger(_ dist : Double, _ time_diff : Int, new_window : RoutePoint ) -> Bool
    {
        Debugger.Debug(TAG, "\(dist) \(time_diff)");
        
        //Branch on whether we satisfy value
        if (dist/Double(time_diff) > mVeloTresh)
        {
            mNumSucVel = min(mNumSucVel + 1, mWindSize);   //Update the number of successive velocities passing the treshold
        }
        else
        {
            flush();    //Restart anew
            
        }
        
        //In any case, add this window... since even if we flush, this may be the first of a set of valid windows...
        mBuffer.add(point : new_window);
        
        //Return indication of whether we succeed
        return (mNumSucVel >= mWindSize) && (getCumulativeVelocity() > mCumuTresh);

    }
    
    func CheckMaxTrigger(_ dist : Double, _ time_diff : Int, new_window : RoutePoint ) -> Bool
    {
        //Branch on whether we satisfy value
        if (dist/Double(time_diff) < mVeloTresh)
        {
            mNumSucVel = min(mNumSucVel + 1, mWindSize);   //Update the number of successive velocities passing the treshold (below)
        }
        else
        {
            flush();    //Restart anew
        }
    
        //In any case, add this window... since even if we flush, this may be the first of a set of valid windows...
        mBuffer.add(point : new_window);
    
        //Return indication of whether we succeed
        return (mNumSucVel >= mWindSize) && (getCumulativeVelocity() < mCumuTresh);
    }
    
    /**
     * \brief Clear (flush) the buffer
     */
    func flush()
    {
        //Next the buffer:
        mBuffer = WindowBuffer(with: mWindSize+1);    //Size must hold one more than the window size
    
        //Create Total
        mNumSucVel = 0;
    }
    
    /**
     * \brief Helper function to compute the distance between two extreme points
     */
    func getCumulativeVelocity() -> Double
    {
        //Calculate Distance: Note that we use mWindSize, not mWindSize-1 because indeed, mBuffer is of size mWindSize+1
        let dist = mBuffer[mWindSize].distanceTo(point: mBuffer[0]);
        
        //Calculate Time Difference
        let time_dif = Double(mBuffer[0].TimeStamp - mBuffer[mWindSize].TimeStamp);
        
        return dist/time_dif;
    }

}
