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

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class HelpActivity extends AppCompatActivity
{
    public static final String TAG = "H";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        LogView.Debug(TAG, "create");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        try
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
            getSupportActionBar().setIcon(R.drawable.vjagg_icon);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        catch (Exception e) { LogView.Error(TAG, e.toString()); }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        LogView.Debug(TAG, "options");
        getMenuInflater().inflate(R.menu.menu_help, menu);
        return true;
    }

    public void onTerms(MenuItem item)
    {
        LogView.Debug(TAG, "terms");//Empty: need to display terms and conditions
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("vjaġġ: Terms & Conditions").setView(getLayoutInflater().inflate(R.layout.dialog_show_terms, null)).create();
        dialog.show();
        ((TextView)dialog.findViewById(R.id.terms_conditions)).setText(R.string.terms_conditions);
    }

    public void onGetID(MenuItem item)
    {
        LogView.Debug(TAG, "id");//Empty: need to display terms and conditions
        Utilities.GenerateMessage(this, "Your ID is:", SecureConnector.PeekIdentity(getApplicationContext()), null).show();
    }
}
