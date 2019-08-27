package mt.edu.um.vjagg;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Created by michael on 23/02/2016.
 * Implemented as a singleton class
 *
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
 * [Converted to using primitives]
 */
//TODO Check that this is thread-safe...

//TODO I am ignoring possibilities that:
//      User does not use app for a certain number of days, in which case there will be a file-leak (older files will not be deleted)
//      User does not attempt to send data and fail at midnight s.t. the data is retrieved before midnight but the prepend done after midnight!
public class LogView extends Handler implements Runnable
{
    //!< Static Constants
    public static final int DEBUG   = 1;
    public static final int INFO    = 2;
    public static final int WARNING = 3;
    public static final int ERROR   = 4;

    private static final String  LOG_STUB = "'vjagg-log-['yy-MM-dd'].log'";
    private static final String  LOG_PREF = "vjagg-log-";
    private static final int     MSG_SIZE = 1000;
    private static final int     VIEW_SIZE = 10;
    private static final int     DAY_BUFFER = 2;            //Keep for the last two days (plus current one)
    private static final long    FLUSH_RATE = 60000;        //Flush every minute
    private static final long    MSEC_DAY = 24*60*60*1000;  //Number of milliseconds in one day

    //!< Member Variables
    private Context             mApp;               //!< Reference to the application context
    private BufferedWriter      mWriter;            //!< File to write to
    private TextView            mOutput;            //!< Dictates the text view to which to write log output (must be in UI thread!)
    private ArrayList<String>   mLastMessages;      //!< Crude Implementation of a circular buffer (using an array list)
    private long                mLastFlush;         //!< Time of last buffer flush
    private boolean             mWroteSinceFlush;   //!< Wrote since last flush
    private long                mNextFile;          //!< Indication that need to change file name

    /**
     * \brief Singleton Implementation
     */
    private static LogView instance = new LogView();

    /**
     * \brief Private Constructor
     */
    private LogView()
    {
        //Initialise Handler on Main (UI) Thread
        super(Looper.getMainLooper());

        //Set Variables
        mWriter = null;
        mOutput = null;
        mApp    = null;
        mLastFlush = -1;
        mWroteSinceFlush = false;
        mNextFile = -1;
    }

    /**
     * \brief Set the Application Context
     * @param app
     */
    public static void SetContext(Context app) { instance.setContext(app); }
    private synchronized void setContext(Context app)
    {
        if (mApp == null)
        {
            mApp = app.getApplicationContext(); ResolveLogFile(true);
            Thread thread = new Thread(this);
            thread.start();
        }
    }

    /**
     * \brief Set the Logging to the Screen
     * @param v
     */
    public static void SetViewLog(View v) { instance.setViewLog(v); }
    private synchronized void setViewLog(View v)
    {
        if (v != null)
        {
            mOutput = (TextView)v;
            mOutput.setMovementMethod(new ScrollingMovementMethod());
            mLastMessages = new ArrayList<>(VIEW_SIZE);
        }
        else
        {
            mOutput = null;
            mLastMessages = null;
        }
    }

    public static void ForceFlush() { instance.forceFlush(); }
    private synchronized void forceFlush()
    {
        try { if (mWriter != null) { mWriter.flush(); mLastFlush = System.currentTimeMillis(); mWroteSinceFlush = false; } }
        catch (Exception e) {}
    }

    /**
     * \brief Gets the logged data so far and deletes it
     * @return String representation of the log data
     */
    public static ArrayList<String> RetrieveLoggedData() { return instance.retrieveLoggedData(); }
    private synchronized ArrayList<String> retrieveLoggedData()
    {
        ArrayList<String> string_list = new ArrayList<>(); //String list for controlling size of output
        boolean reopen = false;

        try
        {
            //Record current time
            long curr_time = System.currentTimeMillis();

            //First close the file writer (if currently writing)
            if (mWriter != null) { mWriter.close(); reopen = true; mWriter = null; }

            //Now read in the data, one file at a time...
            for (int i=DAY_BUFFER; i>=0; i--) //Start with the oldest file in the bunch
            {
                //Resolve file to read from
                File curr_file = new File(mApp.getFilesDir(), (new SimpleDateFormat(LOG_STUB, Locale.ENGLISH)).format(new Date(curr_time - i*MSEC_DAY)));
                if (!curr_file.exists()) { continue; } //IF no file, move to next

                //Prepare for reading
                BufferedReader reader = new BufferedReader(new FileReader(curr_file));
                String single_msg = "";
                String line;

                //Read in file
                while((line = reader.readLine()) != null)
                {
                    if ((single_msg.length() + line.length() + 1) < MSG_SIZE)
                    {
                        single_msg += " " + line;
                    }
                    else
                    {
                        string_list.add(new String(single_msg));  //To enforce copying and not reference
                        single_msg = new String(line);
                    }
                }
                if (single_msg.length() > 0) { string_list.add(single_msg); }

                //Close and clean up
                reader.close();
                curr_file.delete();
            }

            //If nothing read in the first place, nullify
            if (string_list.size() < 1)  { string_list = null; }
        }
        catch (Exception e) { return null; }
        finally
        {
            //Finally, if need be reopen writer
            if (reopen) { ResolveLogFile(true); }
        }

        return string_list;
    }

    /**
     * \brief Delete all log data
     * @return
     */
    public static boolean DeleteLogs() { return instance.deleteLogs(); }
    private synchronized boolean deleteLogs()
    {
        boolean reopen = false;

        try
        {
            //First close the file writer (if open)
            if (mWriter != null) { mWriter.close(); reopen = true; mWriter = null; }

            //Now delete the file(s)
            for (File f : mApp.getFilesDir().listFiles())
            {
                if (f.getName().startsWith(LOG_PREF)) { f.delete(); }
            }
        }
        catch (IOException e) { return false; }
        finally
        {
            //Finally, if need be reopen writer
            if (reopen) { ResolveLogFile(true); }
        }

        return true;
    }

    /**
     * \brief Re-save logs which were not sent successfully...
     * @param string_list
     * @return
     */
    public static boolean PrependUnsentLogs(ArrayList<String> string_list) { return instance.prependUnsentLogs(string_list); }
    public synchronized boolean prependUnsentLogs(ArrayList<String> string_list)
    {
        boolean reopen = false;

        try
        {
            BufferedWriter  writer;

            //Keep track of current time
            long curr_time = System.currentTimeMillis();

            //First close the original file writer
            if (mWriter != null) { mWriter.close(); reopen = true; mWriter = null; }

            //Now check which would be the previous-day file name (which will contain all of the prepend logs now) and create it
            File curr_file = new File(mApp.getFilesDir(), (new SimpleDateFormat(LOG_STUB, Locale.ENGLISH)).format(new Date(curr_time-MSEC_DAY)));
            writer = new BufferedWriter(new FileWriter(curr_file, false));

            //Write to file
            for (String str: string_list) { writer.write(str + "\n"); }

            //Close temporary writer
            writer.close();
        }
        catch (Exception e) { return false; }
        finally
        {
            //Finally, if need be reopen writer
            if (reopen) { ResolveLogFile(true); }
        }

        return true;
    }

    /**
     * \brief These can be run from any thread...
     * @param tag The Tag of the calling method
     * @param str The Message string to log
     */
    public static void Debug(String tag, String str)
    {
        Message.obtain(instance, DEBUG, "[" + Long.toString(System.currentTimeMillis()) + "]{D}" + tag + ":" + str).sendToTarget();
        Log.d(tag, str);
    }
    public static void Info(String tag, String str)
    {
        Message.obtain(instance, INFO, "[" + Long.toString(System.currentTimeMillis()) + "]{I}" + tag + ":" + str).sendToTarget();
        Log.i(tag, str);
    }
    public static void Warn(String tag, String str)
    {
        Message.obtain(instance, WARNING, "[" + Long.toString(System.currentTimeMillis()) + "]{W}" + tag + ":" + str).sendToTarget();
        Log.w(tag, str);
    }
    public static void Error(String tag, String str)
    {
        Message.obtain(instance, ERROR, "[" + Long.toString(System.currentTimeMillis()) + "]{E}" + tag + ":" + str).sendToTarget();
        Log.e(tag, str);
    }

    /**
     * \brief This will run in the main thread and will update the UI
     * @param inputMessage
     */
    @Override
    public synchronized void handleMessage(Message inputMessage)
    {
        if ((mOutput != null) && (inputMessage.what > DEBUG)) //Do not log DEBUG to Screen
        {
            mLastMessages.add(inputMessage.obj.toString());
            if (mLastMessages.size() > VIEW_SIZE) { mLastMessages.remove(0); }  //Remove the oldest element
            String output = mLastMessages.get(0);
            for(int i=1; i<mLastMessages.size(); i++) { output += "\n" + mLastMessages.get(i); }
            mOutput.setText(output);
        }
        if (ResolveLogFile(false)) { try { mWriter.write(inputMessage.obj.toString() + "\n"); mWroteSinceFlush = true; } catch (Exception e) {} }
    }

    @Override
    public void run()
    {
        while (!Thread.interrupted())
        {
            synchronized (this) { ResolveLogFile(false); }
            try { Thread.sleep(FLUSH_RATE); } catch (InterruptedException e) { break; } //No more logging...
        }
    }

    /**
     * \brief Resolves the current state of the log file. Note that this is not a thread-safe function (not synchronised) and must be called from within
     *          a synchronised environment.
     * @param open Indicates whether we are opening for the first time...
     */
    private boolean ResolveLogFile(boolean open)
    {
        try
        {
            //Branch based on whether we are opening the file or just checking it
            if (open)
            {
                //Just in case, close
                if (mWriter != null) { mWriter.close(); }

                //Keep track of calling time
                mLastFlush = System.currentTimeMillis();

                //Create Output File
                mWriter = new BufferedWriter(new FileWriter(new File(mApp.getFilesDir(), (new SimpleDateFormat(LOG_STUB, Locale.ENGLISH)).format(new Date(mLastFlush))), true));

                //Now Delete File from 3 Days ago if any (i.e. we always keep today plus two days older)
                (new File(mApp.getFilesDir(), (new SimpleDateFormat(LOG_STUB, Locale.ENGLISH)).format(new Date(mLastFlush-(DAY_BUFFER+1)*MSEC_DAY)))).delete();

                //Set up control/flush variables
                mWroteSinceFlush = false;

                //Finally calculate the next turnover time
                mNextFile = mLastFlush - (mLastFlush % MSEC_DAY) + MSEC_DAY;
            }
            else
            {
                //Get Current Time
                long curr_time = System.currentTimeMillis();

                //Check that writer is not null
                if (mWriter == null) { return false; }

                //Check if day turned over
                if (curr_time >= mNextFile)
                {
                    mWriter.close();    //Close (previous) Writer
                    mWriter = new BufferedWriter(new FileWriter(new File(mApp.getFilesDir(), (new SimpleDateFormat(LOG_STUB, Locale.ENGLISH)).format(new Date(curr_time))), true)); //Create Output File
                    (new File(mApp.getFilesDir(), (new SimpleDateFormat(LOG_STUB, Locale.ENGLISH)).format(new Date(mLastFlush-(DAY_BUFFER+1)*MSEC_DAY)))).delete(); //Delete old files
                    mWroteSinceFlush = false;
                    mLastFlush = curr_time;
                    mNextFile += MSEC_DAY;
                }
                else //Just flush if need be
                {
                    if (((curr_time - mLastFlush) > FLUSH_RATE) && mWroteSinceFlush) { mWriter.flush(); mLastFlush = curr_time; mWroteSinceFlush = false; }
                }
            }
        }
        catch (IOException e)
        {
            mWriter = null;
            return false;
        }
        return  true;
    }
}
