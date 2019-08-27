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
 * Created by Michael Camilleri
 *
 * Implements the basic Route Point for logging of journeys. This consists of a time stamp, a latitude and a longitude
 *
 * [Converted to Primitive Types]
 */

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * \brief   Route-Point Class
 * \detail  This class is used to store a single route-point object. This consists of a Latitude value, Longitude and Time-Stamp
 */
public class RoutePoint
{
    public double mLatitude;    //!< Stores the Latitude of the point in degrees
    public double mLongitude;   //!< Stores the Longitude of the point in degrees
    public long   mTimeSt;      //!< Stores the UTC Time-stamp (milliseconds since January 1, 1970)

    private static double ORIG_LAT = 36.043280;
    private static double ORIG_LNG = 14.290679;

    /**
     * \brief Public Default Constructor
     */
    public RoutePoint()
    {
        mLatitude = mLongitude = 0.0;
        mTimeSt = 0L;
    }

    /**
     * \brief Public Constructor for initialisation
     * @param lat   The Latitude Value
     * @param lon   The Longitude Value
     * @param time  The Time-Stamp
     */
    public RoutePoint(double lat, double lon, long time)
    {
        mLatitude = lat;
        mLongitude = lon;
        mTimeSt = time;
    }

    /**
     * \brief Public Constructor for initialisation from a Location Object
     * @param location  The location object: the latitude, longitude and time stamp are stored
     */
    public RoutePoint(Location location)
    {
        mLatitude   = location.getLatitude();
        mLongitude  = location.getLongitude();
        mTimeSt     = location.getTime();
    }

    /**
     * Constructs and returns a LatLng point
     * @return
     */
    public LatLng Point()
    {
        return new LatLng(mLatitude, mLongitude);
    }

    public RoutePoint(DataInputStream stream) throws IOException
    {
        serialize(stream);
    }

    public void serialize(DataOutputStream stream) throws IOException
    {
        stream.writeDouble(mLatitude);
        stream.writeDouble(mLongitude);
        stream.writeLong(mTimeSt);
    }

    public void serialize(DataInputStream stream) throws IOException
    {
        mLatitude = stream.readDouble();
        mLongitude = stream.readDouble();
        mTimeSt = stream.readLong();
    }

    ////// Operator Overloading //////

    /**
     * \brief (Overloads) The Addition operator
     * \details This overloads the Plus (+) operator, without modifying any of the original values.
     * @param rp The RoutePoint to add with a copy of this object
     * @return   The resulting value
     */
    public RoutePoint Plus (RoutePoint rp)
    {
        RoutePoint tmp = new RoutePoint();
        tmp.mLongitude = this.mLongitude + rp.mLongitude;
        tmp.mLatitude  = this.mLatitude  + rp.mLatitude;
        tmp.mTimeSt    = this.mTimeSt    + rp.mTimeSt;
        return tmp;
    }

    /**
     * \brief (Overloads) The Plus-Equal operator
     * \details This overloads the PlusEquals (+=) operator, modifying the original object.
     *          Note that the operation is defined only on the lat/long: the time value is retained
     * @param rp The RoutePoint to add to this object
     * @return  This object so that operations can be chained...
     */
    public RoutePoint Add (RoutePoint rp)
    {
        this.mLatitude  += rp.mLatitude;
        this.mLongitude += rp.mLongitude;
        this.mTimeSt    += rp.mTimeSt;
        return this;
    }

    /**
     * \brief (Overloads) The Minus-Equal operator
     * \details This overloads the MinusEquals (-=) operator, modifying the original object.
     *          Note that the operation is defined only on the lat/long: the time value is retained
     * @param rp The RoutePoint to subtract from this object
     * @return  This object so that operations can be chained...
     */
    public RoutePoint Subtract (RoutePoint rp)
    {
        this.mLatitude  -= rp.mLatitude;
        this.mLongitude -= rp.mLongitude;
        this.mTimeSt    -= rp.mTimeSt;
        return this;
    }

    /**
     * \brief (Overloads) The scalar multiplication operator
     * \details This overloads the scalar multiplication (*) operator, without modifying the original value
     *          Note that the operator is defined only for the Lat/Lng: the time value is set to 0
     * @param scalar The scalar multiple
     * @return       A copy of the result
     */
    public RoutePoint Mult (double scalar)
    {
        return new RoutePoint(this.mLatitude*scalar, this.mLongitude*scalar, Math.round(this.mTimeSt*scalar));
    }

    /**
     * \brief (Overloads) The Scalar Self-Multiplication operator
     * \details This overloads the scalar self-multiplication (*=) operator, modifying the original value
     *          Note that the operation is defined only on the lat/long: the time value is retained
     * @param scalar Scalar multiplier
     * @return       Reference to this object for chaining...
     */
    public RoutePoint MultEq (double scalar)
    {
        this.mLongitude *= scalar;
        this.mLatitude  *= scalar;
        this.mTimeSt     = Math.round(this.mTimeSt*scalar);

        return this;
    }

    /**
     * \brief For creating a copy of this object
     * @return
     */
    public RoutePoint copy()
    {
        return new RoutePoint(mLatitude, mLongitude, mTimeSt);
    }

//    /**
//     * \brief For Debugging //TODO Remove if necessary
//     */
//    public String toString()
//    {
//        return "(" + Long.toString(mTimeSt) +")(" + Double.toString(mLatitude) + ")(" + Double.toString(mLongitude) + ")";
//    }
}
