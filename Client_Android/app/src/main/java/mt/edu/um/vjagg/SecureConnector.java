/**
 *   Copyright (C) 2019  Michael Camilleri
 *
 * 	This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 *	License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * 	version.
 *	This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 *	warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 	You should have received a copy of the GNU General Public License along with this program. If not, see
 *	http://www.gnu.org/licenses/.
 *
 *	Author: Michael Camilleri
 *
 */

package mt.edu.um.vjagg;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.InvalidParameterException;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * Created by Michael Camilleri on 05/02/2016.
 *
 * This class encapsulates the communication with our server. It is envisioned that the system
 *    communicates to our server on port 1500 and from then on is assigned a specific port over
 *    which to communicate...
 *
 * The class also takes care of the Security protocols as well as the packaging of data to be sent.
 *
 * [Converted to using primitives]
 */
public class SecureConnector implements HostnameVerifier
{
    /**
     * Class for encapuslating a message
     */
    private static class ControlMessage_t
    {
        public int     mCode;
        public String  mData;

        public ControlMessage_t()
        {
            mCode = 0;
            mData = "";
        }
    }

    //!< Static Constants
    public static final String IDENTIFICATION_F = "VJAGG.id";

    public static final int     CONNECTION_PORT = 1500;    //!<The Default connection port

    public static final String  SERVER_URL      = "drt.research.um.edu.mt";
    public static final String  SECURITY_PROTOCOL = (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) ? "TLSv1" : "TLSv1.2";
    public static final int     STREAM_TIMEOUT  = 10000;    //!< MilliSeconds for stream timeout

    public static final int CM_EMPTY      		=   0;	//!< Empty Message

    public static final int CM_PING_REQ			=  11;	//!< Request ping
    public static final int CM_PING_REP   		=  12;	//!< Reply to ping

    public static final int CM_OK				=  21;	//!< General OK Message
    public static final int CM_INV				=  22;	//!< Invalid request
    public static final int CM_FAIL				=  23;	//!< General Failure Message
    public static final int CM_TERM				=  24;	//!< Termination Message


    ///-------------------------- TMAR Messages (100-199) --------------------------///
    public static final int CM_IDENT_REQ			= 101;	//!< Identification request
    public static final int CM_IDENT_REP  		= 102;	//!< Identification reply
    public static final int CM_IDENT_JOUR_VER  	= 103;	//!< Verify Identification Code (typically between server and dbase) [dbase will reply with OK or FAIL message]
    public static final int CM_IDENT_VER		  = 104;	//!< Verify Identification Code (no journey)

    public static final int CM_REQ_SEND_JOURNEY   = 111;  //Client Request to send a journey: Data will contain the user identifier, & journey unique identifier
    public static final int CM_OK_TO_SEND         = 112;  //Server Send Confirmation that can send data
    public static final int CM_SENDING_HEADER     = 113;  //Client Sending Journey Header [Length, Termination Code, Mode, Reason]: Server will reply (if ok) with MSG_OK_TO_SEND again...
    public static final int CM_SENDING_ROUTE_PART   = 114;  //Client Sending the Actual Journey Route
    public static final int CM_ROUTE_PART_RECVD	    = 115;	//Route Part Received
    public static final int CM_SENDING_ROUTE_DONE	= 116;	//Entire route received (data would normally be empty)
    public static final int CM_JOURNEY_RECEIVED     = 117;  //Reply from server that journey received...
    public static final int CM_STORE_JOURNEY		= 118;	//Command to store Journey (server to dbase) [dbase will reply with journey received]

    public static final int CM_REQ_SEND_LOGDATA	  = 121;	//Request to send Log Data
    public static final int CM_OK_TO_LOG          = 122;  //Ok to send log data
    public static final int CM_SENDING_LOG_PART   = 123;  //Sending part of a log
    public static final int CM_LOG_PART_RECVD     = 124;  //Received Log part
    public static final int CM_SENDING_LOG_DONE   = 125;  //Last part of log information
    public static final int CM_LOG_RECEIVED       = 126;  //Received Log
    public static final int CM_STORE_LOG		  = 127;	//Store the received log file...

    /**
     * Debug variables
     */
    public static final String TAG = "SC";

    //!< Member variables
    private SSLContext          sslType;    //!< The SSL Context
    private SSLSocket           sslSckt;    //!< The actual SSL Socket
    private BufferedReader      inputSt;    //!< The input stream reader
    private OutputStreamWriter  outputSt;   //!< The Output Stream Writer
    private String              mID;        //!< Identification
    private Context             mParent;    //!< Parent Context

    /**
     * Default Constructor
     */
    public SecureConnector(Context context)
    {
        mParent = context;
    }


    public static boolean CheckFiles(Context c)
    {
        //locate the Identification file
        return ((new File(c.getFilesDir(), IDENTIFICATION_F)).exists());
    }

    /**
     * Initialisation Function: just the SSL Parameters
     */
    private Exception InitialiseSSLParams()
    {
        try
        {
            LogView.Debug(TAG, "Init SSL");
            //Generate appropriate context
            sslType = SSLContext.getInstance(SECURITY_PROTOCOL);
            sslType.init(null, null, null);
        }
        catch (Exception e)
        {
            return e;
        }

        LogView.Debug(TAG, "Init OK");
        //If ok
        return null;
    }


    private Exception InitialiseConnection()
    {
        try
        {
            LogView.Debug(TAG, "Init Connx");
            //Connect
            sslSckt = (SSLSocket) sslType.getSocketFactory().createSocket(SERVER_URL, CONNECTION_PORT);  //!< Create SSL Socket
            sslSckt.setSoTimeout(STREAM_TIMEOUT);
            inputSt = new BufferedReader(new InputStreamReader(sslSckt.getInputStream()));
            outputSt = new OutputStreamWriter(sslSckt.getOutputStream());

            //Check Connection
            LogView.Debug(TAG, "Connx OK: Ping.");
            SendMsg(CM_PING_REQ, "");
            ReadMsg();      //As yet not doing anything with reply
            LogView.Debug(TAG, "Server OK.");
        }
        catch (Exception e)
        {
            return e;
        }

        //If ok
        return null;
    }


    /**
     * This is the public connection function...
     * @return
     */
    public Exception Connect()
    {
        LogView.Debug(TAG, "Connect");
        Exception result;
        if ((result = InitialiseSSLParams()) != null) { return result; }
        return InitialiseConnection();
    }

    /**
     * Public disconnect function (sends disconnect request to server)
     * @return Null if ok, an exception on failure
     */
    public Exception Disconnect()
    {
        try
        {
            LogView.Debug(TAG, "Disconnect");
            SendMsg(CM_TERM, "");
            ReadMsg();  //Wait for reply

            //Close resources
            LogView.Debug(TAG, "Server OK-Term");
            inputSt.close();
            outputSt.close();
            sslSckt.close();
        }
        catch (Exception e)
        {
            return e;
        }
        return null;
    }

    /**
     * Function to request an Identification tag. The function assumes there is already a secure connection established.
     * It also assumes that the ID was not previously created...
     * @return Null if successful, or Exception caught if fails...
     */
    public Exception RequestIDentification(int gender, int age, int position, boolean car_access)
    {
        try
        {
            LogView.Debug(TAG, "ReqID");
            //Request & Obtain ID from server
            SendMsg(CM_IDENT_REQ, Integer.toString(gender) + " " + Integer.toString(age) + " " + Integer.toString(position) + " " + (car_access ? "1" : "0"));
            ControlMessage_t tmp_msg = ReadMsg();
            if (tmp_msg.mCode != CM_IDENT_REP) { throw new InvalidParameterException(Integer.toString(tmp_msg.mCode)); }
            mID = tmp_msg.mData;

            //Write to File
            LogView.Debug(TAG, "ID=(" + mID + ")");
            File file = new File(mParent.getFilesDir(), IDENTIFICATION_F);
            file.createNewFile();
            FileWriter writer = new FileWriter(file);
            writer.write(mID.toString());
            writer.flush();
            writer.close();
            LogView.Debug(TAG, "ID OK");
        }
        catch (Exception e)
        {
            return e;
        }

        return null;
    }

    public Exception SendJourney(Journey journey)
    {
        try
        {
            LogView.Debug(TAG, "SendJourney");
            Exception result = null;
            result = LoadIdentification(); if (result != null) { throw result; }    //Load Identification for user

            //Request to send journey and wait for reply
            SendMsg(CM_REQ_SEND_JOURNEY, mID + " " + journey.getIdentifier());
            ControlMessage_t tmp_msg = ReadMsg();
            if (tmp_msg.mCode != CM_OK_TO_SEND) { throw new IOException(tmp_msg.mData); }  //IF not ok to send, will reply with reason for error

            //Else, send Journey Header and wait for reply
            SendMsg(CM_SENDING_HEADER, journey.getTripHeader());
            tmp_msg = ReadMsg();
            if (tmp_msg.mCode != CM_OK_TO_SEND) { throw  new IOException(tmp_msg.mData); }

            //Finally, send journey, part by part, 100 pts at a time
            int buffer_size = journey.getNumPoints();
            int sent = 0;
            while (sent + 100 < buffer_size)
            {
                SendMsg(CM_SENDING_ROUTE_PART, journey.getTripPoints(sent, 100)); //Send 100 at a time
                tmp_msg = ReadMsg();
                if (tmp_msg.mCode != CM_ROUTE_PART_RECVD) { throw  new IOException(tmp_msg.mData); }
                sent += 100;
            }
            SendMsg(CM_SENDING_ROUTE_DONE, journey.getTripPoints(sent, buffer_size - sent));
            tmp_msg = ReadMsg();
            if (tmp_msg.mCode != CM_JOURNEY_RECEIVED) { throw  new IOException(tmp_msg.mData); }
        }
        catch (Exception e)
        {
            return e;
        }

        return null;
    }


    public Exception SendLogData(String msg, ArrayList<String> log_data)
    {
        try
        {
            LogView.Debug(TAG, "SendLogData");
            Exception result = null;
            result = LoadIdentification(); if (result != null) { throw result; }    //Load Identification for user

            //Request to send journey and wait for reply
            SendMsg(CM_REQ_SEND_LOGDATA, mID + " " + msg);
            ControlMessage_t tmp_msg = ReadMsg();
            if (tmp_msg.mCode != CM_OK_TO_LOG) { throw new IOException(tmp_msg.mData); }  //IF not ok to send, will reply with reason for error

            //Else, send journey, part by part, 1000 characters at a time at a time
            for (int i=0; i<log_data.size()-1; i++)
            {
                SendMsg(CM_SENDING_LOG_PART, log_data.get(i));
                tmp_msg = ReadMsg();
                if (tmp_msg.mCode != CM_LOG_PART_RECVD) { throw  new IOException(tmp_msg.mData); }
            }
            //Send the last part
            SendMsg(CM_SENDING_LOG_DONE, log_data.get(log_data.size() - 1));
            tmp_msg = ReadMsg();
            if (tmp_msg.mCode != CM_LOG_RECEIVED) { throw  new IOException(tmp_msg.mData); }
        }
        catch (Exception e)
        {
            return e;
        }

        return null;
    }

    protected Exception LoadIdentification()
    {
        try
        {
            LogView.Debug(TAG, "LoadID");
            //Locate and open Identification file and read in the identification string
            mID = (new BufferedReader(new FileReader(new File(mParent.getFilesDir(), IDENTIFICATION_F)))).readLine();
        }
        catch (Exception e)
        {
            return e;
        }

        LogView.Debug(TAG, "Load OK");
        return null;
    }

    /**
     * \brief Send a Message
     * @param msg_code
     * @param msg_data
     * @throws Exception
     */
    private void SendMsg(int msg_code, String msg_data) throws Exception
    {
        String message = Integer.toString(msg_code) + ' ' + msg_data;
        outputSt.write(message);
        outputSt.flush();   //Ensure written
    }

    private ControlMessage_t ReadMsg() throws Exception
    {
        String input_msg;

        input_msg = inputSt.readLine();
        if(input_msg.length() > 0)
        {
            ControlMessage_t tmp_msg = new ControlMessage_t();
            tmp_msg.mCode = Integer.parseInt(input_msg.substring(0,3));
            if (input_msg.length() > 3) { tmp_msg.mData = input_msg.substring(4); }
            return tmp_msg;
        }
        else
        {
            return null;
        }
    }

    public String getIdentification()
    {
        return mID;
    }

    public static String PeekIdentity(Context c)
    {
        LogView.Debug(TAG, "PeekID");
        try
        {
            //Locate and open Identification file and read the identification string
            return (new BufferedReader(new FileReader(new File(c.getFilesDir(), IDENTIFICATION_F)))).readLine();
        }
        catch (Exception e)
        {
            return "Error";
        }
    }

    @Override
    /**
     * Override of the verify functionality: for now just allowing all ip's...
     */
    public boolean verify(String hostname, SSLSession session)
    {
        LogView.Debug(TAG, "verify - " + hostname);
        return hostname.equalsIgnoreCase(SERVER_URL);
    }
}
