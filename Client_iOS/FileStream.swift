//
//  FileStream.swift
//  vjagg
//
//  Defines File Input/Output Operations
//
//  Created by Michael Camilleri on 03/01/2017.
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

import Foundation

protocol Serialiseable
{
    init?(from file: FileStream)
    func toFile(file: FileStream) -> Bool
}

class FileStream : NSObject
{
    static let READABLE   = 1;  //Open File in Read-Only Mode: if it does not exist it fails
    static let WRITEABLE  = 2;  //Open File in Write-Only Mode: if it does not exist it is created: else it is overwritten
    static let APPENDABLE = 3;  //Open File in Write-Only Mode: if it does not exist it is created: else writing happens at the end.
    
    private var mFileHandle : FileHandle?;
    private let mMode       : Int;
    
    private let TAG = "FS";
    
    init?(file name:String, with option:Int = READABLE)
    {
        switch(option)
        {
            case FileStream.READABLE:
                guard let fileHandle = FileHandle(forReadingAtPath: name) else { Debugger.Error(TAG, "Read Open Fail"); return nil; }
                mFileHandle = fileHandle;
                mMode = option;
            
            case FileStream.WRITEABLE:
                //Create Empty File:
                if (!FileManager.default.createFile(atPath: name, contents: nil)) { Debugger.Error(TAG, "Write Create Fail"); return nil; }
                    
                //Now Open File for Writing
                guard let fileHandle = FileHandle(forWritingAtPath: name) else { Debugger.Error(TAG, "Write Open Fail"); return nil; }
                mFileHandle = fileHandle;
                mMode = option;
            
            case FileStream.APPENDABLE:
                //Create Empty File (ONLY if it does not exist):
                if !FileManager.default.fileExists(atPath: name)
                {
                    if (!FileManager.default.createFile(atPath: name, contents: nil)) { Debugger.Error(TAG, "Write Create Fail"); return nil; }
                }
                
                //Now Open File for Writing
                guard let fileHandle = FileHandle(forWritingAtPath: name) else { Debugger.Error(TAG, "Append Open Fail"); return nil; }
                mFileHandle = fileHandle;
                mMode = option;
            
                //Finally seek to end of file
                mFileHandle!.seekToEndOfFile();

            default:
                return nil;
        }
    }
    
    //!< Reading Functions
    func Read<T>(type: T.Type) -> T?
    {
        //Check that readable
        guard mMode == FileStream.READABLE else { return nil; }

        //Attempt read
        guard let data = mFileHandle?.readData(ofLength: MemoryLayout<T>.stride), data.count > 0 else { return nil; }
        
        return data.to(type: T.self)
        
    }
    
    func ReadArray<T>(of type: T.Type) -> [T]?
    {
        //Check that readable
        guard mMode == FileStream.READABLE else { return nil; }
        
        //First Read Integer size
        guard let dSize = mFileHandle?.readData(ofLength: MemoryLayout<Int>.stride), dSize.count > 0 else { return nil; }
        let iSize = dSize.to(type: Int.self);
        
        //Now Read Actual Array
        guard let data = mFileHandle?.readData(ofLength: MemoryLayout<T>.stride * iSize), data.count == MemoryLayout<T>.stride * iSize else { return nil; }

        //Finally Format
        return data.toArray(of: T.self)
    }
    
    func ReadSerialiseable<T: Serialiseable>(of type: T.Type) -> T?
    {
        //Ensure that readable
        guard mMode == FileStream.READABLE else { return nil; }
        
        //Attempt read
        return T(from: self);
    }
    
    func ReadSerialiseableArray<T: Serialiseable>(of type: T.Type) -> [T]?
    {
        //Check that readable
        guard mMode == FileStream.READABLE else { return nil; }
        
        //First Read Integer size
        guard let dSize = mFileHandle?.readData(ofLength: MemoryLayout<Int>.stride), dSize.count > 0 else { return nil; }
        let iSize = dSize.to(type: Int.self);

        //Now Create Array and read individual items
        var array = [T]();
        for _ in 0..<iSize
        {
            guard let element = T(from: self) else { return nil; }
            array.append(element);
        }
        
        //Return
        return array;
    }

    func Write<T>(value: T) -> Bool
    {
        //Check that writeable
        guard mMode == FileStream.WRITEABLE || mMode == FileStream.APPENDABLE else { return false; }
        
        //Now Write the Data Type
        mFileHandle?.write(Data.init(from: value))
        
        //Return True
        return true;
    }
    
    func WriteArray<T>(values: [T]) -> Bool
    {
        //Check that writeable
        guard mMode == FileStream.WRITEABLE || mMode == FileStream.APPENDABLE else { return false; }
        
        //First Write the Size
        mFileHandle?.write(Data.init(from: values.count));
        
        //Now Write the actual array
        mFileHandle?.write(Data.init(fromArray: values));
        
        //Now Return True
        return true;
    }
    
    func WriteSerialiseable<T: Serialiseable>(value: T) -> Bool
    {
        //Check that writeable
        guard mMode == FileStream.WRITEABLE || mMode == FileStream.APPENDABLE else { return false; }
        
        //Now Write the Data Type
        return value.toFile(file: self);
    }
    
    func WriteSerialiseableArray<T: Serialiseable>(values: [T]) -> Bool
    {
        //Check that writeable
        guard mMode == FileStream.WRITEABLE || mMode == FileStream.APPENDABLE else { return false; }
        
        //First Write the Size
        mFileHandle?.write(Data.init(from: values.count));
        
        //Now Write the actual array
        for i in 0..<values.count
        {
            if (!values[i].toFile(file: self)) { return false; }
        }
        
        //Else Return True
        return true;
    }
    
    func close()
    {
        if (mFileHandle != nil) { mFileHandle!.closeFile(); mFileHandle = nil; }
    }
    
    deinit
    {
        if (mFileHandle != nil) { mFileHandle!.closeFile(); }
    }
}
