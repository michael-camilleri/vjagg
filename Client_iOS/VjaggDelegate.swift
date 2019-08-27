//
//  AppDelegate.swift
//  vjagg
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

/**
 * \brief: This will also be the main file
 */

//For Switching between different Views:
// http://sapandiwakar.in/programatically-set-the-initial-view-controller-using-storyboards/
// http://stackoverflow.com/questions/16702631/how-does-xcode-load-the-main-storyboard (see answer by Beanwah)
// https://www.newventuresoftware.com/blog/organizing-xcode-projects-using-multiple-storyboards

import UIKit

@UIApplicationMain
class VjaggDelegate: UIResponder, UIApplicationDelegate
{
    var window: UIWindow?
    
    private let TAG = "VDeleg"

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplicationLaunchOptionsKey: Any]?) -> Bool
    {
        //Create the Window
        self.window = UIWindow(frame: UIScreen.main.bounds)

        // Initialise the Storyboard Selectively
        let storyboard: UIStoryboard;
        
        if SecureConnector.UserID.isEmpty
        {
            //Then Initialise the Sign-Up Storyboard
            storyboard = UIStoryboard(name: "SignUp", bundle: Bundle.main)
            Debugger.Debug(TAG, "No User ID");
        }
        else
        {
            //Else, just go to main
            storyboard = UIStoryboard(name: "Main", bundle: Bundle.main)
            Debugger.Debug(TAG, "User ID OK - " + SecureConnector.UserID);
        }
        
        //storyboard = UIStoryboard(name: "Debug", bundle: Bundle.main);
        //storyboard = UIStoryboard(name: "Main", bundle: Bundle.main);
        
        //Now start the corresponding View Controller
        let vc : AnyObject! = storyboard.instantiateInitialViewController();
        self.window!.rootViewController = vc as? UIViewController
        self.window!.makeKeyAndVisible();
        
        return true
    }

    func applicationWillResignActive(_ application: UIApplication)
    {
        // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
        // Use this method to pause ongoing tasks, disable timers, and invalidate graphics rendering callbacks. Games should use this method to pause the game.
    }

    func applicationDidEnterBackground(_ application: UIApplication)
    {
        print("2Back");
        // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
        // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
    }

    func applicationWillEnterForeground(_ application: UIApplication)
    {
        // Called as part of the transition from the background to the active state; here you can undo many of the changes made on entering the background.
    }

    func applicationDidBecomeActive(_ application: UIApplication)
    {
        // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
        Debugger.Debug(TAG, "2Front");
    }

    func applicationWillTerminate(_ application: UIApplication)
    {
        Debugger.Debug(TAG, "Term");
    }

}

