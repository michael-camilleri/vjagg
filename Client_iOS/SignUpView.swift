//
//  SignUpView.swift
//  vjagg
//
//  Created by Michael Camilleri on 19/12/2016.
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

class SignUpOKView: UIViewController
{
    @IBOutlet weak var mLabelID: UILabel!
    var mID : String = "";
    
    override func viewDidLoad()
    {
        mLabelID.text = mID + "*";
    }
    
    @IBAction func onContinue(_ sender: UIButton)
    {
        let ctrl = UIStoryboard(name: "Main", bundle: nil).instantiateInitialViewController()! as UIViewController;
        self.present(ctrl, animated: true, completion: nil);
    }
    
}

class SignUpView: UIViewController, UIPickerViewDelegate, UIPickerViewDataSource, SCDelegate
{
    class UserDetails
    {
        var mGender: Int = 0;
        var mAgeGrp: Int = SignUpView.MINAGE;
        var mRelUni: Int = 0;
        var mCarAcc: Int = 0;
    }
    
    //!< Constant Definitions
    static let GENDER: [String] = ["Male", "Female"];
    static let AGEGRP: [String] = ["-17", "18-24", "25-40", "41-60", "61+"];
    static let RELUNI: [String] = ["Student", "Academic", "Admin", "Visitor", "None"];
    static let ACCCAR: [String] = ["No", "Yes"];
    static let MINAGE: Int = 1;
    
    @IBOutlet weak var GenderPicker:    UIPickerView!
    @IBOutlet weak var AgePicker:       UIPickerView!
    @IBOutlet weak var RelationPicker:  UIPickerView!
    @IBOutlet weak var CarAccessPicker: UIPickerView!
    
    private var mUser  : UserDetails = UserDetails();
    private var mID    : String = "";
    private var mAlert : UIAlertController? = nil;
    
    private let TAG = "SignUp";
    
    override func viewDidLoad()
    {
        //Call base method
        super.viewDidLoad()

        //Setup Pickers
        self.GenderPicker.dataSource = self;
        self.GenderPicker.delegate   = self;
        
        self.AgePicker.dataSource = self;
        self.AgePicker.delegate   = self;
        
        self.RelationPicker.dataSource = self;
        self.RelationPicker.delegate   = self;
        
        self.CarAccessPicker.dataSource = self;
        self.CarAccessPicker.delegate   = self;
    }

    override func didReceiveMemoryWarning()
    {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }
    

    // MARK: UIPickerViewDataSource Protocol
    func numberOfComponents(in pickerView: UIPickerView) -> Int
    {
        return 1;
    }
    
    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int
    {
        switch(pickerView)
        {
            case self.GenderPicker:
                return SignUpView.GENDER.count;
            
            case self.AgePicker:
                return SignUpView.AGEGRP.count - SignUpView.MINAGE;
            
            case self.RelationPicker:
                return SignUpView.RELUNI.count;
            
            case self.CarAccessPicker:
                return SignUpView.ACCCAR.count;
            
            default:
                fatalError("Incorrect Picker");
        }

    }
    
    // MARK: UIPickerViewDelegate Protocol
    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String?
    {
        switch(pickerView)
        {
            case self.GenderPicker:
                return SignUpView.GENDER[row];
            
            case self.AgePicker:
                return SignUpView.AGEGRP[row+SignUpView.MINAGE];
            
            case self.RelationPicker:
                return SignUpView.RELUNI[row];
            
            case self.CarAccessPicker:
                return SignUpView.ACCCAR[row];
            
            default:
                fatalError("Incorrect Picker");
        }

    }
    
    func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int)
    {
        switch(pickerView)
        {
            case self.GenderPicker:
                self.mUser.mGender = row;
            
            case self.AgePicker:
                self.mUser.mAgeGrp = row + SignUpView.MINAGE;
            
            case self.RelationPicker:
                self.mUser.mRelUni = row;
            
            case self.CarAccessPicker:
                self.mUser.mCarAcc = row;
            
            default:
                fatalError("Incorrect Picker");
        }

    }
    
    
    @IBAction func AttemptSignUp(_ sender: UIButton)
    {
        mAlert = Util.EnableWait(onview: self, showing: "Connecting with Server");
        SecureConnector.SimulateID(forUser: mUser, informing: self); //TODO Change back to RequestID
    }
    
    
    // MARK: - Navigation
    override func prepare(for segue: UIStoryboardSegue, sender: Any?)
    {
        super.prepare(for: segue, sender: sender);
        
        guard sender == nil else { fatalError("Segue Invoked Prematurely"); }
        guard let nav_ctrl = segue.destination as? UINavigationController else { fatalError("Incorrect Navigation Controller"); }
        guard let sign_ok = nav_ctrl.topViewController as? SignUpOKView else { fatalError("Incorrect View"); }

        sign_ok.mID = self.mID;
    }
    
    
    func OnDone(action: Int, with: Bool, containing: String?, other:AnyObject?)
    {
        switch action
        {
        case SecureConnector.OP_REQ_ID:
            //In any case remove alert
            mAlert?.dismiss(animated: false)
            {
                self.mAlert = nil;
                if with
                {
                    self.mID = containing!;
                    self.performSegue(withIdentifier: "SegOnIDOK", sender: nil);
                }
                else
                {
                    self.mID = "";
                    print("Error - Unable to retrieve ID due to (" + containing! + ")");
                }
                
            }

        default:
            Debugger.Error(TAG, "Retrieval Failed: (" + containing! + ")")
        }
    }
}
