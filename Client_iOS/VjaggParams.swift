//
//  VjaggParams.swift
//  vjagg
//
//  Created by administrator on 13/01/2017.
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

final class LoggingParams
{
    //!< Accelerometer-Specific Parameters:
    static let ACCEL_CHECK_RATE  = 500;      //!< Currently check every 500ms
    static let ACCEL_SAMPLE_RATE = 0.2;     //!< Sample every 200ms (this is usually ignored however...)
    //static let ACCEL_SAMPLE_RATE_T = (ACCEL_SAMPLE_RATE - 10000)/1000;
    static let QUICK_SAMPLE_SIZE = 7;          //!< 4 Samples for averaging
    static let EXTRA_SAMPLE_SIZE = 8;         //!< 12 Samples for extra identification...
    static let QUICK_SAMPLE_THRESH = 0.0189;      //!< Threshold for Quick Sampling (expressed in terms of g's)
    static let EXTRA_SAMPLE_THRESH = 0.4294;      //!< Threshold for Extra Sampling... 1.0m/s2 deviation
    
    //GPS Specific Parameters
    //static let GPS_SAMPLE_RATE = 2000;      //!< GPS Sample Rate is fixed by the OS at 1 second intervals
    static let GPS_DOWNSAMPLE  = 6;  //!< Downsampling rate (divide by 6 - to account for different sample rate)
    static let GPS_WINDOW_SIZE = 30;        //!< The buffer size...
    static let GPS_VEL_ST_TRIG = 0.001;    //!< Velocity Treshold between successive points: m/ms (amounts to 1m/s)
    static let GPS_VEL_ST_TOTAL = 0.001;    //!< Total Velocity in any one direction...(1m/s)
    static let GPS_VEL_ST_NUM  = 3;         //!< Number of successive differences passing the Velocity Treshold (i.e. if 3, then between 4 points)
    static let GPS_TERM_WIND   = 25;        //!< Number of windows to consider for hysterisis of stopping
    static let GPS_TERM_DIST_TOT = 30;      //!< If in GPS_TERM_WIND time points, the subject did not move more than this amount of meteres, then stop journey
    static let GPS_VEL_EN_TRIG = 0.001;    //!< Velocity Treshold between successive points: m/ms (amounts to 1m/s) (for terminating)
    static let GPS_VEL_EN_TOTAL = 0.001;    //!< Total Velocity in any one direction...(1m/s) (for terminating)
    static let GPS_VEL_EN_NUM  = 3;         //!< Number of successive differences passing the Velocity Treshold (i.e. if 3, then between 4 points) (for terminating)
    
    //GPS/ACC Transitioning Parameters
    static let GPS_UNC_MIN  = 100.0;      //!< Horizontal Accuracy of 100 metres or less is acceptable: otherwise trigger Satellite TimeOut
    static let GPS_NOSAT_TO =  40000;   //!< GPS Timeout in milliseconds: if lost for more than this, then kill and start new journey
    static let GPS_STATE_TO =  300000;   //!< If no valid journey in 5 minutes...
    
    //PostProcessor Parameters
    static let PP_CONS_DIST_THRESH = 50.0; //!< Conservative Distance Threshold
    static let PP_JOIN_TIME_THRESH = 120000;
    static let PP_JOIN_VEL_FACTOR  = 1.2;
    static let PP_ABS_DIST_THRESH  = 500.0;
    static let PP_TRIM_MAX         = 2;     //!< Trim at maximum 2 points
    static let PP_TRIM_VEL_THRESH  = 0.010;
}
