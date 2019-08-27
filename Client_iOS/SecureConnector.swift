//
//  SecureConnector.swift
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

import UIKit

//http://www.appcoda.com/socket-io-chat-app/ : Requires External Library
//https://www1.in.tum.de/lehrstuhl_1/home/98-teaching/tutorials/540-ios14intro-client-server-communication : Too High level and old
//https://www.andrewcbancroft.com/2015/09/21/openssl-for-ios-swift-the-easy-way/ : May be the way should all else fail
//https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/NetworkingTopics/Articles/UsingSocketsandSocketStreams.html
//https://developer.apple.com/library/content/documentation/NetworkingInternetWeb/Conce	ptual/NetworkingOverview/SecureNetworking/SecureNetworking.html#//apple_ref/doc/uid/TP40010220-CH1-SW3

//TODO Consider implementing TimeOuts...

//!< Secure Connector Delegate
protocol SCDelegate
{
    //!< This function will be called once the requested operation is completed. The result is passed back, as well as an indication of success/failure
    func OnDone(action: Int, with result:Bool, containing data:String?, other:AnyObject?);
}

/**
 * \brief   This is the Secure Connector Class implementation
 * \detail  The class is implemented as a singleton. That being said, it is not entirely thread-safe, in that it assumes that there are no simultaneous calls to the functions which access the network - i.e. no explicit locking mechanisms are as yet implemented.
 */
class SecureConnector : NSObject, StreamDelegate
{
    //!< Control Message Definition
    private struct ControlMessage_t
    {
        var mCode:Int;
        var mData:String;
        
        init(code:Int = 0, data:String="")
        {
            mCode = code;
            mData = data;
        }
        
        init?(fromMessage msg:String)
        {
            let data = msg.characters.split(separator: " ", maxSplits: 1).map(String.init);
            if let code = Int(data[0])
            {
                mCode = code;
                if (data.count > 1) { mData = data[1]; }
                else                { mData = ""; }
            }
            else
            {
                return nil;
            }
        }
        
        func serialise()->String
        {
            return String(format: "%03d \(mData)", mCode);
        }
        
        static func IDReqMsg(with details:SignUpView.UserDetails) -> String
        {
            return String(format: "%03d %d %d %d %d", SecureConnector.CM_IDENT_REQ, details.mGender, details.mAgeGrp, details.mRelUni, details.mCarAcc);
        }
        
        static func UploadReqMsg(for journey:Journey) -> String
        {
            return String(format: "%03d \(SecureConnector.UserID) \(journey.ID)", SecureConnector.CM_REQ_SEND_JOURNEY);
        }
        
        static func TripHeadMsg(for journey:Journey) -> String
        {
            return String(format: "%03d \(journey.header())", SecureConnector.CM_SENDING_HEADER);
        }
        
        static func TripEndMsg(for journey:Journey, from:Int) -> String
        {
            return String(format: "%03d \(journey.points(from: from, num: 100))", SecureConnector.CM_SENDING_ROUTE_DONE);
        }
        
        static func TripPartMsg(for journey:Journey, from:Int) -> String
        {			
            return String(format: "%03d \(journey.points(from: from, num: 100))", SecureConnector.CM_SENDING_ROUTE_PART);
        }
    }
    
    //!< Constant Definitions
    private static let SERVER_PORT  :Int    = 1500;
    private static let SERVER_URL   :String = "drt.research.um.edu.mt";
    private static let USER_ID_FILE :String = "vjagg_id.dat";
    
    //!< Message Opcodes
    private static let CM_EMPTY    : Int  =  0;
    
    private static let CM_PING_REQ : Int  = 11;
    private static let CM_PING_REP : Int  = 12;
    
    private static let CM_OK       : Int  = 21;
    private static let CM_INV      : Int  = 22;
    private static let CM_FAIL     : Int  = 23;
    private static let CM_TERM     : Int  = 24;
    
    private static let CM_IDENT_REQ        : Int  = 101;
    private static let CM_IDENT_REP        : Int  = 102;
    
    private static let CM_REQ_SEND_JOURNEY     : Int  = 111;
    private static let CM_OK_TO_SEND           : Int  = 112;
    private static let CM_SENDING_HEADER       : Int  = 113;
    private static let CM_SENDING_ROUTE_PART   : Int  = 114;
    private static let CM_ROUTE_PART_RECVD     : Int  = 115;
    private static let CM_SENDING_ROUTE_DONE   : Int  = 116;
    private static let CM_JOURNEY_RECEIVED     : Int  = 117;
    private static let CM_STORE_JOURNEY        : Int  = 118;
    
    /**
     * State Definitions
     */
    private static let ST_NDEF          : Int = -1; //!< Undefined
    
    private static let ST_INIT          : Int =  0; //!< Just Initialised
    
    private static let ST_CONX_TRY      : Int = 10; //!< Attempting Connection
    private static let ST_CONX_IN_OK    : Int = 11; //!< The Reader has Connected successfully
    private static let ST_CONX_OUT_OK   : Int = 12; //!< The Writer has Connected successfully
    private static let ST_CONX_DONE     : Int = 13; //!< Connection has been carried out sucessfully
    private static let ST_WAIT_PONG     : Int = 14; //!< Connection is waiting PONG (having sent ping)
    private static let ST_IDLE          : Int = 15; //!< Connection is up and idle
    private static let ST_WAIT_TERM     : Int = 16; //!< Sent Termination signal and awaiting reply
    
    private static let ST_WAIT_ID       : Int = 20; //!< Awaiting ID reply
    
    private static let ST_WAIT_START_OK : Int = 30; //!< Awaiting OK to upload
    private static let ST_WAIT_HEAD_OK  : Int = 31; //!< Awaiting Header Receive Confirmation
    private static let ST_WAIT_PART_OK  : Int = 32; //!< Journey Part Received Confirmation
    private static let ST_WAIT_DONE_OK  : Int = 33; //!< Journey Received entirely
    
    public static let OP_NULL       : Int = 0;  //!< No operation to perform
    public static let OP_REQ_ID     : Int = 1;  //!< Request Identification
    public static let OP_UPLOAD     : Int = 2;  //!< Operation to Upload Journeys
    
    
    //!< Class Variables
    private static let SINGLE = SecureConnector();  //!< Singleton pattern
    
    //!< Object Variables
    private var mUserID: String?;       //!< The User ID - an optional
    
    private var mWriter: OutputStream?;     //!< The Writing Stream
    private var mReader: InputStream?;      //!< The Reading Stream
    private var mBuffer: String;            //!< The Read Buffer
    
    private var mStatus: Int;           //!< The State Variable
    private var mOperat: Int;           //!< Operation to perform
    private var mInform: SCDelegate?;   //!< The Delegate to inform
    private var mDetail: SignUpView.UserDetails?;    //!< Used to keep track of user parameters to send request for
    private var mTrips : [Journey]?;
    private var mSent  : Int;           //How many points have been sent...
    
    private let TAG = "Sec.Connx";
    
    //!< Computed Variables
    static var UserID : String  //!< For getting (and optionally loading) the ID
    {
        get
        {
            if SINGLE.mUserID == nil { SINGLE.LoadID(); }
            return SINGLE.mUserID ?? "";
        }
    }
    
    //!< Default Constructor is private which enforces singleton pattern
    private override init()
    {
        mUserID = nil;
        mWriter = nil;
        mReader = nil;
        mStatus = SecureConnector.ST_INIT;
        mOperat = SecureConnector.OP_NULL;
        mBuffer = "";
        mInform = nil;
        mDetail = nil;
        mTrips  = nil;
        mSent   = -1;
    }
    
    //Request the ID
    static func RequestID(forUser user:SignUpView.UserDetails, informing delegate: SCDelegate)
    {
        //Ensure that no operation in progress...
        guard SINGLE.mOperat == SecureConnector.OP_NULL
            else
        {
            delegate.OnDone(action: SecureConnector.OP_REQ_ID, with: false, containing: "Error - Operation in progress", other:nil);
            return;
        }
        
        //Ensure that not already initialised
        guard SINGLE.mStatus == SecureConnector.ST_INIT
            else
        {
            delegate.OnDone(action: SecureConnector.OP_REQ_ID, with: false, containing: "Error - Connection in progress", other:nil);
            return;
        }
        
        //Otherwise, set Operation, parameters and delegate
        SINGLE.mOperat = SecureConnector.OP_REQ_ID;
        SINGLE.mDetail = user;
        SINGLE.mInform = delegate;
        
        //Start Connection Attempt
        let result = SINGLE.OpenConnection();
        if (!result.0)
        {
            SINGLE.Inform(operation: SecureConnector.OP_REQ_ID, success: false, containing: result.1, clean:true);
            return;
        }
    }
    
    //Simulate an ID Request without actual server
    static func SimulateID(forUser user:SignUpView.UserDetails, informing delegate: SCDelegate)
    {
        SINGLE.mUserID = "ABC123DEF456GHI";
        
        do      { try "ABC123DEF456GHI".write(toFile: Util.GetAppFile(withName: SecureConnector.USER_ID_FILE).path, atomically: true, encoding: String.Encoding.utf8) }
        catch   { delegate.OnDone(action: SecureConnector.OP_REQ_ID, with: false, containing: "Error - " + error.localizedDescription, other: nil); return; }

        delegate.OnDone(action: SecureConnector.OP_REQ_ID, with: true, containing: "ABC123DEF456GHI", other: nil);
    }
    
    //Load the ID
    private func LoadID()
    {
        do    { mUserID = try String(contentsOf: Util.GetAppFile(withName: SecureConnector.USER_ID_FILE)); }
        catch { Debugger.Error(TAG, error.localizedDescription); mUserID = ""; }
    }
    
    static func Upload(journeys: [Journey], informing delegate: SCDelegate)
    {
        //Ensure we have a valid User ID and there are actually journeys to send...
        guard UserID.count == 15 else { delegate.OnDone(action: SecureConnector.OP_UPLOAD, with: false, containing: "Error - No Valid ID found!", other: journeys.count as AnyObject?); return; }
        guard journeys.count > 0 else { delegate.OnDone(action: SecureConnector.OP_UPLOAD, with: false, containing: "Error - No Journeys to send!", other: journeys.count as AnyObject?); return; }
        
        //Ensure that no operation in progress...
        guard SINGLE.mOperat == SecureConnector.OP_NULL
            else
        {
            delegate.OnDone(action: SecureConnector.OP_UPLOAD, with: false, containing: "Error - Operation in progress", other: journeys.count as AnyObject?);
            return;
        }
        
        //Ensure that not already initialised
        guard SINGLE.mStatus == SecureConnector.ST_INIT
            else
        {
            delegate.OnDone(action: SecureConnector.OP_UPLOAD, with: false, containing: "Error - Connection in progress", other: journeys.count as AnyObject?);
            return;
        }
        
        //Otherwise, set Operation, parameters and delegate
        SINGLE.mOperat = SecureConnector.OP_UPLOAD;
        SINGLE.mTrips  = journeys;
        SINGLE.mInform = delegate;
        
        //Start Connection Attempt
        let result = SINGLE.OpenConnection();
        if (!result.0)
        {
            SINGLE.Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: result.1, other: journeys.count as AnyObject?, clean:true);
            return;
        }
    }
    
    static func SimulateUpload(journeys: [Journey], informing delegate: SCDelegate)
    {
        delegate.OnDone(action: OP_UPLOAD, with: false, containing: "Some Error", other: 2 as AnyObject?);
    }
    
    private func SendNextJourney()
    {
        guard let journeys = mTrips
            else
        {
            //Inform and clean up only if the send termination is unsuccessful
            mStatus = SecureConnector.ST_WAIT_TERM; //State will be cleaned if send fails
            Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: "Error - Parameter Error", other: -1 as AnyObject?, clean:(!Send(message:ControlMessage_t(code:SecureConnector.CM_TERM))));
            return;
            
        }

        guard Send(string: ControlMessage_t.UploadReqMsg(for: journeys[0]))
            else
        {
            Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: "Error - Send Request Error", other: journeys.count as AnyObject?, clean:true);
            return;
        }

        //Else just update status
        mStatus = SecureConnector.ST_WAIT_START_OK;
        mSent   = 0;
    }

    //MARK: Networking:
    private func OpenConnection() -> (Bool, String?)
    {
        //Create Streams
        Stream.getStreamsToHost(withName: SecureConnector.SERVER_URL, port: SecureConnector.SERVER_PORT, inputStream: &(self.mReader), outputStream: &(self.mWriter));
        guard self.mReader != nil, self.mWriter != nil else { return (false, "Error - Unable to Create Streams"); }
        
        //Set Delegates
        self.mReader?.delegate = self; self.mWriter?.delegate = self;
        
        //Schedule Run Loop
        self.mReader?.schedule(in: .main, forMode: RunLoopMode.defaultRunLoopMode);
        self.mWriter?.schedule(in: .main, forMode: RunLoopMode.defaultRunLoopMode);
        
        //Attempt open
        self.mReader?.open();
        self.mWriter?.open();
        
        //Update state and return
        self.mStatus = SecureConnector.ST_CONX_TRY;
        return (true, nil);
    }
    
    //Function Performs no testing...
    private func Send(message:ControlMessage_t) -> Bool
    {
        if let writer = mWriter
        {
            let msg_str = (message.serialise() + "\n").data(using: String.Encoding.utf8)!;
            let result  = msg_str.withUnsafeBytes { writer.write($0, maxLength: msg_str.count); }
            return result == msg_str.count;
        }
        else
        {
            return false;
        }
    }
    
    private func Send(string:String) -> Bool
    {
        if let writer = mWriter
        {
            let msg_str = (string + "\n").data(using: String.Encoding.utf8)!;
            let result  = msg_str.withUnsafeBytes { writer.write($0, maxLength: msg_str.count); }
            return result == msg_str.count;
        }
        else
        {
            return false;
        }

    }

    private func OnMessage(message:ControlMessage_t)
    {
        switch(message.mCode)
        {
        case SecureConnector.CM_PING_REQ:
            if (!Send(message: ControlMessage_t(code:SecureConnector.CM_PING_REP))) { Debugger.Error(TAG, "Ping Failed"); }
            
        case SecureConnector.CM_PING_REP:
            if (mStatus == SecureConnector.ST_WAIT_PONG)
            {
                mStatus = SecureConnector.ST_IDLE;
                
                //Now Switch based on Operation Mode:
                switch(mOperat)
                {
                case SecureConnector.OP_REQ_ID:
                    guard let user = mDetail
                        else
                    {
                        //Inform and clean up only if the send termination is unsuccessful
                        mStatus = SecureConnector.ST_WAIT_TERM; //State will be cleaned if send fails
                        Inform(operation: SecureConnector.OP_REQ_ID, success: false, containing: "Error - Parameter Error", clean:(!Send(message:ControlMessage_t(code:SecureConnector.CM_TERM))));
                        return;
                    }

                    guard Send(string: ControlMessage_t.IDReqMsg(with: user))
                        else
                    {
                        Inform(operation: SecureConnector.OP_REQ_ID, success: false, containing: "Error - Parameter Error", clean:true);
                        return;
                    }

                    mStatus = SecureConnector.ST_WAIT_ID;
                    
                case SecureConnector.OP_UPLOAD:
                    SendNextJourney();
                    
                default:
                    Debugger.Error(TAG, "Invalid OpCode");
                }
            }
            
        case SecureConnector.CM_IDENT_REP:
            if (mStatus == SecureConnector.ST_WAIT_ID && mOperat == SecureConnector.OP_REQ_ID)
            {
                //Copy Data
                mUserID = message.mData;
                
                //Store in File:
                do
                {
                    try mUserID!.write(toFile: Util.GetAppFile(withName: SecureConnector.USER_ID_FILE).path, atomically: true, encoding: String.Encoding.utf8)
                }
                catch
                {
                    //Inform of Failure, and also terminate connection (and clean up if send fails)
                    Inform(operation: mOperat, success: false, containing: "Error - " + error.localizedDescription, clean:(!Send(message:ControlMessage_t(code:SecureConnector.CM_TERM))));
                    return;
                }

                //Inform the User (and clean up immediately if send fails)
                Inform(operation: mOperat, success: true, containing: mUserID, clean:(!Send(message:ControlMessage_t(code:SecureConnector.CM_TERM))));
            }
            else
            {
                Debugger.Error(TAG, "Invalid Message");
                if (!Send(message: ControlMessage_t(code:SecureConnector.CM_INV))) { Debugger.Error(TAG, "Send Failure"); }
            }
            
        case SecureConnector.CM_OK_TO_SEND:
            guard (mOperat == SecureConnector.OP_UPLOAD) else { Debugger.Error(TAG, "Invalid Message"); if (!Send(message: ControlMessage_t(code:SecureConnector.CM_INV))) { Debugger.Error(TAG, "Send Failure"); }; return; }
            
            //Ensure dereferenceable
            guard let journeys = mTrips
                else
            {
                //Inform and clean up only if the send termination is unsuccessful
                mStatus = SecureConnector.ST_WAIT_TERM; //State will be cleaned if send fails
                Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: "Error - Parameter Error", other: -1 as AnyObject?, clean:(!Send(message:ControlMessage_t(code:SecureConnector.CM_TERM))));
                return;
            }

            switch(mStatus)
            {
            case SecureConnector.ST_WAIT_START_OK:
                //Send Message Header
                guard Send(string: ControlMessage_t.TripHeadMsg(for: journeys[0]))
                    else
                {
                    Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: "Error - Send Header Error", other: journeys.count as AnyObject?, clean:true);
                    return;
                }
                
                //If OK so far:
                mStatus = SecureConnector.ST_WAIT_HEAD_OK;
                
            case SecureConnector.ST_WAIT_HEAD_OK:
                let msg = journeys[0].count < 101 ? ControlMessage_t.TripEndMsg(for: journeys[0], from: 0) : ControlMessage_t.TripPartMsg(for: journeys[0], from: 0);
                
                //Guard Against Send Error
                guard Send(string: msg)
                    else
                {
                    Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: "Error - Send Route Error", other: journeys.count as AnyObject?, clean:true);
                    return;
                }

                //IF OK
                mStatus = journeys[0].count < 101 ? SecureConnector.ST_WAIT_DONE_OK : SecureConnector.ST_WAIT_PART_OK;
                
            default:
                Debugger.Error(TAG, "Invalid Message"); if (!Send(message: ControlMessage_t(code:SecureConnector.CM_INV))) { Debugger.Error(TAG, "Send Failure"); }
            }
            
        case SecureConnector.CM_ROUTE_PART_RECVD:
            guard (mOperat == SecureConnector.OP_UPLOAD && mStatus == SecureConnector.ST_WAIT_PART_OK) else { Debugger.Error(TAG, "Invalid Message"); if (!Send(message: ControlMessage_t(code:SecureConnector.CM_INV))) { Debugger.Error(TAG, "Send Failure"); }; return; }
            
            //Ensure dereferenceable
            guard let journeys = mTrips
                else
            {
                //Inform and clean up only if the send termination is unsuccessful
                mStatus = SecureConnector.ST_WAIT_TERM; //State will be cleaned if send fails
                Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: "Error - Parameter Error", other: -1 as AnyObject?, clean:(!Send(message:ControlMessage_t(code:SecureConnector.CM_TERM))));
                return;
            }
            
            mSent += 100;
            let msg = journeys[0].count < mSent + 101 ? ControlMessage_t.TripEndMsg(for: journeys[0], from: mSent) : ControlMessage_t.TripPartMsg(for: journeys[0], from: mSent);
            
            //Guard against Send Error
            guard Send(string: msg)
                else
            {
                Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: "Error - Send Route Error", other: journeys.count as AnyObject?, clean:true);
                return;
            }
            
            //IF OK
            mStatus = journeys[0].count < mSent + 101 ? SecureConnector.ST_WAIT_DONE_OK : SecureConnector.ST_WAIT_PART_OK;
            
        case SecureConnector.CM_JOURNEY_RECEIVED:
            guard (mOperat == SecureConnector.OP_UPLOAD && mStatus == SecureConnector.ST_WAIT_DONE_OK) else { Debugger.Error(TAG, "Invalid Message"); if (!Send(message: ControlMessage_t(code:SecureConnector.CM_INV))) { Debugger.Error(TAG, "Send Failure"); }; return; }
            
            //Ensure dereferenceable
            guard var journeys = mTrips
                else
            {
                //Inform and clean up only if the send termination is unsuccessful
                mStatus = SecureConnector.ST_WAIT_TERM; //State will be cleaned if send fails
                Inform(operation: SecureConnector.OP_UPLOAD, success: false, containing: "Error - Parameter Error", other: -1 as AnyObject?, clean:(!Send(message:ControlMessage_t(code:SecureConnector.CM_TERM))));
                return;
            }

            //Remove this sent journey...
            journeys.remove(at: 0);
            if (journeys.count > 0) { SendNextJourney(); }
            else                    { Inform(operation: mOperat, success: true, containing: "All Journeys Uploaded Successfully", other: 0 as AnyObject?, clean: true); }
            
        case SecureConnector.CM_FAIL:
            Debugger.Warn(TAG, "Failure Msg: " + message.mData);
            switch(mOperat)
            {
            case SecureConnector.OP_REQ_ID:
                Inform(operation: mOperat, success: false, containing: message.mData, other: nil, clean: true);
                
            case SecureConnector.OP_UPLOAD:
                Inform(operation: mOperat, success: false, containing: message.mData, other: mTrips?.count as AnyObject?, clean: true)
                
            default:
                Debugger.Warn(TAG, "Failure with no operation");
            }
                
                
        default:
            Debugger.Error(TAG, "Invalid Message");
            if (!Send(message: ControlMessage_t(code:SecureConnector.CM_INV))) { Debugger.Error(TAG, "Send Failure"); }
        }
    }
    
    func stream(_ aStream: Stream, handle eventCode: Stream.Event)
    {
        switch(eventCode)
        {
        case Stream.Event.openCompleted:
            //Handle the Stream Open Completion
            if (aStream == mReader)
            {
                switch(mStatus)
                {
                case SecureConnector.ST_CONX_TRY:
                    mStatus = SecureConnector.ST_CONX_IN_OK;
                    
                case SecureConnector.ST_CONX_OUT_OK:
                    mStatus = SecureConnector.ST_CONX_DONE;
                    
                default:
                    Inform(operation: mOperat, success: false, containing: "Error - Wrong Reader State", other: mTrips?.count as AnyObject?, clean:true); return;
                }
            }
            else if (aStream == mWriter)
            {
                switch(mStatus)
                {
                case SecureConnector.ST_CONX_TRY:
                    mStatus = SecureConnector.ST_CONX_OUT_OK;
                    
                case SecureConnector.ST_CONX_IN_OK:
                    mStatus = SecureConnector.ST_CONX_DONE;
                    
                default:
                    Inform(operation: mOperat, success: false, containing: "Error - Wrong Writer State", other: mTrips?.count as AnyObject?, clean:true); return;
                }
            }
            
            //If Connection OK, start Ping
            if mStatus == SecureConnector.ST_CONX_DONE
            {
                //Set Security Levels (since the same underlying socket, it is sufficient to set on one)
                if (!(self.mReader?.setProperty(kCFStreamSocketSecurityLevelNegotiatedSSL, forKey: Stream.PropertyKey.socketSecurityLevelKey))!)
                {
                    Inform(operation: mOperat, success: false, containing: "Error - SSL Error", other: mTrips?.count as AnyObject?, clean:true);
                    return;
                }
                
                //Send Ping
                if (!Send(message: SecureConnector.ControlMessage_t(code:SecureConnector.CM_PING_REQ)))
                {
                    Inform(operation: mOperat, success: false, containing: "Error - Ping HandShake Failure", other: mTrips?.count as AnyObject?, clean:true);
                    return;
                }
                
                //Else
                mStatus = SecureConnector.ST_WAIT_PONG;
            }
            
        case Stream.Event.hasBytesAvailable:
            if aStream == mReader
            {
                //Read in the bytes
                var buffer = [UInt8](repeating:0, count:8192);
                let reader = mReader!;
                while reader.hasBytesAvailable
                {
                    let len = reader.read(&buffer, maxLength: buffer.count);
                    if len > 0, let str = String(bytes: buffer, encoding: String.Encoding.utf8)
                    {
                        mBuffer.append(str.substr(to:len)); //TODO check this
                    }
                }
                
                var newline = mBuffer.find("\n");
                while (newline != String.NF)
                {
                    //Parse up to Newline (not included) and forward message
                    guard let message = SecureConnector.ControlMessage_t(fromMessage: mBuffer.substr(to:newline)) else { fatalError("Error: Ill-Formed Message"); }
                    OnMessage(message: message)

                    //Clean up for next iteration
                    mBuffer = mBuffer.substr(from:newline + 1);
                    newline = mBuffer.find("\n");
                }
            }
        
        default:
            //Unhandled Operation - may avoid in the end...
            return;
        }
    }
    
    /**
     * \brief The Function Informs the delege and optionally cleans up
     * \detail The cleaning up is performed beforehand so that any processing carried out by the delegate does not interfere
     */
    private func Inform(operation: Int, success: Bool, containing: String?, other: AnyObject? = nil, clean : Bool)
    {
        //First, (optionally) clean up
        if (clean)
        {
            //Clean Sockets
            mReader?.close(); mReader = nil;
            mWriter?.close(); mWriter = nil;
            
            //Update states
            mOperat = SecureConnector.OP_NULL;
            mStatus = SecureConnector.ST_INIT;
            mBuffer = "";
            mDetail = nil;
            mTrips  = nil;
            mSent   = -1;
        }
        
        //Now Inform User
        if let delegate = mInform
        {
            delegate.OnDone(action: operation, with: success, containing: containing, other:other);
            mInform = nil;
        }
    }
}
