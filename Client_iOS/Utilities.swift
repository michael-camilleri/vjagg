//
//  Utilities.swift
//  vjagg
//
//  Created by Michael Camilleri on 21/12/2016.
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


extension Data
{
    init<T>(from value:T)
    {
        var val = value;
        self.init(buffer: UnsafeBufferPointer(start: &val, count: 1));
    }
    
    init<T>(fromArray values:[T])
    {
        var vals = values;
        self.init(buffer: UnsafeBufferPointer(start: &vals, count: vals.count));
    }
    
    func to<T>(type: T.Type) -> T
    {
        return self.withUnsafeBytes
            {
                $0.pointee;
            }
    }
    
    func toArray<T>(of type: T.Type) -> [T]
    {
        return self.withUnsafeBytes
            {
                [T](UnsafeBufferPointer(start: $0, count: self.count/MemoryLayout<T>.stride));
            }
    }
}

extension String
{
    static let NF: Int = -1;    //!< Representation of not Found
    
    var count : Int { return characters.count; }
    
    /**
     * \brief Find a character in a String
     */
    func find(_ char: Character) -> Int
    {
        if let index = characters.index(of: char)
        {
            return distance(from: self.startIndex, to: index);
        }
        else
        {
            return -1;
        }
    }
    
    func substr(to: Int) -> String
    {
        let tIdx = self.index(self.startIndex, offsetBy: min(to, count-1));
        return self.substring(with: Range<String.Index>(uncheckedBounds: (lower: self.startIndex, upper:tIdx)));
    }
    
    func substr(from: Int) -> String
    {
        if from < count
        {
            return self.substring(with: Range<String.Index>(uncheckedBounds: (lower: self.index(self.startIndex, offsetBy: from), upper:self.endIndex)));
        }
        else
        {
            return "";
        }
    }
    
}

class Util: NSObject
{
    static private let docs_dir = (FileManager().urls(for: .documentDirectory, in: .userDomainMask).first!)
    
    static func GetAppFile(withName name:String) -> URL
    {
        return docs_dir.appendingPathComponent(name);
    }
    
    static func DeleteAppFile(withName name:String) -> Bool
    {
        do { try FileManager().removeItem(at:docs_dir.appendingPathComponent(name)); return true; }
        catch { return false; }
    }
    
    static func AppFileExists(withName name:String) -> Bool
    {
        return FileManager.default.fileExists(atPath: docs_dir.appendingPathComponent(name).path)
    }
    
    static func EnableWait(onview view:UIViewController, showing msg:String) -> UIAlertController
    {
        let alert = UIAlertController(title: nil, message: msg, preferredStyle: .alert);
        alert.view.tintColor = UIColor.black;
        
        let load_indic = UIActivityIndicatorView(frame: CGRect(x:50,y:50,width:10,height:5));
        load_indic.hidesWhenStopped = true;
        load_indic.activityIndicatorViewStyle = .gray;
        load_indic.startAnimating();
        
        alert.view.addSubview(load_indic);
        view.present(alert, animated: true, completion: nil);
        
        return alert;
    }
    
    /**
     * \brief Utility function for displaying an Alert Dialog with optional Cancel/OK
     */
    static func DisplayMsg(onview view:UIViewController, title:String, showing msg:String,
                           okTxt:String = "OK",
                           onOK:((UIAlertAction) -> Swift.Void)? = nil,
                           showCancel:Bool = false,
                           onCancel:((UIAlertAction) -> Swift.Void)? = nil) -> UIAlertController
    {
        let alert = UIAlertController(title: title, message: msg, preferredStyle: .alert);
        alert.view.tintColor = UIColor.black;
        if (showCancel) { alert.addAction(UIAlertAction(title: "Cancel", style: .cancel, handler: onCancel)); }
        alert.addAction(UIAlertAction(title: okTxt, style: .default, handler: onOK));
        
        view.present(alert, animated: true, completion: nil)
        return alert;
    }
    
    static func GetCurrentMSTime() -> Int
    {
        return Int(Date().timeIntervalSince1970 * 1000);
    }
    
    static func FormateDate(from mstime : Int) -> String
    {
        let dateFormatter = DateFormatter();
        dateFormatter.dateStyle = .long;
        dateFormatter.timeStyle = .none;
        dateFormatter.locale    = Locale(identifier: "en_UK")
        
        let date = Date(timeIntervalSince1970: TimeInterval(mstime)/1000.0);
        
        return dateFormatter.string(from: date);
    }
    
    static func FormateTime(from mstime : Int) -> String
    {
        let timeFormatter = DateFormatter();
        timeFormatter.dateFormat = "HH:mm:ss.SSS";
        
        let time = Date(timeIntervalSince1970: TimeInterval(mstime)/1000.0);
        
        return timeFormatter.string(from: time);
    }
    
    static func AsyncTask<Rtype>(_ task: @escaping ()->Rtype, _ result: @escaping (Rtype)->Void)
    {
        DispatchQueue.global(qos: .utility).async
        {
            let resulting = task();
            DispatchQueue.main.async { result(resulting); }
        }
    }

}
