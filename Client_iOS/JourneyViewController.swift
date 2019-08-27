//
//  JourneyViewControllerTableViewController.swift
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
//_ = Util.DisplayMsg(onview: self, title: "Error", showing: "No Journeys to Display"); //TODO REDO onOK : { _ in self.navigationController?.tabBarController?.selectedIndex = 0 }

import UIKit

class JourneyViewController: UITableViewController, SCDelegate
{
    //==================== Properties =====================//
    private var mHistoryFile = "";   //!< History File: Needs to be set before executing segue...
    
    private var mJourneys = [Journey]();    //!< Will store the list of journeys.
    private var mSelected = [Journey]();
    
    private var mDelete : UIBarButtonItem!;
    private var mUpload : UIBarButtonItem!;
    
    private var mAlert : UIAlertController? = nil;
    
    private let TAG = "JVC";
    
    var MainDelegate : MainController?;
    
    func Initialise(with historyFile:String) -> Bool
    {
        //First delete any previous journeys
        mJourneys.removeAll();

        //!< Load the Journeys from the respective file
        guard let reader = FileStream(file: Util.GetAppFile(withName: historyFile).path, with: FileStream.READABLE) else { Debugger.Warn(TAG, "No File"); return false; }
        
        //!< Now load the actual journeys
        while let journey = reader.ReadSerialiseable(of: Journey.self) { mJourneys.append(journey); }
        tableView.reloadData();
        
        //Finally keep track of History File and return ok
        mHistoryFile = historyFile;
        return true;
    }
    
    override func viewDidLoad()
    {
        super.viewDidLoad()

        //Add Edit Button and subsequently the delete/upload
        navigationItem.rightBarButtonItem = editButtonItem;
        tableView.allowsMultipleSelectionDuringEditing = true;
        
        mDelete = UIBarButtonItem(barButtonSystemItem: UIBarButtonSystemItem.trash, target: self, action: #selector(deleteRows));
        mUpload = UIBarButtonItem(barButtonSystemItem: UIBarButtonSystemItem.reply, target: self, action: #selector(uploadRows));
    }

    override func setEditing(_ editing: Bool, animated: Bool)
    {
        if (editing) { navigationItem.setLeftBarButtonItems([mDelete, mUpload], animated: true); }
        else         { navigationItem.setLeftBarButtonItems(nil, animated: true); }
        
        super.setEditing(editing, animated: animated);
    }
    
    func deleteRows()
    {
        if let selected = tableView.indexPathsForSelectedRows
        {
            //First Delete Items (in descending order, irrespective of tap order)
            for row in selected.sorted(by: >)
            {
                mJourneys.remove(at: row.row);
            }
            
            //Store to file
            guard flushJourneys() else { _ = Util.DisplayMsg(onview: self, title: "File I/O Error", showing: "The application was unable to save any changes you may have commited. Ensure that the application has full Read/Write Access"); return; } //TODO consider switching views...
            
            //Now Delete actual rows
            tableView.deleteRows(at: selected, with: .fade)
            
            //Next update tags
            for i in 0 ..< tableView.numberOfRows(inSection: 0)
            {
                tableView.cellForRow(at: IndexPath(row: i, section: 0))?.tag = i;
            }
            
            //Now if none left...
            if tableView.numberOfRows(inSection: 0) < 1
            {
                setEditing(false, animated: true);
                _ = Util.DisplayMsg(onview: self, title: "No Journeys", showing: "All Journeys have been deleted: Switching to Main View", onOK:{ _ in self.MainDelegate?.requestTabChange(); })
            }
        }
    }
    
    func uploadRows()
    {
        if let selected = tableView.indexPathsForSelectedRows
        {
            mAlert = Util.EnableWait(onview: self, showing: "Uploading Journeys");
            
            //Build (Sorted) List of Selected Journeys
            mSelected = [Journey]();
            for idx in selected.sorted(by: >) { mSelected.append(mJourneys[idx.row]); }
            
            //Send Request to Delegate
            SecureConnector.Upload(journeys: mSelected, informing: self);
        }
    }

    func OnDone(action: Int, with result:Bool, containing data:String?, other:AnyObject?)
    {
        guard action == SecureConnector.OP_UPLOAD else { Debugger.Error(TAG, "Incorrect Action"); return; }
        
        guard let selected = tableView.indexPathsForSelectedRows?.sorted(by: >) else { fatalError("Selection No Longer Valid"); }
        
        if (result)
        {
            //All were successfull: delete all rows
            for row in selected
            {
                mJourneys.remove(at: row.row);
            }
            
            //Store to file
            guard flushJourneys() else { _ = Util.DisplayMsg(onview: self, title: "File I/O Error", showing: "The application was unable to save any changes you may have commited. Ensure that the application has full Read/Write Access"); return; } //TODO consider switching views...
            
            //Now Delete actual rows
            tableView.deleteRows(at: selected, with: .fade)
            
            //Next update tags
            for i in 0 ..< tableView.numberOfRows(inSection: 0)
            {
                tableView.cellForRow(at: IndexPath(row: i, section: 0))?.tag = i;
            }
            
            //Now Remove Alert
            mAlert?.dismiss(animated: true)
            {
                //Now if none left...
                if self.tableView.numberOfRows(inSection: 0) < 1
                {
                    self.setEditing(false, animated: true);
                    _ = Util.DisplayMsg(onview: self, title: "No Journeys", showing: "All Journeys have been uploaded and removed from the internal cache: Switching to Main View", onOK:{ _ in self.MainDelegate?.requestTabChange(); })
                }

            }
            mAlert = nil;
        }
        else
        {
            Debugger.Error(TAG, "Unable to Upload some Journeys");
            
            guard let failed = other as? Int, failed > 0 else { Debugger.Warn(TAG, "No Information about Successful Uploads"); return; }
            let uploaded = selected[0..<max(selected.count - failed, 0)];
            
            for idx in uploaded
            {
                mJourneys.remove(at: idx.row);
            }
            
            //Store to file
            guard flushJourneys() else { _ = Util.DisplayMsg(onview: self, title: "File I/O Error", showing: "The application was unable to save any changes you may have commited. Ensure that the application has full Read/Write Access"); return; } //TODO consider switching views...
            
            //Now Delete actual rows
            tableView.deleteRows(at: Array(uploaded), with: .fade)

            //Finally, update tags
            for i in 0 ..< tableView.numberOfRows(inSection: 0)
            {
                tableView.cellForRow(at: IndexPath(row: i, section: 0))?.tag = i;
            }
            
            //Now Remove Alert
            mAlert?.dismiss(animated: true){ _ = Util.DisplayMsg(onview: self, title: "Error", showing: "The application was unable to upload some of the journeys. Please ensure that you are connected to the internet or try again later.\n Reason for Failure: " + (data ?? "N/A")); };
        }
    }
    
    override func didReceiveMemoryWarning()
    {
        super.didReceiveMemoryWarning()
    }

    
    // MARK: - Table view data source
    override func numberOfSections(in tableView: UITableView) -> Int
    {
        return 1
    }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int
    {
        return mJourneys.count;
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell
    {
        //Load Cell View
        guard let cell = tableView.dequeueReusableCell(withIdentifier: "JourneyViewCell", for: indexPath) as? JourneyCell else { fatalError("Dequed Cell not usable"); }

        //Configure the cell
        cell.mDateLabel.text = mJourneys[indexPath.row].getDate();
        cell.mTimesLabel.text = mJourneys[indexPath.row].getTimes();
        cell.tag = indexPath.row;
        
        return cell
    }
    
    override func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCellEditingStyle, forRowAt indexPath: IndexPath)
    {
        if editingStyle == .delete
        {
            // Delete the row from the data source
            mJourneys.remove(at: indexPath.row);
            
            //Store to file
            guard flushJourneys() else { _ = Util.DisplayMsg(onview: self, title: "File I/O Error", showing: "The application was unable to save any changes you may have commited. Ensure that the application has full Read/Write Access"); return; } //TODO consider switching views...
            
            //Delete Rows from the Table View
            tableView.deleteRows(at: [indexPath], with: .fade)
            
            //Now for all rows from indexpath, update the tag
            for i in indexPath.row ..< tableView.numberOfRows(inSection: 0)
            {
                tableView.cellForRow(at: IndexPath(row: i, section: 0))?.tag = i;
            }
            
            //Finally if none left...
            if self.tableView.numberOfRows(inSection: 0) < 1
            {
                _ = Util.DisplayMsg(onview: self, title: "No Journeys", showing: "All Journeys have been deleted: Switching to Main View", onOK:{ _ in self.MainDelegate?.requestTabChange(); })
            }

        }
    }

    // MARK: - Navigation
    override func shouldPerformSegue(withIdentifier identifier: String, sender: Any?) -> Bool
    {
        return (identifier == "DetailJourney" && !isEditing);
    }
    
    // In a storyboard-based application, you will often want to do a little preparation before navigation
    override func prepare(for segue: UIStoryboardSegue, sender: Any?)
    {
        super.prepare(for: segue, sender: sender);
        
        guard let row = sender as? UITableViewCell else { fatalError("Cell Error"); }
        guard let next_view = segue.destination as? JourneyDetailViewController else { fatalError("Wrong View"); }
            
        next_view.mJourney = mJourneys[row.tag];
    }
    
    @IBAction func unwindJourneyParameters(_ sender:UIStoryboardSegue)
    {
        guard sender.source is JourneyDetailViewController else { Debugger.Error(TAG, "Incorrect Type returned"); return; }
        
        //Store All
        if (!flushJourneys())
        {
            _ = Util.DisplayMsg(onview: self, title: "I/O Error", showing: "Journey Details could not be updated due to System File Error");
        }
    }
    
    func flushJourneys() -> Bool
    {
        Debugger.Debug(TAG, "Persisting to File");
        
        if mJourneys.count < 1 { _ = Util.DeleteAppFile(withName: mHistoryFile); return true; }
        
        //Open file in write mode
        guard let writer = FileStream(file: Util.GetAppFile(withName: mHistoryFile).path, with: FileStream.WRITEABLE) else { return false; }
        
        //Store all journeys
        for journey in mJourneys { if (!writer.WriteSerialiseable(value: journey)) { Debugger.Warn(TAG, "Some Journeys were not written"); } }
        
        //Close Writer
        writer.close();
        
        //Return ok
        return true;
    }
    
}
