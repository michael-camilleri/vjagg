//
//  JourneyDetailViewController.swift
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

import UIKit
import MapKit

class JourneyDetailViewController: UIViewController, UITableViewDelegate, UITableViewDataSource, MKMapViewDelegate
{
    var mJourney : Journey? = nil;
    var mModeCpy : [Bool] = [];
    var mPurpCpy : Int = -1;
    
    private let TAG = "JDVC";

    private static let TRIP_MODE = 1;
    private static let TRIP_PURP = 2;
    
    @IBOutlet weak var mProperties: UITableView!
    @IBOutlet weak var mMapView: MKMapView!
    
    override func viewDidLoad()
    {
        super.viewDidLoad()
        
        //Ensure a Valid Journey
        guard let journey = mJourney else { fatalError("Invalid Journey"); }
        
        //Journey Copies
        mModeCpy = journey.Mode;
        mPurpCpy = journey.Purpose;

        //Table Properties
        mProperties.dataSource = self;
        mProperties.delegate   = self;
        mProperties.isScrollEnabled = false;
        
        //Map Properties
        mMapView.delegate = self;
        
        //Initialise Map
        mMapView.region = mJourney!.getRegion(0.005);
        mMapView.add(mJourney!.getPolyline());
    }

    override func didReceiveMemoryWarning()
    {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }
    
    //MARK: UITableViewDataSource Protocol
    func numberOfSections(in tableView: UITableView) -> Int
    {
        return 1
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int
    {
        return 2;
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell
    {
        //Load Cell View
        let cell = tableView.dequeueReusableCell(withIdentifier: "UITableViewCell", for: indexPath);
        
        //Configure the cell Title
        cell.textLabel?.text = indexPath.row == 0 ? "Mode" : "Purpose";
        
        //Configure the detail cell Title
        cell.detailTextLabel?.text = indexPath.row == 0 ? Journey.ModeStr(mModeCpy) : Journey.PurpStr(mPurpCpy);
        
        //Add Tag for identification
        cell.tag = indexPath.row;
        
        return cell;
    }
    
    //MARK : Map View Delegation
    func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer
    {
        if overlay is MKPolyline
        {
            let polylineRenderer = MKPolylineRenderer(overlay: overlay)
            polylineRenderer.strokeColor = UIColor.blue;
            polylineRenderer.lineWidth = 5
            return polylineRenderer
        }
        else
        {
            return MKOverlayRenderer();
        }
    }

    //MARK : Navigation
    override func prepare(for segue: UIStoryboardSegue, sender: Any?)
    {
        super.prepare(for: segue, sender: sender);
        
        switch (sender)
        {
        //If moving forward to selector, then initialise next view
        case is UITableViewCell:
            let row = sender as! UITableViewCell;
            guard let property_view = segue.destination as? MCSelectorController else { fatalError("Incorrect View"); }
            
            property_view.mOptions = row.tag == 0 ? Journey.MODE_SELECT_STR : Journey.PURP_SELECT_STR;
            property_view.mSingle  = row.tag == 1;  //!< Single if purpose
            property_view.mSelections = row.tag == 0 ? mModeCpy : mPurpCpy;
            property_view.mTag = row.tag == 0 ? JourneyDetailViewController.TRIP_MODE : JourneyDetailViewController.TRIP_PURP;
            
        //If Moving backward on Done press, then update actual journey
        case is UIBarButtonItem:
            guard segue.destination is JourneyViewController else { fatalError("Incorrect View"); }
            mJourney?.Mode = mModeCpy;
            mJourney?.Purpose = mPurpCpy;
            
        default:
            fatalError("Incorrect Sender");
        }
    }

    @IBAction func unwindPropertyValues(_ sender:UIStoryboardSegue)
    {
        guard let source = sender.source as? MCSelectorController else { Debugger.Error(TAG, "Nothing returned"); return; }
        
        if (source.mTag == JourneyDetailViewController.TRIP_MODE)
        {
            mModeCpy = source.mSelections as! [Bool];
        }
        else
        {
            mPurpCpy = source.mSelections as! Int;
        }
        
        //Force Reload of data
        mProperties.reloadData();        
    }
}
