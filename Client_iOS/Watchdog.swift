//
//  Watchdog.swift
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

protocol TimerDelegate
{
    func HandleTimeOut(from watchdog: Int);
}

class Watchdog
{
    private let mID      : Int;
    private let mTimeOut : Double;
    private let mHandler : TimerDelegate;
    private var mActive  : Bool;
    private var mTimer   : Timer?;
    
    init(ID : Int, TO : Int, delegate : TimerDelegate)
    {
        mID      = ID;
        mTimeOut = Double(TO)/1000;
        mHandler = delegate;
        mActive = false;
        mTimer  = nil;
    }
    
    func start() -> Bool
    {
        //Check if not active
        if (mActive) { return false; }  //Timer already active
        
        //Else, start it by scheduling on Run Loop
        mTimer  = Timer.scheduledTimer(timeInterval: mTimeOut, target: self, selector: #selector(timerFireMethod(timer:)), userInfo: nil, repeats: true);
        mActive = true;
        return true;
    }
    
    func stop() -> Bool
    {
        if (!mActive) { return false; }
        
        mTimer?.invalidate();
        mActive = false;
        return true;
    }
    
    func ping(with timeout : Int = -1) -> Bool
    {
        if (!mActive) { return false; }
        
        //Else, first invalidate
        mTimer?.invalidate();
        
        //Now Resolve Timeout
        let to = timeout > 0 ? Double(timeout)/1000 : mTimeOut;
        
        //Now Reping
        mTimer  = Timer.scheduledTimer(timeInterval: to, target: self, selector: #selector(timerFireMethod(timer:)), userInfo: nil, repeats: true);
        return true;
    }
    
    @objc private func timerFireMethod(timer : Timer)
    {
        mHandler.HandleTimeOut(from: mID);
    }
    
}
