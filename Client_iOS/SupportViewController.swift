//
//  SupportViewController.swift
//  vjagg
//
//  Created by Michael Camilleri on 07/02/2017.
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

class SupportViewController: UITableViewController
{
    @IBOutlet weak var mUserIDTab: UITableViewCell!
    @IBOutlet weak var mVersionTab: UITableViewCell!
    
    override func viewDidLoad()
    {
        super.viewDidLoad()

        // Do any additional setup after loading the view.
        mUserIDTab.detailTextLabel?.text = SecureConnector.UserID;
        mVersionTab.detailTextLabel?.text = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Error N/A";
    }

    override func didReceiveMemoryWarning()
    {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }
    
    
    

    /*
    // MARK: - Navigation

    // In a storyboard-based application, you will often want to do a little preparation before navigation
    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        // Get the new view controller using segue.destinationViewController.
        // Pass the selected object to the new view controller.
    }
    */

}
