//
//  RoutePoint.swift
//  vjagg
//
//  Created by Michael Camilleri on 03/01/2017.
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

import CoreLocation
import CoreMotion
import Foundation

extension CLLocation
{
    var Minimal : String { return "\(coordinate.latitude) \(coordinate.longitude) \(Int(timestamp.timeIntervalSince1970*1000)) \(horizontalAccuracy)"; }
}

extension CMAccelerometerData
{
    var Minimal : String { return "\(acceleration.x) \(acceleration.y) \(acceleration.z) " +
        "\(Int(timestamp*1000))"; }
}

struct RoutePoint : Serialiseable, Textualiseable, CustomStringConvertible
{
    private var mLocation : CLLocation
    
    var TimeStamp : Int               { return Int(mLocation.timestamp.timeIntervalSince1970*1000); }
    var Latitude  : CLLocationDegrees { return mLocation.coordinate.latitude; }
    var Longitude : CLLocationDegrees { return mLocation.coordinate.longitude; }
    var description : String { return "\(Latitude) \(Longitude) \(TimeStamp)" ; }

    init()
    {
        self.init(latitude: 0, longitude: 0, time: 0);
    }
    
    init(from location: CLLocation)
    {
        mLocation = location.copy() as! CLLocation;
    }
    
    init(latitude: CLLocationDegrees, longitude: CLLocationDegrees, time: Int)
    {
        mLocation = CLLocation(coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude), altitude: 0.0, horizontalAccuracy: 0.0, verticalAccuracy: -1.0, timestamp: Date(timeIntervalSince1970: Double(time)/1000.0));
    }
    
    init?(from file: FileStream)
    {
        guard let latitude  = file.Read(type: Double.self) else { return nil; }
        guard let longitude = file.Read(type: Double.self) else { return nil; }
        guard let timestamp = file.Read(type: Int.self)    else { return nil; }
        
        mLocation = CLLocation(coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude), altitude: 0.0, horizontalAccuracy: 0.0, verticalAccuracy: -1.0, timestamp: Date(timeIntervalSince1970: Double(timestamp)/1000));
    }
    
    init?(from text: TextStream)
    {
        guard let latitude  = text.Read(Double.self) else { return nil; }
        guard let longitude = text.Read(Double.self) else { return nil; }
        guard let timestamp = text.Read(Int.self) else { return nil; }
        
        mLocation = CLLocation(coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude), altitude: 0.0, horizontalAccuracy: 0.0, verticalAccuracy: -1.0, timestamp: Date(timeIntervalSince1970: Double(timestamp)/1000));
    }
    
    mutating func from(_ rp : RoutePoint)
    {
        mLocation = rp.mLocation.copy() as! CLLocation;
    }
    
    func copy() -> RoutePoint
    {
        return RoutePoint(from: mLocation);
    }
    
    func toFile(file: FileStream) -> Bool
    {
        if (!file.Write(value: Latitude)) { return false; }
        if (!file.Write(value: Longitude)){ return false; }
        if (!file.Write(value: TimeStamp)){ return false; }
        
        return true;
    }
    
    func toText(text: TextStream) -> Bool
    {
        if (!text.Write(Latitude)) { return false; }
        if (!text.Write(Longitude)){ return false; }
        if (!text.Write(TimeStamp)){ return false; }
        
        return true;

    }
    
    mutating func set(_ latitude: CLLocationDegrees, _ longitude: CLLocationDegrees, _ time: Int)
    {
        mLocation = CLLocation(coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude), altitude: 0.0, horizontalAccuracy: 0.0, verticalAccuracy: -1.0, timestamp: Date(timeIntervalSince1970: Double(time)/1000.0));
    }
    
    func distanceTo(point : RoutePoint) -> CLLocationDistance
    {
        return mLocation.distance(from: point.mLocation);
    }
}

func += (left: inout RoutePoint, right: RoutePoint)
{
    let lat = left.Latitude + right.Latitude;
    let lng = left.Longitude + right.Longitude;
    let tme = left.TimeStamp + right.TimeStamp;
    
    left.set(lat, lng, tme);
}

func *= (left: inout RoutePoint, right: Double)
{
    let lat = left.Latitude * right;
    let lng = left.Longitude * right;
    let tme = Int(Double(left.TimeStamp) * right);
    
    left.set(lat, lng, tme);

}

func /= (left: inout RoutePoint, right: Double)
{
    let lat = left.Latitude / right;
    let lng = left.Longitude / right;
    let tme = Int(Double(left.TimeStamp) / right);
    
    left.set(lat, lng, tme);
    
}

struct CoordBounds
{
    private var mBottomLeft : CLLocationCoordinate2D;
    private var mTopRight   : CLLocationCoordinate2D;
    private var mValid      : Bool;
    
    var SouthWest : CLLocationCoordinate2D { return mBottomLeft; }
    var NorthEast : CLLocationCoordinate2D { return mTopRight; }
    var Span : Double
    {
        return CLLocation(latitude: mTopRight.latitude, longitude: mTopRight.longitude).distance(from: CLLocation(latitude: mBottomLeft.latitude, longitude: mBottomLeft.longitude));
    }
    
    init(from points : [RoutePoint] = [])
    {
        if (points.count < 1)
        {
            mBottomLeft = CLLocationCoordinate2D(latitude: 0,longitude: 0);
            mTopRight   = CLLocationCoordinate2D(latitude: 0,longitude: 0);
            mValid      = false;
        }
        else
        {
            //First assign to first element
            mBottomLeft = CLLocationCoordinate2D(latitude: points[0].Latitude, longitude: points[0].Longitude);
            mTopRight   = CLLocationCoordinate2D(latitude: points[0].Latitude, longitude: points[0].Longitude);
            
            //Now iterate over points
            for pt in points
            {
                if (pt.Latitude < mBottomLeft.latitude) { mBottomLeft.latitude = pt.Latitude; }
                if (pt.Latitude > mTopRight.latitude)   { mTopRight.latitude   = pt.Latitude; }
                
                if (pt.Longitude < mBottomLeft.longitude) { mBottomLeft.longitude = pt.Longitude; }
                if (pt.Longitude > mTopRight.longitude)   { mTopRight.longitude   = pt.Longitude; }
            }
            
            //Validate
            mValid = true;
        }
    }
    
    mutating func reCalculate(using points : [RoutePoint] = [])
    {
        if (points.count < 1)
        {
            mBottomLeft = CLLocationCoordinate2D(latitude: 0,longitude: 0);
            mTopRight   = CLLocationCoordinate2D(latitude: 0,longitude: 0);
            mValid      = false;
        }
        else
        {
            //First assign to first element
            mBottomLeft = CLLocationCoordinate2D(latitude: points[0].Latitude, longitude: points[0].Longitude);
            mTopRight   = CLLocationCoordinate2D(latitude: points[0].Latitude, longitude: points[0].Longitude);
            
            //Now iterate over points
            for pt in points
            {
                if (pt.Latitude < mBottomLeft.latitude) { mBottomLeft.latitude = pt.Latitude; }
                if (pt.Latitude > mTopRight.latitude)   { mTopRight.latitude   = pt.Latitude; }
                
                if (pt.Longitude < mBottomLeft.longitude) { mBottomLeft.longitude = pt.Longitude; }
                if (pt.Longitude > mTopRight.longitude)   { mTopRight.longitude   = pt.Longitude; }
            }
            
            //Validate
            mValid = true;
        }
    }
    
    mutating func add(point : RoutePoint)
    {
        if (mValid)
        {
            if (point.Latitude < mBottomLeft.latitude) { mBottomLeft.latitude = point.Latitude; }
            if (point.Latitude > mTopRight.latitude)   { mTopRight.latitude   = point.Latitude; }
            
            if (point.Longitude < mBottomLeft.longitude) { mBottomLeft.longitude = point.Longitude; }
            if (point.Longitude > mTopRight.longitude)   { mTopRight.longitude   = point.Longitude; }
        }
        else
        {
            mBottomLeft = CLLocationCoordinate2D(latitude: point.Latitude, longitude: point.Longitude);
            mTopRight   = CLLocationCoordinate2D(latitude: point.Latitude, longitude: point.Longitude);
            mValid      = true;
        }
    }
    
    
    
}


