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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Random;

public class NoteViewActivity extends BaseActivity {

    Toolbar noteViewToolbar;
    LinedEditText noteViewEditTextNoteBody;
    Button noteViewButtonSave;

    // Graphic Effect Variables
    private char[] decryptedFinalBodyEffectChars;
    private int[] decryptedCarriageReturnPositions;
    private int decryptedNumOfCarriageReturns;

    // Program Flow Booleans
    private boolean returningFromSavedState;
    private boolean passcodeChecked = false;
    private boolean noteDecrypted = false;
    private boolean savedDuringDecryptingEffect = false;
    private boolean duringPasscodeCheck = false;

    // Misc
    private long rowId;
    private char[] passcode = null;
    private String recordBody;
    private String savedInstanceBody = null;

    private CheckPasscodeAndDecryptThread checkPasscodeAndDecryptThread;
    private DecryptingGraphicEffectThread decryptingGraphicEffectThread;
    private static Handler noteBodyHandler;

    // Note Body Handler Codes
    private static final int SET_TEXT_DATABASE_RECORD = 0;
    private static final int SET_TEXT_SAVED_INSTANCE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Setup UI
        setContentView(R.layout.note_view);
        noteViewEditTextNoteBody = findViewById(R.id.noteViewEditTextNoteBody);
        noteViewButtonSave = findViewById(R.id.noteViewButtonSave);

        noteViewToolbar = findViewById(R.id.noteViewToolbar);
        setSupportActionBar(noteViewToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Get Bundle Info Passed from Main Activity
        Bundle extra = getIntent().getExtras();
        rowId = extra.getLong("rowId");
        int passedBodyEffectOrientation = extra.getInt("currentbodyeffectorientation");

        // If Device Orientation is identical to that when bundle was first passed, then use passed random body, otherise reset graphic effect parameters & generate new random body
        if (passedBodyEffectOrientation == currentBodyEffectOrientation) {

            randomBodyEffectChars = extra.getCharArray("randombodyeffectchars");
        }
        else {

            setGraphicEffectParameters();
            setRandomBodyEffectChars();
        }

        // Handle Returning to Activity from Saved State (Activity was destroyed due to device reorientation, etc.)
        returningFromSavedState = false;

        if (savedInstanceState!=null) {

            returningFromSavedState = true;

            // Recover saved variables
            passcode = savedInstanceState.getCharArray("statepasscode");
            savedInstanceBody = savedInstanceState.getString("savedinstancebody");
            recordBody = savedInstanceState.getString("recordbody");
            passcodeChecked = savedInstanceState.getBoolean("passcodechecked");
            noteDecrypted = savedInstanceState.getBoolean("notedecrypted");
            savedDuringDecryptingEffect = savedInstanceState.getBoolean("savedduringdecryptingeffect");

            // If activity was destroyed during encrypting effect, then just return to main activity
            if (savedInstanceState.getBoolean("savedduringencryptingeffect")) {

                finish();
            }
        }

        registerNoteBodyHandler();
        registerNoteViewListeners();
        displayNoteView();
    }

    // Setup Handler to Update Note Body Edit Text
    private void registerNoteBodyHandler() {

        noteBodyHandler = new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(Message message) {

                switch(message.what) {

                    case SET_TEXT_DATABASE_RECORD:

                        // Update Note Body with body saved in database & unlock body & save button

                        if (noteViewEditTextNoteBody != null) {

                            noteViewEditTextNoteBody.setText(recordBody);
                            noteViewEditTextNoteBody.setFocusable(true);
                            noteViewEditTextNoteBody.setFocusableInTouchMode(true);
                            noteViewEditTextNoteBody.setClickable(true);
                        }

                        if (noteViewButtonSave != null) {

                            noteViewButtonSave.setClickable(true);
                        }

                        break;

                    case SET_TEXT_SAVED_INSTANCE:

                        // Update Note Body with body saved if activity is destroyed & unlock body & save button

                        if (noteViewEditTextNoteBody != null) {

                            noteViewEditTextNoteBody.setText(savedInstanceBody);
                            noteViewEditTextNoteBody.setFocusable(true);
                            noteViewEditTextNoteBody.setFocusableInTouchMode(true);
                            noteViewEditTextNoteBody.setClickable(true);
                        }

                        if (noteViewButtonSave != null) {

                            noteViewButtonSave.setClickable(true);
                        }

                        break;
                }
            }
        };
    }

    // Setup Click & Text Change Listeners
    private void registerNoteViewListeners() {

        // Toolbar Back Navigation
        noteViewToolbar.setNavigationOnClickListener(new View.OnClickListener() {

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

        // Save Note Button
        noteViewButtonSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                // If database is in use, or during graphic effect, do nothing.
                if (!duringDecryptingGraphicalEffect & ! duringEncryptingGraphicalEffect & !dbInUse) {

                    // Save(Update) Record

                    final String bodyString = noteViewEditTextNoteBody.getText().toString();

                    // Dont do anything if Body Field is Blank
                    if (bodyString.length() > 0) {

                        // Don't do anything if Note body hasn't changed
                        if (!bodyString.equals(recordBody)) {

                            // Close Keyboard
                            if (view != null) {

                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                            }

                            // Setup Save Confirmation Prompt Alert Dialog
                            LayoutInflater layoutInflater = LayoutInflater.from(NoteViewActivity.this);
                            View savePromptView = layoutInflater.inflate(R.layout.save_prompt, null);
                            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(NoteViewActivity.this);
                            alertDialogBuilder.setView(savePromptView);

                            // Construct Alert Dialog Responses
                            alertDialogBuilder
                                    .setCancelable(false)
                                    .setPositiveButton("Yes",
                                            new DialogInterface.OnClickListener() {

                                                public void onClick(DialogInterface dialog, int id) {

                                                    updateNote(bodyString);
                                                }
                                            })
                                    .setNegativeButton("No",
                                            new DialogInterface.OnClickListener() {

                                                public void onClick(DialogInterface dialog, int id) {

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
                        else {

                            // Note hasn't changed
                            Toast.makeText(getApplicationContext(), "Note Hasn't Changed!", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else {

                        // Body Field is blank
                        Toast.makeText(getApplicationContext(), "Note is Blank!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // Note Body Text Change Listener to prevent inputs greater than max lines & max note size
        // Currently deletes last character.  A better solution would be to delete last character input.
        noteViewEditTextNoteBody.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) { }

            @Override
            public void afterTextChanged(Editable editable) {

                // If current line count is greater than maxlines then delete last character
                if (null != noteViewEditTextNoteBody.getLayout() && noteViewEditTextNoteBody.getLayout().getLineCount() > maxLines) {

                    noteViewEditTextNoteBody.getText().delete(noteViewEditTextNoteBody.getText().length() - 1, noteViewEditTextNoteBody.getText().length());
                    Toast.makeText(getApplicationContext(), "Max Note Size is " + maxLines + " Lines!", Toast.LENGTH_SHORT).show();
                }

                // If current note size in characters is greater than max note size, then delete last character
                if (null != noteViewEditTextNoteBody.getLayout() && noteViewEditTextNoteBody.getText().length() > maxNoteSize) {

                    noteViewEditTextNoteBody.getText().delete(noteViewEditTextNoteBody.getText().length() - 1, noteViewEditTextNoteBody.getText().length());
                    Toast.makeText(getApplicationContext(), "Max Note Size is " + maxNoteSize + " Characters!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {

        outState.putString("savedinstancebody", noteViewEditTextNoteBody.getText().toString());
        outState.putString("recordbody", recordBody);
        outState.putCharArray("statepasscode", passcode);
        outState.putBoolean("passcodechecked", passcodeChecked );
        outState.putBoolean("notedecrypted", noteDecrypted );
        outState.putBoolean("savedduringdecryptingeffect", duringDecryptingGraphicalEffect );
        outState.putBoolean("savedduringencryptingeffect", duringEncryptingGraphicalEffect );

        // call superclass to save any view hierarchy
        // Not sure if necessary.  Test removing this.
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {

        super.onResume();

        refreshDisplay();
    }

    @Override
    protected void onPause() {

        super.onPause();
    }

    @Override
    protected void onStop() {

        super.onStop();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        /// If Activty is destroyed during passcode check or decryption, then interrupt passcode check and decrypt background thread
        if (duringPasscodeCheck || duringDecryption) {

            if (checkPasscodeAndDecryptThread!=null) {

                checkPasscodeAndDecryptThread.cancel();
            }
        }

        // If Activty is destroyed during decrypting effect, then interrupt decrypting graphic effect background thread
        if (duringDecryptingGraphicalEffect) {

            if (decryptingGraphicEffectThread!=null) {

                decryptingGraphicEffectThread.cancel();
            }
        }

        // If Activty is destroyed during encrypting effect, then interrupt encrypting graphic effect background thread
        if (duringEncryptingGraphicalEffect) {

            if (encryptingGraphicEffectThread!=null) {

                encryptingGraphicEffectThread.cancel();
            }
        }
    }

    @Override
    public void onBackPressed() {

        // If during encrypting or decrypting graphic effect, just destroy activity, and return to Main Activity
        if (!duringEncryptingGraphicalEffect & !duringDecryptingGraphicalEffect) {

            final String bodyString = noteViewEditTextNoteBody.getText().toString();

            // If Note Body is unchanged or blank, then return to main, otherwise iniitiate save confirmation prompt
            if (( !recordBody.equals(bodyString) ) & (bodyString.length() > 0 ) ) {

                // If database is in use, do nothing.
                if (!dbInUse) {

                    // Setup Save Confirmation Prompt Alert Dialog
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

                                            updateNote(bodyString);
                                        }
                                    })
                            .setNegativeButton("No",
                                    new DialogInterface.OnClickListener() {

                                        public void onClick(DialogInterface dialog, int id) {

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

                // Notebody is unchanged or blank.
                finish();
            }
        }
        else {

            // During Graphic Effect
            finish();
        }
    }

    @Override
    void refreshDisplay() {

        // For Note View Activity, refresh display consists of simply changing the toolbar title note name.

        // Get Note Name from Database
        dbInUse = true;
        String name = myNoteDb.getName(rowId);
        dbInUse = false;

        noteViewToolbar.setTitle(name);
    }

    @Override
    void updatePasscodeInUI(long passcodeChangeRowId, char[] newPasscode) {

        // If a passcode change is completed while in NoteView for specific note, then update Activity Passcode Field
        if (passcodeChangeRowId==rowId) {

            passcode = newPasscode;
            passcodeChecked = true;

            // ****** The following was used during debugging.  Test Removing it. ******
            setGraphicEffectParameters();
            setRandomBodyEffectChars();
            // *************************************************************************
        }
    }

    // Controls Display at Activity Creation
    // If note is encrypted and passcode hasn't been checked, then prompts for passcode entry.  Otherwise, displays appropriate version of note body.
    private void displayNoteView() {

        // Lock Note Body & Save Button
        noteViewEditTextNoteBody.setFocusable(false);
        noteViewEditTextNoteBody.setClickable(false);
        noteViewButtonSave.setClickable(false);

        // Check if note is encrypted by checking if passcode hash exists
        dbInUse = true;
        String savedPasscodeHash = myNoteDb.getPasscodeHash(rowId);
        dbInUse = false;

        if (!savedPasscodeHash.equals("")) {

            // A Passcode is saved => Note is encrypted

            // If passcode doesn't exist or has not been checked, then prompt for passcode (first start, or restarting before passcode was entered or checked)
            if ( (passcode == null ) || !passcodeChecked ) {

                // Remove Autocorrect & Edit Text Lines for better graphic effect
                noteViewEditTextNoteBody.setRawInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                noteViewEditTextNoteBody.changeLineColor(getResources().getColor(R.color.colorBlack));

                // Set Edit Text to Random Note Body
                noteViewEditTextNoteBody.setText(new String(randomBodyEffectChars));

                // Setup Passcode Prompt Alert Dialog
                LayoutInflater layoutInflater = LayoutInflater.from(this);
                final View passcodePromptView = layoutInflater.inflate(R.layout.passcode_prompt, null);
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setView(passcodePromptView);
                final EditText passcodePromptEditTextPasscode = passcodePromptView.findViewById(R.id.passcodePromptEditTextPasscode);

                // Construct Alert Dialog Responses
                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int id) {

                                        // Close Keyboard
                                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                        imm.hideSoftInputFromWindow(passcodePromptView.getWindowToken(), 0);

                                        // Get Passocde Field by reading into Character array (for secure overwriting later)
                                        int inputPasscodeLength = passcodePromptEditTextPasscode.getText().length();
                                        char[] inputPasscode = new char[inputPasscodeLength];

                                        for (int i = 0; i < inputPasscodeLength; i++) {

                                            inputPasscode[i] = passcodePromptEditTextPasscode.getText().charAt(i);
                                        }

                                        // Overwrite Passcode EditText Field for security
                                        passcodePromptEditTextPasscode.setText("");

                                        // If passcode is not blank or greater than max size, then launch background threads to check passcode and decrypt, and perform graphic effect,
                                        // othersise return to Main Activity
                                        if ((inputPasscodeLength <= maxPasscodeSize) & (inputPasscodeLength > 0)) {

                                            // Reset Necessary Graphic Effect Arrays
                                            decryptedFinalBodyEffectChars = new char [currentBodyEffectCharSize];
                                            decryptedCarriageReturnPositions = new int[currentBodyDisplayLines];

                                            // Setup seperate thread to Check Passcode & Decrypt
                                            checkPasscodeAndDecryptThread = new CheckPasscodeAndDecryptThread(currentBodyDisplayCharsPerLine, currentBodyDisplayLines, inputPasscode);

                                            // Setup separate thread to Generate Basic Graphic Effect, await signal that note has been decrypted, and then generate final real change effect
                                            decryptingGraphicEffectThread = new DecryptingGraphicEffectThread(currentBodyDisplayCharsPerLine, currentBodyDisplayLines, randomBodyEffectChars);

                                            // Start Threads
                                            checkPasscodeAndDecryptThread.start();
                                            decryptingGraphicEffectThread.start();

                                        } else {

                                            // Passcode is blank or greater than max allowable passcode size
                                            Toast.makeText(getApplicationContext(), "Incorrect Passcode!", Toast.LENGTH_LONG).show();

                                            // Return to Main Activity while maintaining constant random note body in background
                                            Intent resultIntent = new Intent();
                                            resultIntent.putExtra("randombodyeffectchars", randomBodyEffectChars);
                                            resultIntent.putExtra("currentbodyeffectorientation", currentBodyEffectOrientation);
                                            setResult(RESULT_OK, resultIntent);

                                            finish();
                                        }
                                    }
                                })
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int id) {

                                        // Close Keyboard
                                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                        imm.hideSoftInputFromWindow(passcodePromptView.getWindowToken(), 0);

                                        // Overwrite Passcode EditText Field for security
                                        passcodePromptEditTextPasscode.setText("");

                                        // Return to Main Activity while maintaining constant random note body in background
                                        Intent resultIntent = new Intent();
                                        resultIntent.putExtra("randombodyeffectchars", randomBodyEffectChars);
                                        resultIntent.putExtra("currentbodyeffectorientation", currentBodyEffectOrientation);
                                        setResult(RESULT_OK, resultIntent);

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
            else {

                // Passcode has been entered & verified (Returning from saved state)

                // Set Note Body Colors to Normal
                noteViewEditTextNoteBody.setBackgroundColor(getResources().getColor(R.color.colorWhite));
                noteViewEditTextNoteBody.setTextColor(getResources().getColor(R.color.colorBlack));
                noteViewEditTextNoteBody.changeLineColor(getResources().getColor(R.color.colorGreen3));

                // If note has not finished decrypting, then decrypt, otherwise display already decrypted note body
                if (!noteDecrypted) {

                    // Start New Background Thread to Decrypt Note & Update UI
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

                            // Decrypt Note from Database
                            dbInUse = true;
                            byte[] decryptedBody = myNoteDb.decryptNote(rowId, passcode);
                            dbInUse = false;

                            // Update UI Variables & Notify Note Body Handler
                            recordBody = new String(decryptedBody);
                            noteDecrypted = true;

                            noteBodyHandler.sendEmptyMessage(SET_TEXT_DATABASE_RECORD);

                        }
                    }).start();
                }
                else {

                    // Passcode has been entered & verified & note has been decrypted

                    // If activity was destroyed during decrypting effect, then update edit text with decrypted note body from database, otherwise, update with note body saved
                    // from edittext before activity was destroyed
                    if (savedDuringDecryptingEffect) {

                        noteViewEditTextNoteBody.setText(recordBody);
                    }
                    else {

                        noteViewEditTextNoteBody.setText(savedInstanceBody);
                    }

                    // Unlock Note Body & Save Button
                    noteViewEditTextNoteBody.setFocusable(true);
                    noteViewEditTextNoteBody.setFocusableInTouchMode(true);
                    noteViewEditTextNoteBody.setClickable(true);
                    noteViewButtonSave.setClickable(true);
                }
            }
        }
        else {

            // No passcode is saved => Note is not encrypted

            passcode = null;

            // Set Note Body Colors to Normal
            noteViewEditTextNoteBody.setBackgroundColor(getResources().getColor(R.color.colorWhite));
            noteViewEditTextNoteBody.setTextColor(getResources().getColor(R.color.colorBlack));
            noteViewEditTextNoteBody.changeLineColor(getResources().getColor(R.color.colorGreen3));

            // New Thread to Get Note from Database & Update UI
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

                    // Get Note from Database
                    dbInUse = true;
                    byte[] decryptedBody = myNoteDb.decryptNote(rowId, passcode);
                    dbInUse = false;

                    // Update UI Variables & Note Body Handler
                    recordBody = new String(decryptedBody);

                    // If returning from saved state & a saved Instance Body exists, then load body saved in state, otherwise load body saved in database
                    if (returningFromSavedState & savedInstanceBody != null) {

                        noteBodyHandler.sendEmptyMessage(SET_TEXT_SAVED_INSTANCE);
                    }
                    else {

                        noteBodyHandler.sendEmptyMessage(SET_TEXT_DATABASE_RECORD);
                    }
                }
            }).start();
        }
    }

    // Updates Note in Database (encrypting if necessary) & triggers encrypting graphic effect.
    private void updateNote(final String bodyString) {

        // Lock Note Body & Save Button
        noteViewEditTextNoteBody.setFocusable(false);
        noteViewEditTextNoteBody.setClickable(false);
        noteViewButtonSave.setClickable(false);

        // Start Background Thread to Update Note in Database
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

                // Update Note in Database & Notify Database Update Handler
                dbInUse = true;
                myNoteDb.updateNote(rowId, bodyString.getBytes(), passcode);
                dbInUse = false;

                databaseUpdateHandler.sendEmptyMessage(NOTE_SAVED);

            }
        }).start();

        // If note is encrypted, then start encrypting graphic effect background thread, otherwise just return to main activity
        if (passcode != null) {

            // Note is encrypted

            // Turn off Autocorrect for better graphic effect
            noteViewEditTextNoteBody.setRawInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

            // Start Encrypting Graphic Effect Background Thread
            encryptingGraphicEffectThread = new EncryptingGraphicEffectThread(bodyString, noteViewEditTextNoteBody, currentBodyDisplayCharsPerLine, currentBodyDisplayLines, currentBodyEffectOrientation);
            encryptingGraphicEffectThread.start();

            Toast.makeText(getApplicationContext(), "Encrypting...", Toast.LENGTH_LONG).show();
        }
        else {

            // Note is not encrypted.  Return to Main.
            finish();
        }
    }

    // Background Thread for Checking Passcode & Decrypting Note
    private class CheckPasscodeAndDecryptThread extends Thread {

        private int bodyCharsPerLine;
        private int bodyEffectCharSize;
        private char[] testPasscode;

        CheckPasscodeAndDecryptThread(int bodyDisplayCharsPerLine, int bodyDisplayLines, char[] inputPasscode) {

            bodyCharsPerLine = bodyDisplayCharsPerLine;
            bodyEffectCharSize = bodyDisplayLines * bodyDisplayCharsPerLine;
            testPasscode = inputPasscode;
        }

        public void run() {

            duringPasscodeCheck = true;

            // If Database is still in use by another thread, then wait
            while (dbInUse & !isInterrupted()) {

                // Delay
                try {

                    Thread.sleep(dbinUsePause);
                }
                catch (Exception e) {

                    //Log.d("A", "Exception to Thread Sleep Request");
                }
            }

            if (!isInterrupted()) {

                // Check Entered Passcode against saved database passcode
                dbInUse = true;
                int checkPasscodeResult = myNoteDb.checkPasscode(rowId, testPasscode);
                dbInUse = false;

                if (!isInterrupted()) {

                    // If passcode is incorrect, then check if Note is Locked, Notify Database Update Handler, & return to Main Activity
                    if (checkPasscodeResult == 0) {

                        // Passcodes don't match

                        databaseUpdateHandler.sendEmptyMessage(INCORRECT_PASSCODE);

                        // Check if Note is Locked
                        dbInUse = true;
                        boolean isNoteLocked = myNoteDb.isNoteLocked(rowId);
                        dbInUse = false;

                        if (isNoteLocked) {

                            databaseUpdateHandler.sendEmptyMessage(NOTE_LOCKED);
                        }

                        // Return to Main & pass random note body & screen orientation
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("randombodyeffectchars", randomBodyEffectChars);
                        resultIntent.putExtra("currentbodyeffectorientation", currentBodyEffectOrientation);
                        setResult(RESULT_OK, resultIntent);

                        duringPasscodeCheck = false;

                        finish();

                    } else if (checkPasscodeResult == 1) {

                        // Passcodes do match

                        passcodeChecked = true;
                        passcode = testPasscode;

                        decryptNoteBody();

                    } else if (checkPasscodeResult == 2) {

                        // No passcode is saved (Execution shouldn't reach this point)
                        //Log.d("A", "No Passcode is saved!");

                        duringPasscodeCheck = false;

                        finish();
                    }
                }
            }

            duringPasscodeCheck = false;
        }

        // Package accessible interrupt method
        void cancel() {

            interrupt();
        }

        // Thread Method for Decrypting Note Body
        private void decryptNoteBody() {

            // If Database is still in use by another thread, then wait
            while (dbInUse & !isInterrupted()) {

                // Delay
                try {

                    Thread.sleep(dbinUsePause);
                }
                catch (Exception e) {

                    //Log.d("A", "Exception to Thread Sleep Request");
                }
            }

            databaseUpdateHandler.sendEmptyMessage(DECRYPTING);

            duringDecryption = true;
            duringPasscodeCheck = false;

            if (!isInterrupted()) {

                // Decrypt Note Body from Database
                dbInUse = true;
                byte[] decryptedBody = myNoteDb.decryptNote(rowId, testPasscode);
                dbInUse = false;

                recordBody = new String(decryptedBody);

                noteDecrypted = true;

                if (!isInterrupted()) {

                    String decryptedBodyString = recordBody;
                    int decryptedBodyStringLength = decryptedBodyString.length();
                    int decryptedBodyPosition = 0;
                    int finalBodyEffectPosition = 0;
                    decryptedNumOfCarriageReturns = 0;

                    // Initialize Final Body Effect Character Array with Real Note Body
                    while (finalBodyEffectPosition < bodyEffectCharSize) {

                        char bodyChar;

                        // If we haven't reached the end of the real note, then use real note character, otherwise use carraige return which will be replaced below
                        if (decryptedBodyPosition < decryptedBodyStringLength) {

                            bodyChar = decryptedBodyString.charAt(decryptedBodyPosition);
                            decryptedBodyPosition++;
                        }
                        else {

                            bodyChar = (char) 10;
                        }

                        // If character is a carriage return (from real note or otherwise), replace with sequence of spaces with carriage return on end for
                        // better visual effect.  Else, if character position is at end of line, then it has gotten there without any carriage returns on this
                        // line.  Fix word wrap and end of note massive space character issues, and insert carriage return on end of line.
                        if ((int) bodyChar == 10) {

                            // Character is a carriage return

                            // Replace character with a space
                            decryptedFinalBodyEffectChars[finalBodyEffectPosition] = " ".charAt(0);
                            finalBodyEffectPosition++;

                            // Fill every position from where carriage return was unitl end of line with a space
                            while (finalBodyEffectPosition % bodyCharsPerLine != 0) {

                                decryptedFinalBodyEffectChars[finalBodyEffectPosition] = " ".charAt(0);
                                finalBodyEffectPosition++;
                            }

                            // Replace last space with carriage return & note position
                            decryptedFinalBodyEffectChars[finalBodyEffectPosition - 1] = bodyChar;

                            decryptedCarriageReturnPositions[decryptedNumOfCarriageReturns] = finalBodyEffectPosition - 1;
                            decryptedNumOfCarriageReturns++;
                        }
                        else if (((finalBodyEffectPosition + 1) % bodyCharsPerLine) == 0) {

                            // Reached the last character position without encountering a carriage return on this line

                            decryptedFinalBodyEffectChars[finalBodyEffectPosition] = bodyChar;

                            // Move backwards unitl a space is encountered indicating the beginning of a word, or beginning of line is reached
                            int i = 0;
                            while ((((int) decryptedFinalBodyEffectChars[finalBodyEffectPosition - i]) != 32) & (i != (bodyCharsPerLine - 1))) {

                                i++;
                            }

                            // If last character on line is a space, then replace with a carriage return, otherwise perform word wrapping operations
                            if (i == 0) {

                                // Last character on line is a space.  Replace with a carriage return & note position

                                decryptedFinalBodyEffectChars[finalBodyEffectPosition] = (char) 10;
                                decryptedCarriageReturnPositions[decryptedNumOfCarriageReturns] = finalBodyEffectPosition;

                                decryptedNumOfCarriageReturns++;
                                finalBodyEffectPosition++;
                            }
                            else if (i != (bodyCharsPerLine - 1)) {

                                // Space is found

                                int lastSpacePosition = finalBodyEffectPosition - i;

                                // Move Characters in positions between lastSpacePosition & End of Line to Beginning of Next Line, & replace with spaces & carraige return
                                for (int j = 1; j <= i; j++) {

                                    if ((finalBodyEffectPosition + j) < bodyEffectCharSize) {

                                        decryptedFinalBodyEffectChars[finalBodyEffectPosition + j] = decryptedFinalBodyEffectChars[lastSpacePosition + j];
                                    }

                                    // Replace with spaces unless final character, then replace with carriage return & note position
                                    if (j == i) {

                                        decryptedFinalBodyEffectChars[lastSpacePosition + j] = (char) 10;

                                        decryptedCarriageReturnPositions[decryptedNumOfCarriageReturns] = lastSpacePosition + j;
                                        decryptedNumOfCarriageReturns++;
                                    }
                                    else {

                                        decryptedFinalBodyEffectChars[lastSpacePosition + j] = " ".charAt(0);
                                    }
                                }

                                // Update BodyEffectPostion to after moved characters on new line
                                finalBodyEffectPosition += (i + 1);
                            }
                            else {

                                // Line has no spaces.  Move last character to next position and replace with carriage return & note position

                                char temp = decryptedFinalBodyEffectChars[finalBodyEffectPosition];

                                decryptedFinalBodyEffectChars[finalBodyEffectPosition] = (char) 10;
                                decryptedCarriageReturnPositions[decryptedNumOfCarriageReturns] = finalBodyEffectPosition;

                                decryptedNumOfCarriageReturns++;

                                if ((finalBodyEffectPosition + 1) < bodyEffectCharSize) {

                                    decryptedFinalBodyEffectChars[finalBodyEffectPosition + 1] = temp;
                                    finalBodyEffectPosition++;
                                }

                                finalBodyEffectPosition++;
                            }
                        } else {

                            // Character isn't a carriage return, and we're not at end of line, so just copy

                            decryptedFinalBodyEffectChars[finalBodyEffectPosition] = bodyChar;
                            finalBodyEffectPosition++;
                        }
                    }
                }
            }

            duringDecryption = false;
        }
    }

    // Background Thread for Performing Decrypting Graphic Effect
    private class DecryptingGraphicEffectThread extends Thread {

        private int bodyEffectCharSize;
        private int realChangeEffectIterations;
        private int randomPositionOrderMixupIterations;
        private char[] bodyEffectChars;
        private Random random;

        DecryptingGraphicEffectThread(int bodyDisplayCharsPerLine, int bodyDisplayLines, char[] initialBodyEffectChars) {

            bodyEffectCharSize = bodyDisplayLines * bodyDisplayCharsPerLine;
            realChangeEffectIterations = bodyEffectCharSize / charChangesPerIteration;
            randomPositionOrderMixupIterations = bodyEffectCharSize;
            bodyEffectChars = new char[bodyEffectCharSize];
            int initialBodyEffectCharsLength = initialBodyEffectChars.length;
            random = new Random();

            // Initialize Body Effect Character Array with passed intitial random body array (if sizes are different for some reason, fill in with randomness)
            if (initialBodyEffectCharsLength >= bodyEffectCharSize) {

                for (int i = 0; i < bodyEffectCharSize; i++) {

                    bodyEffectChars[i] = initialBodyEffectChars[i];
                }
            }
            else {

                for (int i = 0; i < initialBodyEffectCharsLength; i++) {

                    bodyEffectChars[i] = initialBodyEffectChars[i];
                }

                for (int i = initialBodyEffectCharsLength; i < bodyEffectCharSize; i++) {

                    bodyEffectChars[i] = permissibleCharactersString.charAt(random.nextInt(permissibleCharactersString.length()));
                }
            }
        }

        public void run() {

            duringDecryptingGraphicalEffect = true;

            // Pre-Basic Effect Pause
            try {

                Thread.sleep(preBasicEffectPause);
            }
            catch (Exception e) {

                //Log.d("A", "Exception to Thread Sleep Request");
            }

            int effectPosition;
            int i = 0;

            // Perform a minimum number of Basic Graphic Effect Iterations and continue until note has been decrypted by separate thread
            while (i < minBasicGraphicEffectIterations || !noteDecrypted || duringDecryption) {

                if (!isInterrupted()) {

                    for (int j = 0; j < charChangesPerIteration; j++) {

                        if (!isInterrupted()) {

                            // Get a random array position which isn't a carriage return
                            do {

                                // Generate random position
                                effectPosition = random.nextInt(bodyEffectCharSize);

                            } while ((int) bodyEffectChars[effectPosition] == 10);

                            // Replace random position in charcter holder array with random character from permissible character string
                            bodyEffectChars[effectPosition] = permissibleCharactersString.charAt(random.nextInt(permissibleCharactersString.length()));
                        }
                        else {

                            break;
                        }
                    }

                    if (!isInterrupted()) {

                        if (noteViewEditTextNoteBody != null) {

                            // Update Note Body in UI Thread
                            noteViewEditTextNoteBody.post(new Runnable() {

                                public void run() {

                                    noteViewEditTextNoteBody.setText(new String(bodyEffectChars)); // Convert to string here may be secuity vulnerability
                                }
                            });
                        }

                        // Graphic Effect Iteration Pause
                        try {

                            Thread.sleep(graphicEffectIterationPause);
                        }
                        catch (Exception e) {

                            //Log.d("A", "Exception to Thread Sleep Request");
                        }
                    }
                    else {

                        break;
                    }
                }
                else {

                    break;
                }

                i++;
            }

            // If note is decrypted then perfrom final "real change" effect converting randomness to real note body
            if (noteDecrypted & !duringDecryption & !isInterrupted()) {

                realChangeDecryptingGraphicEffect();
            }

            // Unlock Note Body & Save Button
            if (noteViewEditTextNoteBody != null) {

                noteViewEditTextNoteBody.post(new Runnable() {

                    public void run() {

                        noteViewEditTextNoteBody.setFocusable(true);
                        noteViewEditTextNoteBody.setFocusableInTouchMode(true);
                        noteViewEditTextNoteBody.setClickable(true);
                    }
                });
            }

            if (noteViewButtonSave != null) {

                noteViewButtonSave.post(new Runnable() {

                    public void run() {

                        noteViewButtonSave.setClickable(true);
                    }
                });
            }

            duringDecryptingGraphicalEffect = false;
        }

        // Package accessible thread interrupt method
        void cancel() {

            interrupt();
        }

        // Thread Method for performing final "real change" graphic effect converting randomness to real note body
        private void realChangeDecryptingGraphicEffect() {

            String decryptedBodyString = recordBody;
            Random random = new Random();
            int effectPosition;

            // Initialize Random Position Order Array
            int[] randomPositionOrder = new int[bodyEffectCharSize];
            for (int i = 0; i < bodyEffectCharSize; i++) {

                randomPositionOrder[i] = i;
            }

            int mixupValueHolder;
            int mixupReferenceA;
            int mixupReferenceB;

            // Place carriage return positions at beginning of array, so they're the first to be replaced for better graphical effect
            for (int i = 0; i < decryptedNumOfCarriageReturns; i++) {

                mixupValueHolder = randomPositionOrder[i];
                randomPositionOrder[i] = decryptedCarriageReturnPositions[i];
                randomPositionOrder[decryptedCarriageReturnPositions[i]] = mixupValueHolder;
            }

            // Mixup up Random Position Order Array
            for (int i = 0; i < randomPositionOrderMixupIterations; i ++) {

                if (!isInterrupted()) {

                    // Get two random array positions that aren't carriage returns
                    do {

                        mixupReferenceA = random.nextInt(bodyEffectCharSize);
                    }
                    while (mixupReferenceA < decryptedNumOfCarriageReturns);

                    do {

                        mixupReferenceB = random.nextInt(bodyEffectCharSize);
                    }
                    while (mixupReferenceB < decryptedNumOfCarriageReturns);

                    // Swap Array positions
                    mixupValueHolder = randomPositionOrder[mixupReferenceA];
                    randomPositionOrder[mixupReferenceA] = randomPositionOrder[mixupReferenceB];
                    randomPositionOrder[mixupReferenceB] = mixupValueHolder;
                }
                else {

                    break;
                }
            }

            // Final Effect Iterations to match random text to real decrypted note body
            for (int realChangeCounter = 0; realChangeCounter <= realChangeEffectIterations; realChangeCounter++) {

                if (!isInterrupted()) {

                    // If not last iteration, then update body effect char array, if Last iteration, get real decrypted note body String & load into character array
                    if (realChangeCounter != realChangeEffectIterations) {

                        for (int j = 0; j < charChangesPerIteration; j++) {

                            if (!isInterrupted()) {

                                // Get random position (which hasn't been used) to transform random body to real decrypted body
                                effectPosition = randomPositionOrder[(realChangeCounter * charChangesPerIteration) + j];

                                // Replace random position in charcter holder array with character from real decrypted body array
                                bodyEffectChars[effectPosition] = decryptedFinalBodyEffectChars[effectPosition];
                            }
                            else {

                                break;
                            }
                        }
                    } else {

                        // Last Iteration

                        int decryptedBodyStringLength = decryptedBodyString.length();
                        bodyEffectChars = new char[decryptedBodyStringLength];

                        // Final Update of Body Effect Character Array with real message
                        for (int j = 0; j < decryptedBodyStringLength; j++) {

                            if (!isInterrupted()) {

                                bodyEffectChars[j] = decryptedBodyString.charAt(j);
                            }
                            else {

                                break;
                            }
                        }
                    }

                    if (!isInterrupted()) {

                        // Update Note Body in UI
                        if (noteViewEditTextNoteBody!=null) {

                            noteViewEditTextNoteBody.post(new Runnable() {

                                public void run() {

                                    noteViewEditTextNoteBody.setText(new String(bodyEffectChars)); // Convert to string here may be secuity vulnerability
                                }
                            });
                        }

                        // Real Change Effect Iteration Pause
                        try {

                            Thread.sleep(realChangeDecryptingEffectIterationPause);
                        }
                        catch (Exception e) {

                            //Log.d("A", "Exception to Thread Sleep Request");
                        }
                    }
                    else {

                        break;
                    }

                    // If last iteration, change background & Text color to normal
                    if (realChangeCounter == realChangeEffectIterations) {

                        // Final Pause
                        try {

                            Thread.sleep(colorChangePause);
                        }
                        catch (Exception e) {

                            //Log.d("A", "Exception to Thread Sleep Request");
                        }

                        // Change Note Body EditText Colors & Renable Autocorrect in UI Thread
                        if (noteViewEditTextNoteBody!=null) {

                            noteViewEditTextNoteBody.post(new Runnable() {

                                public void run() {

                                    noteViewEditTextNoteBody.setBackgroundColor(getResources().getColor(R.color.colorWhite));
                                    noteViewEditTextNoteBody.setTextColor(getResources().getColor(R.color.colorBlack));

                                    // Add Lines by changing color
                                    noteViewEditTextNoteBody.changeLineColor(getResources().getColor(R.color.colorGreen3));

                                    // Renable Autocorrect by Resetting input type
                                    // ***** NOT WORKING ON EMULATORS!  WORKING ON REAL DEVICES SO FAR! *****
                                    // Absence of TYPE_TEXT_FLAG_NO_SUGGESTIONS is not renabling autocorrect, nor is inclusion of auto correct or autocomplete flag
                                    noteViewEditTextNoteBody.setRawInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                                }
                            });
                        }
                    }
                }
                else {

                    break;
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Check if note is encrypted and display appropriate toolbar menu options
        dbInUse = true;
        String passcodeHash = myNoteDb.getPasscodeHash(rowId);
        dbInUse = false;

        if (passcodeHash.equals("")) {

            getMenuInflater().inflate(R.menu.plain_menu_note_view, menu);
        }
        else {

            getMenuInflater().inflate(R.menu.encrypted_menu_note_view, menu);
        }

        return true;
    }

    // Handle Toolbar Options Menu Items Selected while in Note View Activity
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // If during graphic effect or database is in use, do nothing.
        if (!duringEncryptingGraphicalEffect & !duringDecryptingGraphicalEffect & !dbInUse) {

            // Close Keyboard when a toolbar menu item is selected
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getWindow().getCurrentFocus().getWindowToken(), 0);

            int id = item.getItemId();

            if (id == R.id.action_delete) {

                // Delete Note

                // Setup Delete Note Confirmation Prompt Alert Dialog
                LayoutInflater layoutInflater = LayoutInflater.from(this);
                View deletePromptView = layoutInflater.inflate(R.layout.delete_prompt, null);
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setView(deletePromptView);

                // Construct Alert Dialog Responses
                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int id) {

                                        // Start seperate thread to Delete Note in Database
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

                                                // Delete Note in Database & Notify Database Update Handler
                                                dbInUse = true;
                                                myNoteDb.deleteRow(rowId);
                                                dbInUse = false;

                                                databaseUpdateHandler.sendEmptyMessage(NOTE_DELETED);

                                            }
                                        }).start();

                                        // Note open in Note View has been deleted, so return to Main Activity
                                        finish();
                                    }
                                })
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int id) {

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

                return true;
            }
            else if (id == R.id.action_rename) {

                // Rename Note

                renamePrompt(rowId);

                return true;
            }
            else if (id == R.id.action_change_passcode) {

                // Add / Change Note Passcode

                changePasscodePrompt(rowId, passcode);

                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }
}