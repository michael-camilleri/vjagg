//
//  TextStream.swift
//  vjagg
//
//  Created by administrator on 25/01/2017.
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
import CoreData

protocol Textualiseable
{
    init?(from text: TextStream)
    func toText(text: TextStream) -> Bool
}

extension Double : Textualiseable
{
    init?(from text: TextStream)
    {
        let next = text.Next; if next.count < 1 { return nil; }
        self.init(next);
    }
    
    func toText(text: TextStream) -> Bool
    {
        text.Next = String(self);
        return true;
    }
}

extension Int    : Textualiseable
{
    init?(from text: TextStream)
    {
        let next = text.Next; if next.count < 1 { return nil; }
        self.init(next);
    }
    
    func toText(text: TextStream) -> Bool
    {
        text.Next = String(self);
        return true;
    }

}

extension Bool   : Textualiseable
{
    init?(from text: TextStream)
    {
        let next = text.Next; if next.count < 1 { return nil; }
        guard let iNxt = Int(next) else { return nil; }
        self.init(iNxt > 0)
    }
    
    func toText(text: TextStream) -> Bool
    {
        text.Next = self ? "1" : "0";
        return true;
    }
}


class TextStream : NSObject
{
    private let TAG = "TS";
    
    private var mBuffer : [String];
    private var mSeek   : Int;
    
    var Next : String
    {
        get { if (mSeek < mBuffer.count) { mSeek += 1; return mBuffer[mSeek-1]; } else { return ""; } }
        
        set(next) { if (mSeek < mBuffer.count) { mBuffer[mSeek] = next; } else { mBuffer.append(next); }; mSeek += 1; }
    }
    
    var Buffer : String
    {
        get
        {
            if mBuffer.count < 1 { return ""; }
            
            var buffer = mBuffer[0];
            
            for i in 1..<mBuffer.count { buffer.append(" " + mBuffer[i]); }
            
            return buffer;
        }
        
        set(buffer)
        {
            mBuffer = buffer.components(separatedBy: " ");
            mSeek = 0;
        }
    }
    
    init(_ text:String? = nil)
    {
        mBuffer = text?.components(separatedBy: " ") ?? [String]();
        mSeek   = 0;
    }
    
    func ReSeek(position:Int = 0)
    {
        mSeek = position;
    }
    
    func Read<T:Textualiseable>(_ type: T.Type) -> T?
    {
        return T(from:self);
    }
    
    func ReadArray<T:Textualiseable>(_ type: T.Type) -> [T]?
    {
        guard mSeek < mBuffer.count else { return nil; }
        
        //First Read Integer size
        guard let iSize = Int(mBuffer[mSeek]) else { return nil; }
        mSeek += 1;
        
        //Now Read Actual Array
        var TArray = [T]();
        for _ in 0 ..< iSize
        {
            guard let tItem = T(from:self) else { return nil; }
            TArray.append(tItem);
        }
        
        return TArray;
    }
    
    func Write<T: Textualiseable>(_ value: T) -> Bool
    {
        //Now Write the Data Type
        return value.toText(text: self);
    }
    
    func WriteArray<T: Textualiseable>(_ values: [T]) -> Bool
    {
        //First Write the Size
        Next = String(values.count);
        
        //Now Write the actual array
        for i in 0..<values.count
        {
            if (!values[i].toText(text: self)) { return false; }
        }
        
        //Else Return True
        return true;
    }
    
}
