//
//  Debugger.swift
//  vjagg
//
//  Created by Michael Camilleri on 18/01/2017.
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

class Debugger : TimerDelegate
{
    private static let DEBUG_LOG = "Debug.log";
    private static let SINGLE    = Debugger();
    
    private var mFileHandle : FileHandle?;
    private var mTimer      : Watchdog?;
    
    init()
    {
        //Set up FileName
        let name = Util.GetAppFile(withName: Debugger.DEBUG_LOG).path;
        mFileHandle = nil;
        
        if !FileManager.default.fileExists(atPath: name)
        {
            do { try "".write(toFile: name, atomically: true, encoding: String.Encoding.utf8) }
            catch { return; }
        }
        
        //Now Open File for Writing
        mFileHandle = FileHandle(forWritingAtPath: name);
        
        //Seek to end of file if so far successful
        mFileHandle?.seekToEndOfFile();
        
        //Create watchdog
        mTimer = Watchdog(ID: 0, TO: 60000, delegate: self);
    }
    
    func HandleTimeOut(from watchdog: Int)
    {
        mFileHandle?.synchronizeFile();
    }
    
    deinit
    {
        mFileHandle?.closeFile(); mFileHandle = nil;
        _ = mTimer?.stop();
    }
    
    static func Debug(_ tag:String, _ msg:String)
    {
        SINGLE.write("[D] {\(Util.FormateTime(from: Util.GetCurrentMSTime()))} \(tag) : \(msg)");
    }
    
    static func Warn(_ tag:String, _ msg:String)
    {
        SINGLE.write("[W] {\(Util.FormateTime(from: Util.GetCurrentMSTime()))} \(tag) : \(msg)");
    }
    
    static func Error(_ tag:String, _ msg:String)
    {
        SINGLE.write("[E] {\(Util.FormateTime(from: Util.GetCurrentMSTime()))} \(tag) : \(msg)");
    }
    
    func write(_ entire:String)
    {
        print(entire);  //Just for checking on screen...
        if let data = (entire + "\n").data(using: String.Encoding.utf8)
        {
            objc_sync_enter(self);
            mFileHandle?.write(data);
            objc_sync_exit(self);
        }
    }
}
