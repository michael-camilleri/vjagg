//
//  MainController.swift
//  vjagg
//
//  Created by administrator on 02/02/2017.
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

class MainController: UITabBarController, UITabBarControllerDelegate
{
    static private let CONTROL = 0;
    static private let JOURNEY = 1;
    static private let SUPPORT = 2;
    //static private let PRIVATE = 2;
    
    private var mLastStable = 0;
    private var mJourSync = false;
    private var mPrivSync = false;
    
    private let TAG = "Main";
    
    override func viewDidLoad()
    {
        super.viewDidLoad()
        
        //Set Delegate
        self.delegate = self;
        
        //Set Icons
        //guard let items = tabBar.items, items.count == 2 else { fatalError("Tab Bar Has no Items"); }
        //items[0].image = #imageLiteral(resourceName: "Main");
        //items[0].title = "Main";
        //items[1].image = #imageLiteral(resourceName: "Journeys");
        //items[1].title = "Journeys";
        //items[2].image = #imageLiteral(resourceName: "Help");
        //items[2].title = "Help";
        //items[2].image = #imageLiteral(resourceName: "Personal");
        //items[2].title = "Personal";
        
        //Updated Selected
        mLastStable = MainController.CONTROL;
    }

    override func didReceiveMemoryWarning()
    {
        super.didReceiveMemoryWarning()
    }
    
    func informJourneysChange()
    {
        mJourSync = false;
    }
    
    func informPrivateChange()
    {
        mPrivSync = false;
    }
 
    func requestTabChange()
    {
        selectedIndex = MainController.CONTROL;
        mLastStable = MainController.CONTROL;
    }
    
    func tabBarController(_ : UITabBarController, didSelect viewController: UIViewController)
    {
        switch(selectedIndex)
        {
        case MainController.CONTROL:
            Debugger.Debug(TAG, "Selected Main View");
            mLastStable = MainController.CONTROL;
            (viewController as? ControlView)?.MainViewDelegate = self;
            
        case MainController.JOURNEY:
            Debugger.Debug(TAG, "Selected Journey View");
            if (mJourSync)  //If already initialised and nothing changed (no journeys added)
            {
                Debugger.Debug(TAG, "Sync OK");
                //Still consider possibility that user could have deleted all journeys...
                if Util.AppFileExists(withName: TrackingService.GPS_RPP_FILE) { mLastStable = selectedIndex; }
                else
                {
                    selectedIndex = mLastStable;
                    if let selected = selectedViewController
                    {
                        _ = Util.DisplayMsg(onview: selected, title: "No Journeys", showing: "No Journeys are currently stored. To start Tracking, click on the Track Me Button");
                    }

                }
            }
            else
            {
                Debugger.Debug(TAG, "Need to Sync");
                //Ensure we have Navigation Controller
                guard let nav = viewController as? UINavigationController else { fatalError("No Navigation"); }
                
                //Pop to the Root: otherwise, we cannot update list of journeys. This will entail that there is a possibility of losing data for a journey whose parameters have not been locked in...
                nav.popToRootViewController(animated: false);
                
                //Cast to Journey View
                guard let view = nav.topViewController as? JourneyViewController else { fatalError("Journey View Invalid"); }
                
                //Refresh View
                if (view.Initialise(with: TrackingService.GPS_RPP_FILE)) { mLastStable = selectedIndex; }
                else
                {
                    selectedIndex = mLastStable;
                    if let selected = selectedViewController
                    {
                        _ = Util.DisplayMsg(onview: selected, title: "No Journeys", showing: "No Journeys are currently stored. To start Tracking, click on the Track Me Button");
                    }
                }
                
                //Irrespectively, Journey is now synced
                mJourSync = true;
            }
            
            //In any case set delegate:
            ((viewController as? UINavigationController)?.topViewController as? JourneyViewController)?.MainDelegate = self;
            
        case MainController.SUPPORT:
            Debugger.Debug(TAG, "Selected Support View");
            mLastStable = MainController.SUPPORT;

            /*
        case MainController.HELP:
            
        case MainController.PRIVATE:
            Debugger.Debug(TAG, "Selected Private View");
            if (mPrivSync)  //If already initialised and nothing changed (no journeys added)
            {
                //Still consider possibility that user could have deleted all journeys...
                if Util.AppFileExists(withName: TrackingService.GPS_PER_FILE) { mLastStable = selectedIndex; }
                else
                {
                    selectedIndex = mLastStable;
                    if let selected = selectedViewController
                    {
                        _ = Util.DisplayMsg(onview: selected, title: "No Journeys", showing: "No Journeys are currently stored in your Personal History. To store journeys in your personal history click on any journey within the Journeys view and press \"Store to Personal History\"");
                    }
                }
            }
            else
            {
                //Ensure we have Navigation Controller
                guard let nav = viewController as? UINavigationController else { fatalError("No Navigation"); }
                
                //Pop to the Root: otherwise, we cannot update list of journeys. This will entail that there is a possibility of losing data for a journey whose parameters have not been locked in...
                nav.popToRootViewController(animated: false);
                
                //Cast to Journey View
                guard let view = nav.topViewController as? JourneyViewController else { fatalError("Journey View Invalid"); }
                
                //Refresh View
                if (view.Initialise(with: TrackingService.GPS_PER_FILE)) { mLastStable = selectedIndex; }
                else
                {
                    selectedIndex = mLastStable;
                    if let selected = selectedViewController
                    {
                        _ = Util.DisplayMsg(onview: selected, title: "No Journeys", showing: "No Journeys are currently stored in your Personal History. To store journeys in your personal history click on any journey within the Journeys view and press \"Store to Personal History\"");
                    }
                }
                
                //Irrespectively, Private is now synced
                mPrivSync = true;
            }
           */
        default:
            Debugger.Warn(TAG, "Wrong Tab Bar Clicked");
        }
    }
}
