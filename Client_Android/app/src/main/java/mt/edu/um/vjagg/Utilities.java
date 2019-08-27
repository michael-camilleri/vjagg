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

/**
 * Created by Michael Camilleri on 03/02/2016.
 *
 * [Converted to using Primitive Types]
 */

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.support.v7.app.AlertDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;


class Utilities
{
    static final boolean DEBUG_MODE = false;
    static final int     DEBUG_TYPE = 3;     // 0 - Testing IDLE Battery Consumption
                                             // 1 - Testing just the GPS Battery Consumption (base algorithm)
                                             // 2 - Testing just the Accelerometer Consumption (throttled)
                                             // 3 - Testing Accelerometer Always On
                                             // 4 - Testing just the Wakelock

    static final boolean DEBUG_BATTERY = true;  //Enable Battery Logging

    static String ConvertDate(long time_val)
    {
        return (new SimpleDateFormat("E d/MMM/yyyy", Locale.ENGLISH)).format(new Date(time_val));
    }

    static String ConvertTime(long time_val)
    {
        return (new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)).format(new Date(time_val));
    }

    static String FormatDayTime(SettingsActivity.DailyTime time_val)
    {
        return String.format("%02d:%02d", time_val.mHrs, time_val.mMin);
    }

    public static String FormatDayTime(int hours, int mins)
    {
        return String.format("%02d:%02d", hours, mins);
    }

    static int GenerateUnique(Set<Integer> current, int offset)
    {
        if (current.size() < 1) { return 1+offset; }   //Return default
        else
        {
            int unique_id = 1+offset;
            while(current.contains(unique_id)) { unique_id++; }
            return unique_id;
        }
    }

    //TODO improve display of messages... rather than exceptions!
    static void DisplayException(Context c, Exception e, OnClickListener onOK, OnClickListener onCancel)
    {
        LogView.Warn("U", e.toString());
        AlertDialog.Builder dialog = new AlertDialog.Builder(c, R.style.AlertDialogCustom);
        dialog.setTitle("Exception " + e.toString() + " occured...");
        dialog.setMessage(e.getMessage());
        dialog.setPositiveButton("Ok", onOK);
        if (onCancel != null) { dialog.setNegativeButton("Cancel", onCancel); } //Add cancel listener only if required
        dialog.setCancelable(false);
        dialog.create().show();
    }

    static Dialog GenerateMessage(Context c, String title, String message, OnClickListener onOK)
    {
        AlertDialog.Builder dialog = new AlertDialog.Builder(c, R.style.AlertDialogCustom);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setPositiveButton("OK", onOK);
        return dialog.create();
    }

    static Dialog GenerateQuery(Context c, String title, String message, OnClickListener onOK,  OnClickListener onCancel)
    {
        AlertDialog.Builder dialog = new AlertDialog.Builder(c, R.style.AlertDialogCustom);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setPositiveButton("Yes", onOK);
        dialog.setNegativeButton("No", onCancel);
        return dialog.create();
    }
}

