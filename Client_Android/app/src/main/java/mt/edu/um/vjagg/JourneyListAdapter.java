package mt.edu.um.vjagg;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by Michael Camilleri on 04/03/2016.
 *
 * [Converted to using primitive types]
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
 */

//TODO Not sure if I still have a memory leak

public class JourneyListAdapter extends ArrayAdapter<Journey>
{
    /**
     * \brief Wrapper for each Item
     */
    private static class JourneyListItem
    {
        //!< Reference to the Journey it controls
        private Journey mJourney;
        private boolean mChecked;   //Indicates whether selected (checked) [reference]

        public JourneyListItem(Journey journey)
        {
            //Set up References
            mJourney = journey;
            mChecked = false;           //Initially false
        }
    }

    private static class JourneyListView implements CompoundButton.OnCheckedChangeListener, View.OnClickListener, DialogInterface.OnMultiChoiceClickListener, DialogInterface.OnClickListener
    {
        //!< Control Members
        public  TextView mTitle;
        private TextView mTimes;
        private Button   mModeBtn;
        private Button   mPurpBtn;
        private CheckBox mCheckBox;
        private boolean[] mOldMode; //Keep back of old mode
        private int      mOldPurp;  //Keep track of old purpose
        private boolean  mShowMode; //Shows purpose/mode selection or not

        private JourneyListItem              mItem;      //!< Reference to the attached Journey List item
        private WeakReference<ArrayAdapter>  mAdapter;   //!< Weak reference to the array adapter
        private int                          mClicked;   //!< Which button was clicked... (mode = 0, purpose = 1)

        JourneyListView(View v, ArrayAdapter adapter, boolean show_purpose_mode)
        {
            //Expand View
            mTitle    = ((TextView) v.findViewById(R.id.firstLine));
            mTimes    = ((TextView) v.findViewById(R.id.times));
            mModeBtn  = ((Button)   v.findViewById(R.id.mode_button));
            mPurpBtn  = ((Button)   v.findViewById(R.id.purpose_button));
            mCheckBox = ((CheckBox) v.findViewById(R.id.journey_check));
            mOldMode = null;
            mOldPurp = -1;
            mAdapter = new WeakReference<>(adapter);
            mClicked = -1;
            mShowMode = show_purpose_mode;

            //Set up Listener(s)
            mCheckBox.setOnCheckedChangeListener(this);
            mModeBtn.setOnClickListener(this);
            mPurpBtn.setOnClickListener(this);
        }

        public void SetItem(JourneyListItem item)   //Updates the item associated with the view
        {
            mItem = item;

            mTitle.setText(mItem.mJourney.getTitle());
            mTimes.setText(mItem.mJourney.getTimes());
            if (mShowMode)
            {
                mModeBtn.setText(mItem.mJourney.getStrMode());
                mPurpBtn.setText(mItem.mJourney.getStrPurp());
            }
            else
            {
                mModeBtn.setText("Mode");
                mPurpBtn.setText("Purpose");
            }

            mCheckBox.setChecked(mItem.mChecked);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            mItem.mChecked = buttonView.isChecked();
        }

        @Override
        public void onClick(View v)
        {
            if (v == mModeBtn)
            {
                //Obtain which are checked...
                mOldMode = mItem.mJourney.getValMode();

                //Initialise Dialog Fragment
                AlertDialog.Builder builder = new AlertDialog.Builder(mParent.get());
                builder.setTitle("Set Trip Mode");
                builder.setMultiChoiceItems(Journey.MODE_SELECT_STR, mOldMode, this);
                builder.setPositiveButton("OK", this);
                builder.setNegativeButton("Cancel", this);

                //Show
                builder.create().show();
                mClicked = 0;
            }
            else
            {
                //Obtain current selection
                mOldPurp = mItem.mJourney.getValPurp();

                //Initialise Dialog Fragment
                AlertDialog.Builder builder = new AlertDialog.Builder(mParent.get());
                builder.setTitle("Set Trip Purpose");
                builder.setSingleChoiceItems(Journey.PURP_SELECT_STR, mOldPurp, this);
                builder.setPositiveButton("OK", this);
                builder.setNegativeButton("Cancel", this);

                //Show
                builder.create().show();
                mClicked = 1;
            }
        }

        @Override
        public void onClick(DialogInterface dialog, int which, boolean isChecked) { /*Nothing to do*/ }

        @Override
        public void onClick(DialogInterface dialog, int which)
        {
            switch (which)
            {
                case DialogInterface.BUTTON_POSITIVE:
                    if (mClicked == 0) { mItem.mJourney.setMode(mOldMode); }
                    if (mClicked == 1) { mItem.mJourney.setPurpose(mOldPurp); }
                    mClicked = -1;
                    mAdapter.get().notifyDataSetChanged();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    mClicked = -1;
                    break;

                default:
                    if (mClicked == 1) { mOldPurp = which; }
                    break;
            }
        }

    }

    //File Definitions
    private static WeakReference<Activity>      mParent;    //!< Weak reference to the parent activity

    //Global Specifics
    private int     mResource;
    //private boolean mPersonal; //!< Is this the personal or the normal history...
    private boolean mShowDesc;

    //Journey Lists
    private ArrayList<JourneyListItem> mJourneyItems;
    private int                        mSelected;       //Indicates which is the selected journey...

    //Debugging
    private static final String TAG = "JLA";
    private boolean mSuccess;

    //Constructor
    public JourneyListAdapter(Activity context, int resource, boolean personal, boolean show_purpose_mode)
    {
        super(context, resource);
        mResource = resource;
        mSuccess = false;
        mSelected = -1;         //None are selected...
        mShowDesc = show_purpose_mode;

        //Set up Weak Reference(s)
        mParent = new WeakReference<>(context);

        try
        {
            LogView.Debug(TAG, "<Ctor>");
            File file = new File(context.getFilesDir(), personal ? TrackingService.GPS_PER_FILE : TrackingService.GPS_RPP_FILE);
            BufferedInputStream gps_reader = new BufferedInputStream(new FileInputStream(file));
            mJourneyItems = new ArrayList<>();  //create a list

            while (true)    //Will only exit once an exception is thrown or we break out
            {
                //Load Journey
                Journey tmp_journey = new Journey();
                Exception result = tmp_journey.loadRoute(gps_reader);

                //Handle Result
                if (result == null)
                {
                    mJourneyItems.add(new JourneyListItem(tmp_journey));
                    LogView.Debug(TAG, "Found!");
                }
                else if (result instanceof EOFException)
                {
                    LogView.Debug(TAG, "EOF.");
                    break;
                }
                else if (result instanceof InvalidParameterException)
                {
                    LogView.Error(TAG, result.toString());
                    continue;
                }
                else
                {
                    throw result;
                }
            }

            mSuccess = true;    //If arrived up to this point
        }
        catch (Exception e)
        {
            //Not sure what to do...
            LogView.Error(TAG, e.toString());
        }
        finally
        {
            UpdateView(); //always update the view!
        }
    }

    public static boolean AppendJourneysToFile(Context context, boolean personal, ArrayList<Journey> journeys)
    {
        ArrayList<Journey> curr_file;
        HashSet<Long>      curr_ids;

        //First read in the current file if any
        curr_file = new ArrayList<>();  //create a list
        curr_ids  = new HashSet<>();


        File file = new File(context.getFilesDir(), personal ? TrackingService.GPS_PER_FILE : TrackingService.GPS_RPP_FILE);
        if (file.exists())
        {
            try
            {
                BufferedInputStream gps_reader = new BufferedInputStream(new FileInputStream(file));
                while (true)    //Will only exit once an exception is thrown or we break out
                {
                    //Load Journey
                    Journey tmp_journey = new Journey();
                    Exception result = tmp_journey.loadRoute(gps_reader);

                    //Handle Result
                    if (result == null)
                    {
                        curr_file.add(tmp_journey);
                        curr_ids.add(tmp_journey.getLngIdent());
                        LogView.Debug(TAG, "Found!");
                    }
                    else if (result instanceof EOFException)
                    {
                        LogView.Debug(TAG, "EOF.");
                        break;
                    }
                    else if (result instanceof InvalidParameterException)
                    {
                        LogView.Error(TAG, result.toString());
                        continue;
                    }
                    else
                    {
                        throw result;
                    }
                }
            }
            catch (Exception e)
            {
                return false;
            }
            file.delete(); //Delete old file...
        }

        //Now add the journeys present in the array list passed, if the id does not exist already
        for (int i = 0; i<journeys.size(); ++i)
        {
            if (!curr_ids.contains(journeys.get(i).getLngIdent())) { curr_file.add(journeys.get(i)); }
        }

        //Finally save everything
        for(Journey jour : curr_file)
        {
            Exception result = jour.storeRoute(Journey.TC_UNSPEC, file);
            if (result != null) { LogView.Error(TAG, result.toString()); return false; }
        }
        return true;
    }

    /**
     * \brief Indicates if initialisation from file was successful...
     * @return
     */
    boolean InitOK()
    {
        return mSuccess;
    }

    void saveToFile(boolean personal)
    {
        LogView.Debug(TAG, "save");

        //First clear the current file
        (new File(mParent.get().getFilesDir(), personal ? TrackingService.GPS_PER_FILE : TrackingService.GPS_RPP_FILE)).delete();

        //NowStore all journeys still active...
        for(JourneyListItem item : mJourneyItems)
        {
            Exception result = item.mJourney.storeRoute(Journey.TC_UNSPEC, (new File(mParent.get().getFilesDir(), personal ? TrackingService.GPS_PER_FILE : TrackingService.GPS_RPP_FILE)));
            if (result != null) { LogView.Error(TAG, result.toString()); }
        }
    }

    /**
     * \brief Cleans up the resources to avoid memory leaks
     */
    void CleanUp()
    {
        mParent       = null;
        mJourneyItems = null;
    }

    /**
     * \brief Returns the set of journeys which are enabled...
     * @return
     */
    ArrayList<Journey> getEnabledJourneys()
    {
        ArrayList<Journey> tmp_list = new ArrayList<>(1);

        for (JourneyListItem item : mJourneyItems) { if (item.mChecked) { tmp_list.add(item.mJourney); } }

        return tmp_list;
    }

    /**
     * \brief Returns a set of integers which are the indices of the enabled journeys
     * @return
     */
    ArrayList<Integer> getEnabledIndices()
    {
        ArrayList<Integer> tmp_list = new ArrayList<>(1);

        for (int i=0; i<mJourneyItems.size(); i++) { if (mJourneyItems.get(i).mChecked) { tmp_list.add(i); } }

        return tmp_list;
    }

    /**
     * \brief Remove the elements at the specified indices from the array...
     * @param toRemove The list of elements to remove...
     */
    void removeList(List<Integer> toRemove)
    {
        //Start off from the end, so that indices remain valid...
        for (int i=toRemove.size()-1; i>=0; i--)
        {
            if      (toRemove.get(i).intValue() == mSelected) { mSelected = -1; }    //None are selected now...
            else if (toRemove.get(i).intValue() <  mSelected) { --mSelected;    }    //We need to subtract one value from mSelected...
            mJourneyItems.remove(toRemove.get(i).intValue());
        }

        //Update the View
        UpdateView();
    }

    /**
     * \brief Update which view is selected...
     * @param selected which are selected...
     */
    void setSelected(int selected)
    {
        mSelected = selected;
        notifyDataSetChanged();
    }

    int getSelected()
    {
        return mSelected;
    }

    /**
     * \brief Updates the view of the parent object as well as cleaning up all internals
     */
    private void UpdateView()
    {
        //Clean up
        super.clear();

        //Populate
        for (int i=0; i<mJourneyItems.size(); i++) { super.add(mJourneyItems.get(i).mJourney); }

        //Indicate that the dataset was changed
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        View view;

        //Initialise the view object
        if (convertView == null) //Then we need to create a new view
        {
            view = mParent.get().getLayoutInflater().inflate(mResource, null);
            view.setTag(new JourneyListView(view, this, mShowDesc));
        }
        else
        {
            view = convertView;
        }

        //Update View object itself
        ((JourneyListView)view.getTag()).SetItem(mJourneyItems.get(position));
        if (mSelected != position) { view.setBackgroundColor(0xFFDFD4C4); }
        else                       { view.setBackgroundColor(0xFFB57E6D); } //Selected color...

        //Return the view
        return view;
    }

    public Journey getJourney(int position) { return mJourneyItems.get(position).mJourney; }
    public int     getNumJour() { return mJourneyItems.size(); }
}
