//
//  RoutePostProcessor.swift
//  vjagg
//
//  Created by Michael Camilleri on 17/01/2017.
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

class RoutePostProcessor
{
    private static let AVERAGE_SIZE = 6;        //!< Averaging size for velocity calculations
    private        let TAG = "RPP";
    
    private        var mJourneys : [Journey];   //!< List of Journeys

    init?()
    {
        Debugger.Debug(TAG, "Init");
        
        guard let reader = FileStream(file: Util.GetAppFile(withName: TrackingService.GPS_LOG_FILE).path) else { Debugger.Warn(TAG, "File Inexistent"); return nil; }
        
        mJourneys = [Journey]();
        while let journey = reader.ReadSerialiseable(of: Journey.self){ mJourneys.append(journey); print(journey.count); }
        
        reader.close();
    }
    
    func toFile() -> Bool
    {
        Debugger.Debug(TAG, "Save");
        
        //If no journeys to write, do nothing
        guard mJourneys.count > 0 else { return true; }
        
        //Attempt to open
        guard let writer = FileStream(file: Util.GetAppFile(withName: TrackingService.GPS_RPP_FILE).path, with: FileStream.APPENDABLE) else { Debugger.Error(TAG, "File I/O Error"); return false; }
        
        //Write to file (and fail immediately if an error happens)
        for journey in mJourneys { guard writer.WriteSerialiseable(value: journey) else { return false; } }
        
        //Delete and clean
        mJourneys.removeAll();
        writer.close();
        
        //Delete old file
        //_ = Util.DeleteAppFile(withName: TrackingService.GPS_LOG_FILE)
        return true;
    }
    
    
    /**
     * \brief Trims the journey start/end points for velocities exceeding a certain speed...
     * @param num
     * @param vel_threshold
     */
    func TrimEnds(upTo num:Int, underThreshold value:Double)
    {
        Debugger.Debug(TAG, "Trim Ends");
        
        for i in 0..<mJourneys.count
        {
            var k = 0; //Need to keep track of two indices: one is the actual index into the array (k): the other is for the maximum number of counts to remove (j)...
            
            //First Trim Start Points
            for _ in 0..<num
            {
                if (EstimateSpeed(from: mJourneys[i][k], to: mJourneys[i][k+1]) > value)
                {
                    _ = mJourneys[i].remove(idx: k);
                }
                else
                {
                    //Only Increment if we did not trim...
                    k += 1;
                }
                
            }
            
            //Now Trim end-points
            k = mJourneys[i].count - 1;
            for _ in 0..<num
            {
                if (EstimateSpeed(from: mJourneys[i][k-1], to: mJourneys[i][k]) > value)
                {
                    _ = mJourneys[i].remove(idx: k);
                }
                
                //This time, always decrement...
                k -= 1;
            }
        }
    }
    
    /**
     * \brief Removes journeys from the list whose bounds are less than the distance threshold specified.
     * @param dist_threshold
     */
    func ThresholdOnDistance(lessThan threshold:Double)
    {
        Debugger.Debug(TAG, "Thres. Dist.");
        
        //Iterate backwards to avoid invalidating indices
        for i in (0..<mJourneys.count).reversed()
        {
            if (mJourneys[i].Bounds.Span < threshold) { mJourneys.remove(at: i); }
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
    func JoinJourneys(ifTimeLessThan timeDiff:Int, andVelocityWithin velFactor:Double)
    {
        Debugger.Debug(TAG, "Join");
        
        //I not enough journeys still in, nothing to do...
        guard mJourneys.count > 1 else { return; }
        
        //Iterate through journeys, again, starting from reverse...
        for i in (0..<mJourneys.count - 1).reversed()
        {
            //First check that time difference is not excessive
            if ((mJourneys[i+1][0].TimeStamp - mJourneys[i][-1].TimeStamp) > timeDiff) { continue; }
            
            //Compute Velocity between last points in the first journey
            var velEnd = 0.0;
            for j in (1...RoutePostProcessor.AVERAGE_SIZE).reversed()
            {
                velEnd += EstimateSpeed(from: mJourneys[i][-j-1], to: mJourneys[i][-j])
            }
            velEnd /= Double(RoutePostProcessor.AVERAGE_SIZE);
            
            //Now Compute velocity between last point of first journey and first point of second journey
            let velDif = EstimateSpeed(from: mJourneys[i][-1], to: mJourneys[i+1][0])
            
            //Ensure that threshold passed
            if (velDif > velFactor*velEnd) { continue; }
            
            //Else Append and remove second journey...
            mJourneys[i].add(journey: mJourneys[i+1]);
            mJourneys.remove(at: i+1);
        }
    }
    
    func AddDummy()
    {
        let journey = Journey();
        
        journey.add(point: RoutePoint(latitude: 36.0432, longitude: 14.2904, time: Util.GetCurrentMSTime()));
        journey.add(point: RoutePoint(latitude: 36.0397, longitude: 14.2881, time: Util.GetCurrentMSTime()+1000));
        journey.add(point: RoutePoint(latitude: 36.0376, longitude: 14.2846, time: Util.GetCurrentMSTime()+2000));
        journey.add(point: RoutePoint(latitude: 36.0385, longitude: 14.2647, time: Util.GetCurrentMSTime()+3000));
        print(journey.count);
        mJourneys.append(journey);
    }
    
    /**
     * \brief Helper function for estimating the speed (non-negative) between two points
     * @param start
     * @param end
     * @return
     */
    private func EstimateSpeed(from start:RoutePoint, to end:RoutePoint) -> Double
    {
        return start.distanceTo(point: end)/abs(Double(end.TimeStamp - start.TimeStamp));
    }
}
