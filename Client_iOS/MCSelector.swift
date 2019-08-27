//
//  MCSelector.swift
//  vjagg
//
//  Created by Michael Camilleri on 20/01/2017.
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

//!< Dummy MCSelector Cell
class MCSelectorCell : UITableViewCell
{
    override func awakeFromNib()
    {
        super.awakeFromNib()
        // Initialization code
    }
    
    override func setSelected(_ selected: Bool, animated: Bool)
    {
        super.setSelected(selected, animated: animated)
    }
}

//protocol MCSelectorDelegate
//{
//    func change (idx:Int, selected:Bool);
//}

class MCSelector: UITableView, UITableViewDelegate, UITableViewDataSource
{
    private var mOptions    : [String] = [];
    private var mMultiple   : Bool     = true;
    private var mSelections : [Bool]   = [];
    private var mSingleSel  : Int      = -1;
    
    var Selected : [Bool] { return mMultiple ? mSelections : []; }
    var Single   : Int    { return mMultiple ? -1 : mSingleSel; }
    
    private let TAG = "MCS";
    
    /**
     * \brief Initialises the Selector View
     * \detail Sets up the Selector View according to the user's requirements. 
     * @param   options     The List of Strings to display on the rows (selection choices)
     * @param   radio       Indicates whether it is a radio selection or a multiple-selection
     * @param   selections  This is an integer showing the selection if radio or an array of bool if multiple
     */
    func initialise(options:[String], radio:Bool, selections:Any?) -> Bool
    {
        mOptions  = options;
        mMultiple = !radio;
        
        self.allowsMultipleSelection = mMultiple;
        
        self.delegate = self;
        self.dataSource = self;
        
        register(MCSelectorCell.self, forCellReuseIdentifier: "MCSelectorCell");
        
        //Now set up cell selections
        if (radio)
        {
            guard let sel = selections as? Int else { Debugger.Error(TAG, "Int"); return false; }
            mSingleSel = sel;   //Store
        }
        else
        {
            guard let selection = selections as? [Bool] else { Debugger.Error(TAG, "[Bool]"); return false; }
            mSelections = selection;
        }
        
        return true;
    }
    
    //MARK: UITableViewDataSource Protocol
    func numberOfSections(in tableView: UITableView) -> Int
    {
        return 1
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int
    {
        return mOptions.count;
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell
    {
        //Load Cell View
        let cell = tableView.dequeueReusableCell(withIdentifier: "MCSelectorCell", for: indexPath);
        
        //Configure the cell
        cell.textLabel?.text = mOptions[indexPath.row];
        
        //Selection
        cell.accessoryType  = cell.isSelected ? .checkmark : .none;

        return cell;
    }
    
    func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath)
    {
        if (mMultiple)
        {
            if mSelections[indexPath.row]
            {
                selectRow(at: indexPath, animated: true, scrollPosition: .none);
                cell.accessoryType = .checkmark;
            }
        }
        else
        {
            if mSingleSel == indexPath.row
            {
                selectRow(at: indexPath, animated: true, scrollPosition: .none);
                cell.accessoryType = .checkmark;
            }
        }
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath)
    {
        self.cellForRow(at: indexPath)?.accessoryType = .checkmark;
        if (mMultiple) { mSelections[indexPath.row] = true; }
        else           { mSingleSel = indexPath.row; }
    }
    
    func tableView(_ tableView: UITableView, didDeselectRowAt indexPath: IndexPath)
    {
        self.cellForRow(at: indexPath)?.accessoryType = .none;
        if (mMultiple) { mSelections[indexPath.row] = false; } //Do nothing for single selection...
    }
}

class MCSelectorController : UITableViewController
{
    var mOptions    : [String] = [];
    var mSingle     : Bool     = false;
    var mSelections : Any?     = nil;
    var mTag        : Int      = -1;    //May be used to identify the request
    
    override func viewDidLoad()
    {
        super.viewDidLoad()
        
        //Now Initialise our own way of life...
        _ = (tableView as? MCSelector)?.initialise(options: mOptions, radio:mSingle, selections: mSelections);
    }
    
    override func didReceiveMemoryWarning()
    {
        super.didReceiveMemoryWarning()
    }
    
     // In a storyboard-based application, you will often want to do a little preparation before navigation
    override func prepare(for segue: UIStoryboardSegue, sender: Any?)
    {
        super.prepare(for: segue, sender: sender);
        
        mSelections = mSingle ? ((tableView as? MCSelector)?.Single) : ((tableView as? MCSelector)?.Selected);
    }


}
