//
//  Journey.swift
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

import Foundation
import MapKit
import CoreLocation

final class Journey : Serialiseable, Textualiseable, CustomStringConvertible
{
    //!< Member Constants
    static let BUFFER_CAPACITY = 2000;   //!< Initial capacity of the buffer
    
    //!< Flags
    static let TC_UNSPEC = 0;    //Unspecified
    static let TC_RT_CN = 1;    //Route Continues
    static let TC_RE_NM = 2;    //Termination Code: Route Ends because there was no significant motion
    static let TC_RE_SL = 3;    //Termination Code: Route Ends because there was an extended loss of signal...
    static let TC_RE_SF = 4;    //Termination Code: Route Ends because there was an extended loss of signal but a tentative termination point was found...
    static let TC_RE_US = 5;    //Termination Code: Route ended by the user (through the destroy function)
    static let TC_RE_OS = 6;    //Termination Code: Route ended by the OS or an error
    
    static let MD_UNSPEC = 0;   //!< Unspecified
    static let MODE_SELECT_STR = ["Car Driver", "Car Passenger", "Bus (Public Transport)", "Coach/Minibus", "Motorbike", "On Foot", "Bicycle", "Ferry", "Taxi", "Other"];
    static let MODE_SELECT_VAL = [1, 2, 4, 8, 16, 32, 64, 128, 256, 512];
    
    static let TP_UNSPEC = 0;  //!< Unspecified
    static let PURP_SELECT_STR = ["Undisclosed", "Going Home", "Visiting Someone", "Going to Work", "Work-Related", "Medical", "Personal Errand", "Shopping", "Education", "Accompanying", "Sports", "Other"];
    static let PURP_SELECT_VAL = [0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024];
    
    //!< Member Variables
    private var mBuffer  : [RoutePoint];  //!< Stores the route buffer - this is used both during journey buildup and storage
    private var mTermRsn : Int;           //!< Termination Reason for the journey
    private var mIdent   : Int;           //!< The unique Identifier of a Journey - basically, this is currently set to the creation time of the object
    private var mMode    : Int;           //!< Mode of transport
    private var mPurpose : Int;           //!< Purpose of Journey
    
    //!< Visualisation Variables
    private var mBounds  : CoordBounds;    //!< Journey Bounds (south-west/north-east)
    
    private let TAG = "Journey";
    
    //!< Accessors (Computed Variables)
    subscript(index: Int) -> RoutePoint
    {
        return index > -1 ? mBuffer[index] : mBuffer[mBuffer.count + index];
    }
    
    var Purpose : Int
    {
        get
        {
            if (mPurpose == 0) { return 0; }
            else
            {
                for i in 1 ..< Journey.PURP_SELECT_VAL.count
                {
                    if ((mPurpose & Journey.PURP_SELECT_VAL[i]) > 0) { return i; }
                }
                
                //If still here, error
                return -1;
            }
        }
        
        set(purpose)
        {
            if (purpose == 0) { mPurpose = 0; } else { mPurpose = Int(pow(2.0, Double(purpose-1))); }
        }
    }
    
    var Mode : [Bool]
    {
        get
        {
            var mode = [Bool]();
            for i in 0 ..< Journey.MODE_SELECT_VAL.count
            {
                mode.append((mMode & Journey.MODE_SELECT_VAL[i]) > 0);
            }
            return mode;
        }
        
        set(mode)
        {
            mMode = 0;
            for i in 0 ..< mode.count { mMode += Journey.MODE_SELECT_VAL[i]*(mode[i] ? 1 : 0); }
        }
    }
    
    var ID : Int { return mIdent; }
    
    var description : String { return "\(mIdent)" ; }
    
    var count : Int { return mBuffer.count; }
    
    var Bounds : CoordBounds { return mBounds; }
    
    /**
     * Public Initialiser Constructor
     */
    init()
    {
        mBuffer  = [];
        mTermRsn = Journey.TC_UNSPEC;
        mIdent   = Util.GetCurrentMSTime();
        mBounds  = CoordBounds();
        mMode    = Journey.MD_UNSPEC;
        mPurpose = Journey.TP_UNSPEC;
    }
    
    init?(from file: FileStream)
    {
        //First Read from File
        guard let ident = file.Read(type: Int.self) else { return nil; }; mIdent   = ident;
        guard let term  = file.Read(type: Int.self) else { return nil; }; mTermRsn = term;
        guard let mode  = file.Read(type: Int.self) else { return nil; }; mMode    = mode;
        guard let purp  = file.Read(type: Int.self) else { return nil; }; mPurpose = purp;
        guard let buff  = file.ReadSerialiseableArray(of: RoutePoint.self) else { return nil; }; mBuffer = buff;
        
        //Now Build Bounds
        mBounds = CoordBounds(from: mBuffer);
    }
    
    init?(from text: TextStream)
    {
        //First Read from File
        guard let ident = text.Read(Int.self) else { return nil; }; mIdent   = ident;
        guard let term  = text.Read(Int.self) else { return nil; }; mTermRsn = term;
        guard let mode  = text.Read(Int.self) else { return nil; }; mMode    = mode;
        guard let purp  = text.Read(Int.self) else { return nil; }; mPurpose = purp;
        guard let buff  = text.ReadArray(RoutePoint.self) else { return nil; }; mBuffer = buff;
        
        //Now Build Bounds
        mBounds = CoordBounds(from: mBuffer);
    }
    
    ////============================ Buffer Access Functions ===========================////
    
    /**
     * Add a journey point to the current buffer...
     * @param point
     */
    func add(point : RoutePoint)
    {
        if (point.TimeStamp > 0)
        {
            mBuffer.append(point.copy());
        }
    }
    
    /**
     * \brief Appends the points of the specified journey, and also sets the end reason to be that of the second journey (all other values retained)
     *
     * @param journey
     * @return
     */
    func add(journey : Journey)
    {
        //First Handle Termination Reason
        var otr_reason = journey.mTermRsn;
        while (otr_reason > 0)          //Shift to the left to accomodate new journey
        {
            otr_reason /= 8;
            mTermRsn *= 8;
        }
        mTermRsn += journey.mTermRsn;   //Add the new journeys' termination reason
        
        //Now append journey buffer
        mBuffer.append(contentsOf: journey.mBuffer);
        
        //Finally recalculate Bounds
        mBounds.reCalculate(using: mBuffer);
    }
    
    func remove(idx at : Int) -> RoutePoint
    {
        let pt = mBuffer[at];
        mBuffer.remove(at: at);
        return pt;
    }
    
    /**
     * \brief   Flush the buffer, effectively creating a new journey
     */
    func flush()
    {
        mBuffer.removeAll();
        mTermRsn = Journey.TC_UNSPEC;
        mIdent   = Util.GetCurrentMSTime();
        mBounds.reCalculate();
        
        mMode    = Journey.MD_UNSPEC;
        mPurpose = Journey.TP_UNSPEC;
    }
    
    func terminate(with reason : Int)
    {
        if (mBuffer.count > 0 && reason != Journey.TC_UNSPEC) { mTermRsn = reason; }
    }
    
    ////============================ Buffer Serialization Functions ===========================////
    
    func toFile(file: FileStream) -> Bool
    {
        guard file.Write(value: mIdent)     else { return false; }
        guard file.Write(value: mTermRsn)   else { return false; }
        guard file.Write(value: mMode)      else { return false; }
        guard file.Write(value: mPurpose)   else { return false; }
        
        return file.WriteSerialiseableArray(values: mBuffer);
    }
    
    func toText(text: TextStream) -> Bool
    {
        guard text.Write(mIdent)     else { return false; }
        guard text.Write(mTermRsn)   else { return false; }
        guard text.Write(mMode)      else { return false; }
        guard text.Write(mPurpose)   else { return false; }
        
        return text.WriteArray(mBuffer);
    }
    
    func header() -> String
    {
        let text = TextStream();
        _ = text.Write(mMode);
        _ = text.Write(mPurpose);
        _ = text.Write(mTermRsn);
        _ = text.Write(mBuffer.count);
        return text.Buffer;
    }
    
    func points(from:Int, num:Int) -> String
    {
        let text = TextStream();
        for i in from ..< min(from+num, mBuffer.count) { _ = text.Write(mBuffer[i]); }
        return text.Buffer;
    }
    
    ////============================ Buffer Display Functions ===========================////
    
    /**
     * \brief   Return the Region of the Map covered by the journey
     *
     * @return An MKCoordinateRegion
     */
    func getRegion(_ bound:Double = 0.0) -> MKCoordinateRegion
    {
        let span = MKCoordinateSpan(latitudeDelta: mBounds.NorthEast.latitude - mBounds.SouthWest.latitude + bound, longitudeDelta: mBounds.NorthEast.longitude - mBounds.SouthWest.longitude + bound);
        let centre = CLLocationCoordinate2D(latitude: (span.latitudeDelta - bound)/2 + mBounds.SouthWest.latitude, longitude: (span.longitudeDelta - bound)/2 + mBounds.SouthWest.longitude);
        return MKCoordinateRegionMake(centre, span);
    }
    
    func getPolyline() -> MKPolyline
    {
        var points = [CLLocationCoordinate2D]();
        
        for point in mBuffer { points.append(CLLocationCoordinate2DMake(point.Latitude, point.Longitude)); }
        
        return MKPolyline(coordinates: &points, count: points.count);
    }
    
    func getDate() -> String
    {
        if (mBuffer.count < 1) { return "Invalid Journey"; }
        else { return Util.FormateDate(from: mBuffer[0].TimeStamp); }
    }
    
    func getTimes() -> String
    {
        if (mBuffer.count < 1) { return "Invalid Journey"; }
        else { return "[" + Util.FormateTime(from: mBuffer[0].TimeStamp) + "] - [" +  Util.FormateTime(from: mBuffer[mBuffer.count-1].TimeStamp) + "]"; }
    }
   
    func getModeStr() -> String
    {
        if (mMode == Journey.MD_UNSPEC) { return "(Undisclosed)"; }
        else
        {
            var mode = "";
            for i in 0..<Journey.MODE_SELECT_STR.count
            {
                if ((mMode & Journey.MODE_SELECT_VAL[i]) > 0)
                {
                    mode.append(Journey.MODE_SELECT_STR[i] + " ");
                }
            }
            return mode;
        }
    }
    
    static func ModeStr(_ mode:[Bool]) -> String
    {
        //If there is at least one true...
        if (mode.contains(true))
        {
            var modeStr = "";
            for i in 0..<Journey.MODE_SELECT_STR.count
            {
                if (mode[i]) { modeStr.append(Journey.MODE_SELECT_STR[i] + " "); }
            }
            return modeStr;
        }
        else { return "(Undisclosed)"; }
    }
    
    func getPurpStr() -> String
    {
        if (mPurpose == Journey.TP_UNSPEC) { return "(Undisclosed)"; }
        else
        {
            var purp = "";
            for i in 0..<Journey.PURP_SELECT_STR.count
            {
                if ((mPurpose & Journey.PURP_SELECT_VAL[i]) > 0)
                {
                    purp.append(Journey.PURP_SELECT_STR[i])
                }
            }
            return purp;
        }
    }
    
    static func PurpStr(_ purpose:Int) -> String
    {
        if (purpose == Journey.TP_UNSPEC) { return "(Undisclosed)"; }
        else
        {
            var purp = "";
            for i in 0..<Journey.PURP_SELECT_STR.count
            {
                if ((purpose & Journey.PURP_SELECT_VAL[i]) > 0)
                {
                    purp.append(Journey.PURP_SELECT_STR[i])
                }
            }
            return purp;
        }
    }
}
