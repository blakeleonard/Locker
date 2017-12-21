/*

Locker: An open-source notepad application utilizing military-grade encryption with a touch of style!
Copyright (C) 2017  Blake Leonard

blake@leonardlabs.net

Leonard Labs
Blake Leonard
1209 Susan St.
Kearney, MO 64060

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/

package net.leonardlabs.locker;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Arrays;

public class ComposeActivity extends BaseActivity {

    LinedEditText composeEditTextNoteBody;
    Button composeButtonSave;
    Toolbar composeToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Setup UI

        setContentView(R.layout.compose);
        composeEditTextNoteBody = findViewById(R.id.composeEditTextNoteBody);
        composeButtonSave = findViewById(R.id.composeButtonSave);

        composeToolbar = findViewById(R.id.composeToolbar);
        composeToolbar.setTitle("Compose Note");
        setSupportActionBar(composeToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        setupComposeListeners();
    }

    // Setup Click & Text Change Listeners
    private void setupComposeListeners() {

        // Toolbar Back Click
        composeToolbar.setNavigationOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                // Close Keyboard
                if (v != null) {

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }

                onBackPressed();
            }
        });

        // Save Button Click
        composeButtonSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                if (!duringEncryptingGraphicalEffect & !dbInUse) {

                    String bodyString = composeEditTextNoteBody.getText().toString();                   // toString Security Vulnerability

                    if (bodyString.length() > 0) {

                        // Close Keyboard
                        if (view != null) {

                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        }

                        newNotePrompt(bodyString);
                    }
                    else {

                        Toast.makeText(getApplicationContext(), "Note is Blank!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        // Note Body Text Change Listener to prevent inputs greater than max lines & max note size
        // Currently deletes last character.  A better solution would be to delete last character input.
        composeEditTextNoteBody.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) { }

            @Override
            public void afterTextChanged(Editable editable) {

                // If current line count is greater than maxlines then delete last character
                if (null != composeEditTextNoteBody.getLayout() && composeEditTextNoteBody.getLayout().getLineCount() > maxLines) {

                    composeEditTextNoteBody.getText().delete(composeEditTextNoteBody.getText().length() - 1, composeEditTextNoteBody.getText().length());
                    Toast.makeText(getApplicationContext(), "Max Note Size is " + maxLines + " Lines!", Toast.LENGTH_SHORT).show();
                }

                // If current note size in characters is greater than max note size, then delete last character
                if (null != composeEditTextNoteBody.getLayout() && composeEditTextNoteBody.getText().length() > maxNoteSize) {

                    composeEditTextNoteBody.getText().delete(composeEditTextNoteBody.getText().length() - 1, composeEditTextNoteBody.getText().length());
                    Toast.makeText(getApplicationContext(), "Max Note Size is " + maxNoteSize + " Characters!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {

        // If Activity was saved during encrypting effect, then return to Main Activity, otherwise restore note body
        if (savedInstanceState.getBoolean("savedduringencryptingeffect")) {

            finish();
        }
        else {

            composeEditTextNoteBody.setText(savedInstanceState.getString("statebody"));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {

        outState.putString("statebody", composeEditTextNoteBody.getText().toString());
        outState.putBoolean("savedduringencryptingeffect", duringEncryptingGraphicalEffect );

        // Call superclass to save any view hierarchy
        // Not sure if necessary.  Test removing this.
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {

        super.onResume();

        // Control Focus & Bring up keyboard
        composeEditTextNoteBody.requestFocus();
        final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
    }

    @Override
    protected void onPause() {

        super.onPause();

        // Close Keyboard if open
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(composeEditTextNoteBody.getWindowToken(),0);
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        // If Activty is destroyed during encrypting effect, then interrupt encrypting graphic effect background thread
        if (duringEncryptingGraphicalEffect) {

            if (encryptingGraphicEffectThread!=null) {

                encryptingGraphicEffectThread.cancel();
            }
        }
    }

    @Override
    public void onBackPressed() {

        // If pressed during encrypting graphic effect, then return to main
        if (!duringEncryptingGraphicalEffect) {

            final String bodyString = composeEditTextNoteBody.getText().toString();

            // If body is empty, then return to main
            if ( bodyString.length() > 0 ) {

                // If Database is in use, do nothing, otherwise prompt user to save
                if (!dbInUse) {

                    // Setup Save Confirmation Alert Dialog
                    LayoutInflater layoutInflater = LayoutInflater.from(this);
                    View savePromptView = layoutInflater.inflate(R.layout.save_prompt, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                    alertDialogBuilder.setView(savePromptView);

                    // Construct Alert Dialog Responses
                    alertDialogBuilder
                            .setCancelable(false)
                            .setPositiveButton("Yes",
                                    new DialogInterface.OnClickListener() {

                                        public void onClick(DialogInterface dialog, int id) {

                                            newNotePrompt(bodyString);
                                        }
                                    })
                            .setNegativeButton("No",
                                    new DialogInterface.OnClickListener() {

                                        public void onClick(DialogInterface dialog, int id) {

                                            // Return to Main
                                            finish();
                                        }
                                    });

                    // Display Alert Dialog
                    final AlertDialog alertDialog = alertDialogBuilder.create();
                    alertDialog.show();

                    // Dismiss Dialog & Return to Main if Back Button Pressed
                    alertDialog.setOnKeyListener(new AlertDialog.OnKeyListener() {

                        @Override
                        public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {

                            if (keyCode == KeyEvent.KEYCODE_BACK) {

                                alertDialog.dismiss();
                                finish();
                            }

                            return false;
                        }
                    });
                }
            }
            else {

                // Body is Empty
                finish();
            }
        }
        else {

            // During Encrypting Graphicl Effect
            finish();
        }
    }

    // Displays New Note Prompt Alert Dialog for entering name and passcode (if encrypted), & starts background threads to save note and perfrom encrypting graphic effect (if encrypted)
    private void newNotePrompt(final String bodyString) {

        // Setup Alert Dialog
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        final View newFilePromptView = layoutInflater.inflate(R.layout.new_note_prompt, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(newFilePromptView);

        final EditText newNotePromptEditTextNewName = newFilePromptView.findViewById(R.id.newNotePromptEditTextNewName);
        final EditText newNotePromptEditTextNewPasscode = newFilePromptView.findViewById(R.id.newNotePromptEditTextNewPasscode);
        final EditText newNotePromptEditTextConfirmPasscode = newFilePromptView.findViewById(R.id.newNotePromptEditTextConfirmPasscode);

        // Construct Alert Dialog Responses
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog,int id) {

                                // Close Keyboard
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(newFilePromptView.getWindowToken(), 0);

                                // If a new Note Name has been entered, then create new note
                                final String newNoteName = newNotePromptEditTextNewName.getText().toString();

                                if (!newNoteName.equals("")) {

                                    // Start Background Thread to Check if Note Name already exists & insert new note (encrypting if necesary)
                                    new Thread(new Runnable() {

                                        public void run() {

                                            // If Database is still in use by another thread, then wait
                                            while (dbInUse) {

                                                // Delay
                                                try {

                                                    Thread.sleep(dbinUsePause);
                                                }
                                                catch (Exception e) {

                                                    //Log.d("A", "Exception to Thread Sleep Request");
                                                }
                                            }

                                            // Check if Note Name is already in use by another note
                                            dbInUse = true;
                                            Cursor cursor = myNoteDb.getAllNames();
                                            boolean noteNameAlreadyExistsFlag = false;

                                            if (cursor.moveToFirst()) {

                                                do {

                                                    String recordNoteName = cursor.getString(NoteDBAdapter.COL_NAME);

                                                    // Only trip flag if note name is equal to name of already existing record
                                                    if (newNoteName.equals(recordNoteName)) {

                                                        noteNameAlreadyExistsFlag = true;

                                                        break;
                                                    }
                                                }
                                                while (cursor.moveToNext());

                                                cursor.close();
                                            }

                                            dbInUse = false;

                                            // If Name doesn't already exist, then proceed, otherwise alert user
                                            if (!noteNameAlreadyExistsFlag) {

                                                // Get Passocde Fields by reading into Character arrays (for later overwriting)
                                                int newPasscodeLength = newNotePromptEditTextNewPasscode.getText().length();
                                                char[] newPasscode = new char[newPasscodeLength];

                                                for (int i = 0; i < newPasscodeLength; i++) {

                                                    newPasscode[i] = newNotePromptEditTextNewPasscode.getText().charAt(i);
                                                }

                                                int confirmPasscodeLength = newNotePromptEditTextConfirmPasscode.getText().length();
                                                char[] confirmPasscode = new char[confirmPasscodeLength];

                                                for (int i = 0; i < confirmPasscodeLength; i++) {

                                                    confirmPasscode[i] = newNotePromptEditTextConfirmPasscode.getText().charAt(i);
                                                }

                                                // Overwrite EditText Fields for security
                                                newNotePromptEditTextNewPasscode.post(new Runnable() {

                                                    public void run() {

                                                        newNotePromptEditTextNewPasscode.setText("");
                                                    }
                                                });

                                                newNotePromptEditTextConfirmPasscode.post(new Runnable() {

                                                    public void run() {

                                                        newNotePromptEditTextConfirmPasscode.setText("");
                                                    }
                                                });

                                                // If a passcode was entered, is less than the maximum size, & matches confirmation passcode, then make note encrypted
                                                if ( (newPasscodeLength > 0) & (confirmPasscodeLength > 0) ) {

                                                    if (newPasscode.length <= maxPasscodeSize) {

                                                        if (Arrays.equals(newPasscode, confirmPasscode)) {

                                                            // Note is encrypted

                                                            // Lock Note Body Edit Text & Save Button & Turn Off Autocorrect for better graphic effect
                                                            composeEditTextNoteBody.post(new Runnable() {

                                                                public void run() {

                                                                    composeEditTextNoteBody.setFocusable(false);
                                                                    composeEditTextNoteBody.setClickable(false);
                                                                    composeEditTextNoteBody.setRawInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                                                                }
                                                            });

                                                            composeButtonSave.post(new Runnable() {

                                                                public void run() {

                                                                    composeButtonSave.setClickable(false);
                                                                }
                                                            });

                                                            // Start Background Thread to perform encrypting graphic effect (will close compose & return to main when finished or interrrupted)
                                                            encryptingGraphicEffectThread = new EncryptingGraphicEffectThread(bodyString, composeEditTextNoteBody, currentBodyDisplayCharsPerLine, currentBodyDisplayLines, currentBodyEffectOrientation);
                                                            encryptingGraphicEffectThread.start();

                                                            databaseUpdateHandler.sendEmptyMessage(ENCRYPTING);

                                                            // If Database is still in use by another thread, then wait
                                                            while (dbInUse) {

                                                                // Delay
                                                                try {

                                                                    Thread.sleep(dbinUsePause);
                                                                }
                                                                catch (Exception e) {

                                                                    //Log.d("A", "Exception to Thread Sleep Request");
                                                                }
                                                            }

                                                            // Save New Note in Database
                                                            dbInUse = true;
                                                            myNoteDb.insertNote(newNoteName, bodyString.getBytes(), newPasscode);
                                                            dbInUse = false;

                                                            databaseUpdateHandler.sendEmptyMessage(NOTE_SAVED);
                                                        }
                                                        else {

                                                            // Passcode & Confirm Passcode Fields do not match
                                                            databaseUpdateHandler.sendEmptyMessage(PASSCODES_DONT_MATCH);
                                                        }
                                                    }
                                                    else {

                                                        // Entered Passcode exceeds max passcode char array size limit
                                                        databaseUpdateHandler.sendEmptyMessage(PASSCODE_TOO_LONG);
                                                    }

                                                    // Overwrite passcode arrays for security
                                                    for (int i = 0; i < newPasscodeLength; i++) {

                                                        newPasscode[i] = 'x';
                                                    }

                                                    newPasscode = null;
                                                    newPasscodeLength = 0;

                                                    for (int i = 0; i < confirmPasscodeLength; i++) {

                                                        confirmPasscode[i] = 'x';
                                                    }

                                                    confirmPasscode = null;
                                                    confirmPasscodeLength = 0;
                                                }
                                                else {

                                                    // Note is not encrypted

                                                    // Lock Note Body & Save Button
                                                    composeEditTextNoteBody.post(new Runnable() {

                                                        public void run() {

                                                            composeEditTextNoteBody.setFocusable(false);
                                                            composeEditTextNoteBody.setClickable(false);
                                                        }
                                                    });

                                                    composeButtonSave.post(new Runnable() {

                                                        public void run() {

                                                            composeButtonSave.setClickable(false);
                                                        }
                                                    });

                                                    // If Database is still in use by another thread, then wait
                                                    while (dbInUse) {

                                                        // Delay
                                                        try {

                                                            Thread.sleep(dbinUsePause);
                                                        }
                                                        catch (Exception e) {

                                                            //Log.d("A", "Exception to Thread Sleep Request");
                                                        }
                                                    }

                                                    // Save Note in Database
                                                    dbInUse = true;
                                                    myNoteDb.insertNote(newNoteName, bodyString.getBytes(), null);
                                                    dbInUse = false;

                                                    databaseUpdateHandler.sendEmptyMessage(NOTE_SAVED);

                                                    // Return to Main
                                                    finish();
                                                }
                                            }
                                            else {

                                                // Note name already exists

                                                // Overwrite EditText Fields for security
                                                newNotePromptEditTextNewPasscode.setText("");
                                                newNotePromptEditTextConfirmPasscode.setText("");

                                                databaseUpdateHandler.sendEmptyMessage(NAME_EXISTS);
                                            }
                                        }
                                    }).start();
                                }
                                else {

                                    // Name wasn't entered.

                                    // Overwrite EditText Fields for security
                                    newNotePromptEditTextNewPasscode.setText("");
                                    newNotePromptEditTextConfirmPasscode.setText("");

                                    Toast.makeText(getApplicationContext(), "Name wasn't entered!", Toast.LENGTH_LONG).show();
                                }
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog,int id) {

                                // Close Keyboard
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(newFilePromptView.getWindowToken(), 0);

                                // Overwrite EditText Fields for security
                                newNotePromptEditTextNewPasscode.setText("");
                                newNotePromptEditTextConfirmPasscode.setText("");

                                dialog.cancel();
                            }
                        });

        // Display Alert Dialog
        final AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();

        // Dismiss Dialog if Back Button Pressed
        alertDialog.setOnKeyListener(new AlertDialog.OnKeyListener() {

            @Override
            public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {

                if (keyCode == KeyEvent.KEYCODE_BACK) {

                    alertDialog.dismiss();
                }

                return false;
            }
        });
    }
}


