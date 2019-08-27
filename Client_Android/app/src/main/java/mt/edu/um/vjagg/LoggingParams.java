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

import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

/**
 * Created by Michael Camilleri on 05/10/2016.
 *
 * This class encapsulates all of the logging parameters of vjagg
 */

final class LoggingParams
{
    //!< Accelerometer-Specific Parameters:
    static final long   ACCEL_CHECK_RATE  =  500;      //!< Currently check every 20 or so seconds
    static final int    ACCEL_SAMPLE_RATE = 200000;    //!< Sample every 200ms (this is usually ignored however...)
    static final int    ACCEL_SAMPLE_RATE_T = (ACCEL_SAMPLE_RATE - 10000)/1000;
    static final int    QUICK_SAMPLE_SIZE = 7;          //!< 4 Samples for averaging
    static final int    EXTRA_SAMPLE_SIZE = 8;          //!< 12 Samples for extra identification...
    static final double QUICK_SAMPLE_THRESH = 0.185;    //!< Threshold for Quick Sampling (0.5m/s2)
    static final double EXTRA_SAMPLE_THRESH = 4.212;    //!< Threshold for Extra Sampling... 1.0m/s2 deviation

    //GPS Specific Parameters
    static final int   GPS_WINDOW_SIZE = 30;        //!< The buffer size...
    static final long  GPS_SAMPLE_RATE = 2000;      //!< Sample every 2000ms...
    static final int   GPS_DOWNSAMPLE  = 3;         //!< Downsampling rate (divide by 3)
    static final float GPS_VEL_ST_TRIG = 0.001f;    //!< Velocity Treshold between successive points: m/ms (amounts to 1m/s)
    static final float GPS_VEL_ST_TOTAL= 0.001f;    //!< Total Velocity in any one direction...(1m/s)
    static final int   GPS_VEL_ST_NUM  = 3;         //!< Number of successive differences passing the Velocity Treshold (i.e. if 3, then between 4 points)
    static final int   GPS_TERM_WIND   = 25;        //!< Number of windows to consider for hysterisis of stopping
    static final int   GPS_TERM_DIST_TOT = 30;      //!< If in GPS_TERM_WIND time points, the subject did not move more than this amount of meteres, then stop journey
    static final float GPS_VEL_EN_TRIG = 0.001f;    //!< Velocity Treshold between successive points: m/ms (amounts to 1m/s) (for terminating)
    static final float GPS_VEL_EN_TOTAL= 0.001f;    //!< Total Velocity in any one direction...(1m/s) (for terminating)
    static final int   GPS_VEL_EN_NUM  = 3;         //!< Number of successive differences passing the Velocity Treshold (i.e. if 3, then between 4 points) (for terminating)

    //GPS/ACC Transitioning Parameters
    static final int   GPS_MIN_SATS = 5;        //!< Changed to 5 to countdown false positives further
    static final long  GPS_NOSAT_TO =  40000;   //!< GPS Timeout in milliseconds: if lost for more than this, then kill and start new journey
    static final long  GPS_STATE_TO = 300000;   //!< If no valid journey in 5 minutes...

    //PostProcessor Parameters
    static final float PP_CONS_DIST_THRESH = 50.0f; //!< Conservative Distance Threshold
    static final int   PP_JOIN_TIME_THRESH = 120000;
    static final float PP_JOIN_VEL_FACTOR  = 1.2f;
    static final float PP_ABS_DIST_THRESH  = 500.0f;
    static final int   PP_TRIM_MAX         = 3;     //!< Trim at maximum 3 points
    static final float PP_TRIM_VEL_THRESH  = 0.02f;  //!< 0.02 metres per ms - 20 m/s or 72km/hr
}
