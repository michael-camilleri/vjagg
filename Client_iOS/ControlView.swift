//
//  MainviewViewController.swift
//  vjagg
//
//  https://www.raywenderlich.com/143128/background-modes-tutorial-getting-started
//
//  Created by Michael Camilleri on 16/12/2016.
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

class ControlView: UIViewController, StateListener
{
    private let TAG = "Ctrl";
    
    var MainViewDelegate : MainController?; //Delegate for informing of change in journeys
    
    //!< Computed Properties
    var View : UIViewController { return self; }
    
    //!< Member References
    @IBOutlet weak var mTrackMe: UIButton!
    @IBOutlet weak var mStopTracking: UIButton!
    
    //!< Member variables
    var mAlert : UIAlertController? = nil;
    
    override func viewDidLoad()
    {
        super.viewDidLoad()
        
        //Update UI and force instantiation of Singleton!
        UpdateUI();
    }

    override func didReceiveMemoryWarning()
    {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }
    

    @IBAction func onTrackMe(_ sender: UIButton)
    {
        switch (TrackingService.Start(notifyMe: self))
        {
        case TrackerStatus.done:
            Debugger.Debug(TAG, "Already Active");
            UpdateUI();
            
        case TrackerStatus.wait:
            Debugger.Debug(TAG, "Waiting for authorisation confirmation");
            
        case TrackerStatus.fail:
            Debugger.Warn(TAG, "Failure!");
            _ = Util.DisplayMsg(onview: self, title: "Error", showing: "Unable to Start Location Services!");
        }
    }
    
    private func UpdateUI(completion: (() -> Swift.Void)? = nil)
    {
        //First Disable any Waiting Alert:
        mAlert?.dismiss(animated: true, completion: completion); mAlert = nil;
        
        //Now update Buttons
        mTrackMe.isEnabled      = !TrackingService.Alive;
        mStopTracking.isEnabled = TrackingService.Alive;
        //mViewTrips.isEnabled    = !TrackingService.Alive;
    }
    
    @IBAction func onStopTracking(_ sender: UIButton)
    {
        switch (TrackingService.Stop(notifyMe: self))
        {
        case TrackerStatus.done:
            Debugger.Debug(TAG, "Stopped");
            UpdateUI();
            
        case TrackerStatus.wait:
            Debugger.Debug(TAG, "Waiting for PostProcessing");
            mAlert = Util.EnableWait(onview: self, showing: "Post-Processing");
            
        case TrackerStatus.fail:
            Debugger.Warn(TAG, "Failure!");
            _ = Util.DisplayMsg(onview: self, title: "Error", showing: "Unable to Stop Location Services!");
        }
    }
    
    //MARK : StateListener Protocol
    func OnStart(success: TrackerStatus)
    {
        UpdateUI();
    }
    
    func OnPostProcess(success:TrackerStatus)
    {
        if success == TrackerStatus.done { MainViewDelegate?.informJourneysChange(); }
        
        UpdateUI()
            {
                if (success == TrackerStatus.fail) { _ = Util.DisplayMsg(onview: self, title: "Warning", showing: "No Journeys seem to have been detected."); }
            }
    }
    
    
}
