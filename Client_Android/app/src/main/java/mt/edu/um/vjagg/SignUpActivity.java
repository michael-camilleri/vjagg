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

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.security.InvalidParameterException;

/**
 * A login screen that offers login via email/password.
 *
 * [Converted to using primitives]
 */
public class SignUpActivity extends Activity implements OnItemSelectedListener, OnClickListener, OnCancelListener
{
    //Classifications
    public static final int GENDER_MALE   = 0;
    public static final int GENDER_FEMALE = 1;

    public static final int AGE_LIMIT    = 1;   //!< The Default Value: This allows me to change the lower age limit on the fly
    public static final int AGE_TEEN     = 0;
    public static final int AGE_UNDER25  = 1;
    public static final int AGE_YNGADULT = 2;
    public static final int AGE_ADULT    = 3;
    public static final int AGE_ELDERLY  = 4;

    public static final int POS_STUDENT  = 0;
    public static final int POS_ACADEMIC = 1;
    public static final int POS_ADMINIST = 2;
    public static final int POS_VISITOR  = 3;

    //UI References - Views.
    private View mStep0;
    private View mStep1;
    private View mStep2;
    private View mWait;

    //UI References - TextBoxes
    private TextView    mLoginIDTxt;

    //UI References - Buttons
    private Button      mCancelBtn;

    //Async Tasks...
    private AsyncTask   mWorker;    //Generic pointer to worker task
    private Dialog      mCheckPlayDlg;  //Dialog for checking play services
    private Dialog      mCheckExitDlg;  //Really want to exit?

    //Other Variables
    private boolean     mSignUpOK;   //Indicates whether sign up is ok
    private int         mGender;     //!< Actual Gender Variable
    private int         mAgeGrp;     //!< Age Group
    private int         mPosition;   //!< Relation to University
    private boolean     mCarAcc;     //!< Access to car

    //Activity Result Codes
    private static final int UPDATE_GOOGLE  = 2;    //!< Activity request code for Updating Google Play Services

    //Debugging/Logging
    private static final String TAG = "SUA";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        //Enforce Portrait
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //Initialise Some Key Variables
        mWorker     = null;     //Set to null currently
        mCheckExitDlg = null;
        mCheckPlayDlg = null;
        mSignUpOK   = false;    //Currently not logged in until after checking at least...
        mGender     = GENDER_MALE;
        mAgeGrp     = AGE_LIMIT;
        mPosition   = POS_STUDENT;
        mCarAcc     = true;

        //First Check for Google Play Services... and act on the result using either the cancel, click or the onActivityResult framework
        checkPlayServices();
    }

    /**
     * \brief Override of the onCancel interface function
     * \detail This may be called ONLY from within the CheckPlayServices() functionality, and will indicate that user did not wish to update play services...
     * @param d The interface which called it (actually, useless)
     */
    @Override
    public void onCancel(DialogInterface d)
    {
        LogView.Debug(TAG, "onCancel");
        mSignUpOK = false;
        exitApp(); //We have to exit, since dependencies not met
    }

    /**
     * \brief Override of the onClick interface function
     * \detail This may be called in two situations:
     *          > As a result of the CheckPlayServices() function not being able to resolve the error... in this case the BUTTON_POSITIVE is called... or there is no error (BUTTON_NEUTRAL)
     *          > As a result of the BackPressed() query on whether the user really wishes to exit... if so, again the BUTTON_POSITIVE is called, otherwise the BUTTON_NEGATIVE is called
     *         If the button pressed is indeed the positive, this means that the user wishes to exit...
     * @param dialog    The Dialog which called it... not used
     * @param which     The Button pressed
     */
    @Override
    public void onClick (DialogInterface dialog, int which)
    {
        LogView.Debug(TAG, "onClick");
        if (dialog == mCheckPlayDlg)
        {
            switch(which)
            {
                case DialogInterface.BUTTON_NEGATIVE:
                    exitApp();  //He chose to cancel... so the only thing to do is exit app
                    break;
            }
            mCheckPlayDlg = null;
        }
        else if (dialog == mCheckExitDlg)
        {
            switch(which)
            {
                case DialogInterface.BUTTON_POSITIVE:   //Then we need to exit...
                    exitApp();
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;  //Just continue from where we left

            }
            mCheckExitDlg = null;
        }

    }

    /**
     * \brief Override of the onBackPressed functionality
     * \details This is called when the user presses the Cancel Button or the back button. IT must not be called from anywhere else...
     *
     */
    @Override
    public void onBackPressed()
    {
        if (mSignUpOK) { setResult(0); finish(); }
        else
        {
            mCheckExitDlg = Utilities.GenerateQuery(this, "Stop Sign Up Process", "Are you sure you wish to halt the signup process? You will not be able to use the app unless you sign in.", this, this);
            mCheckExitDlg.show();
        }
    }

    /**
     * \brief Override of the onActivityResult Interface Function
     * \detail  This may be called from two situations:
     *           > As a result of the Security Certificate Request Location: will be either RESULT_OK or RESULT_CANCEL
     *           > As a result of the Updating Google Play Services: will be RESULT_OK if successful, else will exit...
     * @param requestCode   The Request code which was sent to the activity
     * @param resultCode    The result code returned...
     * @param data          Intent Data (unused)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        //Handle Different Activity Results
        switch(requestCode)
        {
            case UPDATE_GOOGLE: //TODO Check because result code is never being RESULT_OK even if it works...Probably just recheck the condition instead of the resultCode...
                if (resultCode == RESULT_OK)
                {
                    StartSignUp();
                }
                else
                {
                    exitApp();
                }
                break;

            default:
                Utilities.DisplayException(this, new InvalidParameterException(Integer.toString(requestCode)), null, null);
                break;
        }
    }

    /**
     * \brief Override for the Item Selection for the Sign Up Activity to handle changes to the variables
     * @param parent    The Parent View object
     * @param view      ??
     * @param pos       The position of the selection within the menu
     * @param id        ??
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
    {
        switch(parent.getId())
        {
            case R.id.select_gender:
                mGender = pos;
                break;

            case R.id.select_age:
                mAgeGrp = pos + AGE_LIMIT;
                break;

            case R.id.select_position:
                mPosition = pos;
                break;

            case R.id.select_car_access:
                mCarAcc = (pos == 0);
                break;

        }
    }

    /**
     * \brief Forces an exit of the app
     */
    private void exitApp()
    {
        setResult(MainActivity.REQUEST_EXIT);
        finish();
    }

    protected void StartSignUp()
    {
        LogView.Debug(TAG, "StartSignUp");

        //First locate the Files, since if existent, then can just finish...
        if (SecureConnector.CheckFiles(this)) { setResult(0); finish(); LogView.Debug(TAG, "ID Files OK"); return; }   //Nothing else to do...
        LogView.Debug(TAG, "ID Files Missing");

        // Else,Set up the sign-up screen - connect the links
        mStep0 = findViewById(R.id.sign_up_intro);
        mStep1 = findViewById(R.id.sign_up_details);
        mStep2 = findViewById(R.id.sign_up_success);
        mWait  = findViewById(R.id.sign_up_wait);

        mLoginIDTxt = (TextView)findViewById(R.id.sign_up_success_login_id);
        mCancelBtn = (Button)findViewById(R.id.sign_up_cancel);

        //Setup spinners
        Spinner gendersel = (Spinner)findViewById(R.id.select_gender);
        gendersel.setAdapter(ArrayAdapter.createFromResource(this, R.array.gender_selection, android.R.layout.simple_spinner_item));
        ((ArrayAdapter) gendersel.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gendersel.setOnItemSelectedListener(this);

        Spinner agegrpsel = (Spinner)findViewById(R.id.select_age);
        agegrpsel.setAdapter(ArrayAdapter.createFromResource(this, R.array.age_grp_select, android.R.layout.simple_spinner_item));
        ((ArrayAdapter) agegrpsel.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        agegrpsel.setOnItemSelectedListener(this);

        Spinner positionsel = (Spinner)findViewById(R.id.select_position);
        positionsel.setAdapter(ArrayAdapter.createFromResource(this, R.array.pos_selection, android.R.layout.simple_spinner_item));
        ((ArrayAdapter) positionsel.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        positionsel.setOnItemSelectedListener(this);

        Spinner caraccsel = (Spinner)findViewById(R.id.select_car_access);
        caraccsel.setAdapter(ArrayAdapter.createFromResource(this, R.array.car_access_selection, android.R.layout.simple_spinner_item));
        ((ArrayAdapter) caraccsel.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        caraccsel.setOnItemSelectedListener(this);

        SwitchView(0); //Switch to first view
    }


    @Override
    public void onNothingSelected (AdapterView<?> parent)
    {
        switch(parent.getId()) //Set Defaults
        {
            case R.id.select_gender:
                mGender = GENDER_MALE;
                break;

            case R.id.select_age:
                mAgeGrp = AGE_LIMIT;
                break;

            case R.id.select_position:
                mPosition = POS_STUDENT;
                break;

            case R.id.select_car_access:
                mCarAcc = true;
                break;
        }
    }

    private void SwitchView (final int view)
    {
        //Set visibility of view(s)
        mStep0.setVisibility(view == 0 ? View.VISIBLE : View.GONE);
        mStep1.setVisibility(view == 1 ? View.VISIBLE : View.GONE);
        mStep2.setVisibility(view == 2 ? View.VISIBLE : View.GONE);
        mWait.setVisibility(view == 3 ? View.VISIBLE : View.GONE);

        //Set visibility of cancel button to gone if last screen (3) or Wait (4)
        mCancelBtn.setVisibility(view < 2 ? View.VISIBLE : View.GONE);
    }

    public void onProceed(View view)
    {
        SwitchView(1);
    }

    public void onCancelBtn(View view)
    {
        onBackPressed();    //Just call on Back Pressed
    }


    public void onSignUp(View view)
    {
        if (mWorker == null)    //Only proceed if not doing anything in background thread
        {
            SwitchView(3);  //Switch to wait view...
            mWorker = new SignUp();
            ((SignUp)mWorker).execute();
        }
        else
        {
            LogView.Debug(TAG, "Worker Error");
        }
    }

    private void afterSignUp(Exception result)
    {
        if (result != null)
        {
            Utilities.GenerateMessage(this, "Communication Error!", getString(R.string.retry_content) + "\r\n\nDetails of Error: " + result.toString(), null).show();
            SwitchView(1);
        }
        else
        {
            mSignUpOK = true;   //!< Sign Up Was successful
            SwitchView(2);
        }
    }

    public void onContinueMS(View view)
    {
        setResult(0);   //Should only be called when successful
        finish();
    }

    /**
     * \brief Check for availability of Google Play Services APK
     */
    private void checkPlayServices()
    {
        LogView.Debug(TAG, "CheckPlayServices");
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(getApplicationContext());
        if(result != ConnectionResult.SUCCESS)
        {
            LogView.Debug(TAG, "Err (" + Integer.toString(result) + ")");
            if (googleAPI.isUserResolvableError(result))
            {
                LogView.Debug(TAG, "Err OK");
                mCheckPlayDlg = googleAPI.getErrorDialog(this, result, UPDATE_GOOGLE, this);
                mCheckPlayDlg.show();
            }
            else
            {
                LogView.Error(TAG, "Err Prob");
                mCheckExitDlg = Utilities.GenerateMessage(this, "Google Play Services Error", "This device is missing Google Play Services and the app was unable to sort out the error.\n" +
                        "Please attempt to reinstall Google Play Services manually.", this);
                mCheckExitDlg.show();
            }
        }
        else
        {
            LogView.Debug(TAG, "PlayServices OK");
            StartSignUp();
        }
    }


    protected class SignUp extends AsyncTask<Void, Void, Exception>
    {
        protected String str_io;    //For Inter-thread communication

        protected int     mGenderCpy, mAgeGrpCpy, mPositCpy;    //To avoid Threading issues
        protected boolean mAccessCpy;

        @Override
        protected void onPreExecute()
        {
            mGenderCpy = mGender;
            mAgeGrpCpy = mAgeGrp;
            mPositCpy  = mPosition;
            mAccessCpy = mCarAcc;
        }

        @Override
        protected Exception doInBackground(Void... params)
        {
            LogView.Debug(TAG, "SignUp");
            SecureConnector signup_connector = new SecureConnector(getApplicationContext());
            Exception result = signup_connector.Connect();

            if (result == null) { result = signup_connector.RequestIDentification(mGenderCpy, mAgeGrpCpy, mPositCpy, mAccessCpy); }

            if (result == null) { str_io = signup_connector.getIdentification(); result = signup_connector.Disconnect(); }

            return result;
        }

        @Override
        protected void onPostExecute(final Exception result)
        {
            mWorker = null; //Nullify Worker
            if (result == null) { mLoginIDTxt.setText(str_io + "*"); }
            afterSignUp(result);
        }
    }
}

