//
//  DebugView.swift
//  vjagg
//
//  This class contains the View Controller for the Debug View, which is used to show information...
//
//  Created by administrator on 15/12/2016.
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

import UIKit

class DebugView: UIViewController, TimerDelegate
{
    let TAG   : String    = "DbgVw";
    var timer : Watchdog! = nil;
    
    @IBOutlet weak var mTable: MCSelector!
    
    override func viewDidLoad()
    {
        super.viewDidLoad();
        timer = Watchdog(ID: 0, TO: 10000, delegate: self);
        print(TAG);
        
        for i in (1...5).reversed() { print(i); }
        
        _ = mTable.initialise(options: ["Hello", "My", "Name", "Is", "Michael", "Camilleri"], radio:true, selections: 2); //[true, true, false, false, true, false]
        
        //Test File Access
        let journey_1 = Journey();
        journey_1.add(point: RoutePoint(latitude: 0.5, longitude: 0.5, time: 1000));
        journey_1.add(point: RoutePoint(latitude: 0.4, longitude: 0.4, time: 2000));
        journey_1.add(point: RoutePoint(latitude: 0.6, longitude: 0.6, time: 3000));
        
        let journey_2 = Journey();
        journey_2.add(point: RoutePoint(latitude: 0.8, longitude: 0.8, time: 11000));
        journey_2.add(point: RoutePoint(latitude: 0.1, longitude: 0.1, time: 12000));
        journey_2.add(point: RoutePoint(latitude: 0.2, longitude: 0.2, time: 13000));
        
        let fos = FileStream(file: Util.GetAppFile(withName: "Test.dat").path, with: FileStream.APPENDABLE);
        _ = fos?.WriteSerialiseable(value: journey_1);
        _ = fos?.WriteSerialiseable(value: journey_2);
        fos?.close();
        
        guard let reader = FileStream(file: Util.GetAppFile(withName: "Test.dat").path) else { print("File I/O Error"); return; }
        
        var mJourneys = [Journey]();
        while let journey = reader.ReadSerialiseable(of: Journey.self) { mJourneys.append(journey); }
        
        print(mJourneys);
        
        let textStream = TextStream();
        print(textStream.Buffer);
        print(textStream.Next);
        
        textStream.Buffer = "1 2 4.5";
        print(textStream.Read(Int));
        print(textStream.Read(Double));
        print(textStream.Read(Double));
        textStream.Write(true);
        textStream.Write(5);
        print(textStream.Buffer);
        textStream.ReSeek();
        textStream.Write(false);
        print(textStream.Buffer);
        textStream.ReSeek();
        print(textStream.Read(Bool));
        print(textStream.Read(Int));
        print(textStream.Read(Double));
        print(textStream.Read(Bool));
                
        
    }

    override func didReceiveMemoryWarning()
    {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }

    @IBAction func onStart(_ sender: UIButton)
    {
        if (!timer.start()) { Util.DisplayMsg(onview: self, title: "ERROR!", showing: "Unable to Start"); }
    }
    
    @IBAction func onStop(_ sender: UIButton)
    {
        if (!timer.stop()) { Util.DisplayMsg(onview: self, title: "ERROR!", showing: "Unable to Stop"); }
    }
    
    @IBAction func onPing(_ sender: UIButton)
    {
        if (!timer.ping()) { Util.DisplayMsg(onview: self, title: "ERROR!", showing: "Unable to Ping"); }
    }
    
    func HandleTimeOut(from watchdog: Int)
    {
        Util.DisplayMsg(onview: self, title: "TimeOut!", showing: "Timer \(watchdog) timed out.");
        if (!timer.stop()) { print("Error in Stopping"); }
    }
}
