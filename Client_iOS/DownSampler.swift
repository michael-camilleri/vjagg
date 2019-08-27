//
//  DownSampler.swift
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

protocol DownSampleDelegate
{
    func OnDownSample(with: RoutePoint);
}

class DownSampler
{
    private let DOWNSAMPLE_RATE : Int;  //!< Constant Array Size
    
    private var mWindow : [RoutePoint];         //!< Actual Buffer
    private var mFilled : Int;                  //!< How Many Elements have been populated
    private let mHandler: DownSampleDelegate;   //!< The Actual Delegate to use
    
    var Processed : Int { return mFilled; }
    
    init(rate: Int, handler : DownSampleDelegate)
    {
        DOWNSAMPLE_RATE = rate;
        mWindow = [RoutePoint]();
        mFilled = 0;
        mHandler = handler;
    }
    
    func add(sample : RoutePoint)
    {
        mWindow.append(sample.copy());
        mFilled += 1;
        
        if (mFilled == DOWNSAMPLE_RATE)
        {
            //Compute Average
            var avg = mWindow[0];
            for i in 1..<mFilled { avg += mWindow[i]; }
            avg /= Double(mFilled);
            
            //Reset Parameters
            mWindow = [RoutePoint]();
            mFilled = 0;
            
            //Call Handler
            mHandler.OnDownSample(with: avg);
        }
    }
    
    func flush()
    {
        //Reset Parameters
        mWindow = [RoutePoint]();
        mFilled = 0;
    }
}
