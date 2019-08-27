package mt.edu.um.vjagg;

/**
 * Created by Michael Camilleri
 *
 * Implements the Activity for displaying Journeys and sending them to the server.
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

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;

import java.io.File;
import java.util.ArrayList;

//Used [http://www.vogella.com/tutorials/AndroidListView/article.html] for most of the constructions
//Lookup [http://developer.android.com/training/location/display-address.html] to allow converting to addresses...

public class JourneyListActivity extends AppCompatActivity implements OnMapReadyCallback, OnItemClickListener, OnClickListener
{
    static final String LIST_TYPE = "mt.edu.um.vjagg.ListType"; //!< True if Personal, false otherwise

    protected ListView              mJourView;  //!< The Journey List View
    protected SupportMapFragment    mJourMapF;  //!< The Journey Map View
    protected GoogleMap             mJourMapM;  //!< The Actual Map object

    ////// Indicator...
    private   boolean               mPersonal;  //!< Is this the personal view or the history view?

    ////// View Objects
    private   View                  mDataSend;  //!< Data Send View
    private   View                  mWait;      //!< Progress Bar View
    private   Polyline              mPrevLine;  //!< To keep track of previous polyline
    private   Marker                mStartM;    //!< Start Marker
    private   Marker                mEndM;      //!< End Marker

    //Async Tasks...
    private AsyncTask               mWorker;        //Generic pointer to worker task
    private Dialog                  mClearDialog;   //Pointer to the clear journeys dialog
    private Dialog                  mKeepDelete;    //Keep Historical reference before deleting
    private Dialog                  mKeepUpload;    //Keep Historical reference before upload

    //Logging
    private static final String TAG = "DSA";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        LogView.Debug(TAG, "OnCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_send);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setIcon(R.drawable.vjagg_icon);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mPersonal = this.getIntent().getBooleanExtra(LIST_TYPE, false);

        setTitle(mPersonal ? "Personal Journey History" : "Tracked Journeys");

        //Load the File
        if ((new File(getFilesDir(), mPersonal ? TrackingService.GPS_PER_FILE : TrackingService.GPS_RPP_FILE)).exists())
        {
            //Initialise Members
            mJourView = (ListView)findViewById(R.id.route_list_view);
            mWorker  = null;
            mClearDialog = null;
            mKeepDelete = null;
            mKeepUpload = null;

            //Views
            mDataSend = findViewById(R.id.data_send_layout);
            mWait     = findViewById(R.id.data_send_wait);

            //Refresh Array Adapter (with cleaning up if need be)
            if (mJourView.getAdapter() != null) { ((JourneyListAdapter)mJourView.getAdapter()).CleanUp(); }
            mJourView.setAdapter(new JourneyListAdapter(this, R.layout.listview_journey, mPersonal, ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) != Configuration.SCREENLAYOUT_SIZE_NORMAL)));

            LogView.Debug(TAG, "JourView OK");
            if (((JourneyListAdapter)mJourView.getAdapter()).InitOK())
            {
                //Set up Map View
                mJourMapF = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.route_map_view);
                mJourMapF.getMapAsync(this);

                //Set visibility of main layout
                mDataSend.setVisibility(View.VISIBLE);
                mWait.setVisibility(View.GONE);
                registerForContextMenu(mJourView);

                LogView.Debug(TAG, "Init OK");
            }
            else
            {
                LogView.Warn(TAG, "Init Error.");
                this.finish();
            }
        }
        else
        {
            LogView.Error(TAG, "JourFile inexist");
            this.finish();  //Nothing else to do...
        }
        LogView.Debug(TAG, "x OnCreate");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        if (mPersonal)
        {
            getMenuInflater().inflate(R.menu.personal_history_menu, menu);
        }
        else
        {
            getMenuInflater().inflate(R.menu.data_send_menu, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            onBackPressed();
            return true;
        }
        else
        {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed()
    {
        LogView.Debug(TAG, "OnBackPress");
        if (!mPersonal) { ((JourneyListAdapter)mJourView.getAdapter()).saveToFile(false); } //No need to do any auxiliary saving if personal, since cannot change params...
        ((JourneyListAdapter)mJourView.getAdapter()).CleanUp();
        this.finish();
    }

    @Override
    public void onMapReady(GoogleMap map)
    {
        mJourMapM = map;                            //Assign pointer
        mJourView.setOnItemClickListener(this);     //Add the OnClick Listener function
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
    {
        LogView.Debug(TAG, "ItemClick " + Integer.toString(position));

        //Indicate selection of this node...
        ((JourneyListAdapter)mJourView.getAdapter()).setSelected(position);

        //Now Handle Map - First Add Line
        if (mPrevLine != null)  { mPrevLine.remove(); }
        mPrevLine = mJourMapM.addPolyline(((JourneyListAdapter)mJourView.getAdapter()).getJourney(position).getPolyLine(5, Color.BLUE));

        //Next Add start/stop markers
        if (mStartM != null)    { mStartM.remove(); }
        mStartM = mJourMapM.addMarker(new MarkerOptions().position(((JourneyListAdapter)mJourView.getAdapter()).getJourney(position).getPt(0).Point())
                .title("Start")
                .snippet(((JourneyListAdapter)mJourView.getAdapter()).getJourney(position).toString())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        if (mEndM != null) { mEndM.remove(); }
        mEndM = mJourMapM.addMarker(new MarkerOptions().position(((JourneyListAdapter)mJourView.getAdapter()).getJourney(position).getEnd().Point())
                .title("End")
                .snippet(((JourneyListAdapter)mJourView.getAdapter()).getJourney(position).toString())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        //Then Zoom in to location
        mJourMapM.moveCamera(CameraUpdateFactory.newLatLngBounds(((JourneyListAdapter) mJourView.getAdapter()).getJourney(position).getBounds(), 10));
    }

    /**
     * Function to handle upload of data to server
     * @param item
     */
    public void onUploadData(MenuItem item)
    {
        LogView.Debug(TAG, "onUpData");

        if (((JourneyListAdapter) mJourView.getAdapter()).getEnabledIndices().size() < 1)
        {
            Utilities.GenerateMessage(this, "Send Journeys", "No Journeys Selected. You must check at least 1 Journey to upload.", null).show();
        }
        else
        {
            if (mWorker == null)    //Only proceed if not doing anything in background thread
            {
                mKeepUpload = Utilities.GenerateQuery(this, "Send Journeys", "The journeys will now be sent to our server and deleted from your device. Do you wish to keep a backup for your personal history?", this, this);
                mKeepUpload.show();
            }
            else
            {
                Toast.makeText(this, "Cannot upload: Comms Busy", Toast.LENGTH_SHORT).show();
                LogView.Warn(TAG, "Wrk Err");
            }
        }
    }

    /**
     * Function to handle cleaning of data... will cause popup...
     * @param item
     */
    public void onClearData(MenuItem item)
    {
        if (((JourneyListAdapter) mJourView.getAdapter()).getEnabledIndices().size() < 1)
        {
            Utilities.GenerateMessage(this, "Clear Journeys", "No Journeys Selected. You must check at least 1 Journey to delete.", null).show();
        }
        else
        {
            if (mPersonal)
            {
                mClearDialog = Utilities.GenerateQuery(this, "Clear Journeys", "Are you sure you wish to clear the selected journeys from your personal history? This operation is irreversible.", this, this);
                mClearDialog.show();
            }
            else
            {
                mKeepDelete = Utilities.GenerateQuery(this, "Clear Journeys", "Do you wish to keep a backup of the selected journeys in your personal history?", this, this);
                mKeepDelete.show();
            }
        }
    }

    public void onHelp(MenuItem item) { LogView.Debug(TAG, "onHelp"); startActivity(new Intent(this, HelpActivity.class));}

    @Override
    /**
     * \brief For implementing the on-click listener for the OK/cancel button
     */
    public void onClick (DialogInterface dialog, int which)
    {
        LogView.Debug(TAG, "onClick");
        if (dialog == mKeepUpload)
        {
            //If the user opted to store them, store them...
            if (which == DialogInterface.BUTTON_POSITIVE)
            {
                boolean result = JourneyListAdapter.AppendJourneysToFile(this, true, ((JourneyListAdapter) mJourView.getAdapter()).getEnabledJourneys()); //Store to file
                if (result == false) { Toast.makeText(getApplicationContext(), "Error in Storing to Personal History", Toast.LENGTH_LONG).show(); LogView.Error(TAG, "Error in Personal"); }
            }

            //In any case Send the Data
            LogView.Debug(TAG, "Wrk Ok");
            mWorker = new SendData();
            ((SendData)mWorker).execute();

            //Set View to progress
            mDataSend.setVisibility(View.GONE);
            mWait.setVisibility(View.VISIBLE);
        }
        else if (dialog == mKeepDelete)
        {
            LogView.Debug(TAG, "KHDlg");
            switch (which)
            {
                case DialogInterface.BUTTON_POSITIVE:
                    JourneyListAdapter.AppendJourneysToFile(this, true, ((JourneyListAdapter) mJourView.getAdapter()).getEnabledJourneys()); //Store to file
                    mClearDialog = Utilities.GenerateQuery(this, "Clear Journeys", "The journeys have been stored to personal history and will now be deleted. Proceed?", this, this);
                    mClearDialog.show();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    mClearDialog = Utilities.GenerateQuery(this, "Clear Journeys", "Note that once deleted the journeys cannot be recovered. Proceed?", this, this);
                    mClearDialog.show();
                    break;
            }
            mKeepDelete = null;
        }
        else if (dialog == mClearDialog)
        {
            LogView.Debug(TAG, "ClrDlg");
            switch(which)
            {
                case DialogInterface.BUTTON_POSITIVE:
                    //First clean up
                    ((JourneyListAdapter)mJourView.getAdapter()).removeList(((JourneyListAdapter) mJourView.getAdapter()).getEnabledIndices());  //Remove those enabled...
                    ((JourneyListAdapter)mJourView.getAdapter()).saveToFile(mPersonal);
                    LogView.Info(TAG, "Clr Sel");
                    //Clean up display as well if the selected entry is gone...
                    if (((JourneyListAdapter)mJourView.getAdapter()).getSelected() < 0)
                    {
                        if (mPrevLine != null) { mPrevLine.remove(); }
                        if (mStartM != null)   { mStartM.remove(); }
                        if (mEndM != null)     { mEndM.remove(); }
                        mJourMapM.clear();
                    }
                    //If no journeys remaining, then finish
                    if (((JourneyListAdapter)mJourView.getAdapter()).getNumJour() < 1) { finish(); }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    LogView.Debug(TAG, "Cancel Clr");
                    break;
            }
            mClearDialog = null;
        }
    }

    protected void afterUploadData(Exception result)
    {
        LogView.Debug(TAG, "aft UpData");
        //Irrespective of where we stand, first clean up any that have been successfully sent...
        if ((((SendData)mWorker).mSndSuc != null) && (((SendData)mWorker).mSndSuc.size() > 0))
        {
            ((JourneyListAdapter)mJourView.getAdapter()).removeList(((SendData)mWorker).mSndSuc);
        }
        ((JourneyListAdapter)mJourView.getAdapter()).saveToFile(false);

        //Now Verify result
        if (result != null) { Utilities.DisplayException(this, result, null, null); }
        else                { LogView.Info(TAG, "Up Data OK"); }

        //Revert to main layout
        mDataSend.setVisibility(View.VISIBLE);
        mWait.setVisibility(View.GONE);

        //Clean up display as well if the selected entry is gone...
        if (((JourneyListAdapter)mJourView.getAdapter()).getSelected() < 0)
        {
            if (mPrevLine != null) { mPrevLine.remove(); }
            if (mStartM != null)   { mStartM.remove(); }
            if (mEndM != null)     { mEndM.remove(); }
            mJourMapM.clear();
        }

        //Nullify Worker
        mWorker = null;

        //Finish activity if all sent successfully
        if (((JourneyListAdapter)mJourView.getAdapter()).getNumJour() < 1) { finish(); }
    }

    /**
     * AsyncTask for sending the Data to the Server...
     */
    protected class SendData extends AsyncTask<Void, Void, Void>
    {
        Exception           mResult = null;
        ArrayList<Journey>  mSndRte = null;
        ArrayList<Integer>  mSndIdx = null;
        ArrayList<Integer>  mSndSuc = null;

        @Override
        protected void onPreExecute()
        {
            mSndRte = ((JourneyListAdapter)mJourView.getAdapter()).getEnabledJourneys();
            mSndIdx = ((JourneyListAdapter)mJourView.getAdapter()).getEnabledIndices();
            if (mSndRte.size() < 1) { LogView.Warn(TAG, "No Jour Err"); mResult = new Exception("No Journey Selected!"); }
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            //If no journey selected, then throw exception
            if (mResult != null) { return null; }

            SecureConnector connector = new SecureConnector(getApplicationContext());

            //Attempt to Connect
            mResult = connector.Connect(); if (mResult != null) { LogView.Error(TAG, "Connx Error"); return null; }
            mSndSuc = new ArrayList<>(1);

            //Send all available data
            for (int i=0; i<mSndRte.size(); i++)
            {
                mResult = connector.SendJourney(mSndRte.get(i));
                if (mResult != null) { LogView.Warn(TAG, "Jour " + Integer.toString(i) + " error"); break; }

                //Else, if successful, notify that no longer needed
                mSndSuc.add(mSndIdx.get(i));
                LogView.Debug(TAG, "Jour " + Integer.toString(i) + " ok");
            }

            LogView.Debug(TAG, "Disconnx");

            //Disconnect
            connector.Disconnect();

            return null;
        }

        @Override
        protected void onPostExecute(Void param)
        {
            afterUploadData(mResult);
        }
    }
}
