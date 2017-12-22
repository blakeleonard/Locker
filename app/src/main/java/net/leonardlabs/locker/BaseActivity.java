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
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Random;

public class BaseActivity extends AppCompatActivity {

    // Constant Graphic Effect Parameters
    static final int minBasicGraphicEffectIterations = 60;
    static final int charChangesPerIteration = 15;
    static final int graphicEffectIterationPause = 20;                      // All Pauses in ms
    static final int realChangeEncryptingEffectIterationPause = 30;
    static final int realChangeDecryptingEffectIterationPause = 30;
    static final int preBasicEffectPause = 100;
    static final int colorChangePause = 300;
    static final int finalPause = 300;
    private static final int padding = 6;       // Left & Right EditText Padding in dp

    static final String permissibleCharactersString = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ123456789@#^&*_=";

    // Misc Application Parameters
    static final int maxLines = 200;                // Maximum Note Size in Lines
    static final int maxPasscodeSize = 32;          // Max Size of Passcode Character Array
    static final int dbinUsePause = 50;

    // Dynamic Graphic Effect Parameters & Variables
    static int portraitMaxNoteSize = 0;
    static int maxNoteSize;
    static int currentBodyDisplayLines;
    static int currentBodyDisplayCharsPerLine;
    static int currentBodyEffectCharSize;
    static int currentRealChangeEffectIterations;
    static int currentRandomPositionOrderMixupIterations;
    static int currentBodyEffectOrientation;
    static char[] randomBodyEffectChars;

    // Database Update Handler Codes
    static final int NOTE_SAVED = 0;
    static final int NOTE_DELETED = 1;
    static final int ALL_UNENCRYPTED_NOTES_DELETED = 2;
    static final int NAME_CHANGED = 3;
    static final int NAME_EXISTS = 4;
    static final int PASSCODE_ADDED = 5;
    static final int PASSCODE_CHANGED = 6;
    static final int PASSCODES_DONT_MATCH = 7;
    static final int PASSCODE_TOO_LONG = 8;
    static final int INCORRECT_PASSCODE = 9;
    static final int NOTE_LOCKED = 10;
    static final int ENCRYPTING = 11;
    static final int DECRYPTING = 12;
    static final int RENAME_PROMPT = 13;
    static final int CHANGE_PASSOCDE_PROMPT = 14;

    // Program Flow Booleans
    static boolean duringEncryptingGraphicalEffect = false;
    static boolean duringDecryptingGraphicalEffect = false;
    static boolean duringDecryption = false;
    static boolean dbInUse = false;

    // Misc Global Variables
    static NoteDBAdapter myNoteDb;
    static Handler databaseUpdateHandler;
    EncryptingGraphicEffectThread encryptingGraphicEffectThread;
    static final int NOTE_ACTIVITY_REQUEST = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setGraphicEffectParameters();
        setRandomBodyEffectChars();
    }

    // Setup Dynamic Graphic Effect Parameters based on device screen dimensions & orientation
    void setGraphicEffectParameters() {

        int screenWidth = getResources().getConfiguration().screenWidthDp;
        int screenHeight = getResources().getConfiguration().screenHeightDp;
        currentBodyEffectOrientation = getResources().getConfiguration().orientation;

        // 20dp text size corresponds to divding by 22 for height, Two lines beyond display view for buffer
        currentBodyDisplayLines = ( (int) ( screenHeight / 22 + 0.5 ) ) + 2;

        if (currentBodyDisplayLines > maxLines) {

            currentBodyDisplayLines = maxLines;
        }

        // 20dp text size corresponds to dividing usable screen width (after subtracting padding) by 12. One character beyond display view for carriage returns
        currentBodyDisplayCharsPerLine = ( (int) ( ( ( screenWidth - ( 2 * padding ) ) / 12 ) + 0.5 ) ) + 1;

        currentBodyEffectCharSize = currentBodyDisplayLines * currentBodyDisplayCharsPerLine;

        currentRealChangeEffectIterations = currentBodyEffectCharSize / charChangesPerIteration;
        currentRandomPositionOrderMixupIterations = currentBodyEffectCharSize;

        // Determine Max Note Size based on Portrait Screen Width & Max Lines

        // ***** There's an issue if note is composed or edited without app ever being in portrait orientation, and then screen is re-oriented for notes near max size.  User will lose
        // characters between the difference in max note sizes based on small differences between width & height values in different orientations.  Will only be an issue for notes that
        // are near both max lines & max character size (i.e. Large number of full lines with no carriage returns).  Can eliminate these problems & max lines completely by trigerring
        // note body edit text to draw a new line past current drawn line when needed.  Could simplify to a single constant max note size.*****

        if (currentBodyEffectOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {

            maxNoteSize = maxLines * ( currentBodyDisplayCharsPerLine - 1 );

            portraitMaxNoteSize = maxNoteSize;
        }
        else {

            if (portraitMaxNoteSize == 0) {

                int potentialCharsPerLine = ( (int) ( ( ( screenHeight - ( 2 * padding ) ) / 12 ) + 0.5 ) ) + 1;

                maxNoteSize = maxLines * ( potentialCharsPerLine - 1 );
            }
            else {

                maxNoteSize = portraitMaxNoteSize;
            }
        }
    }

    // Construct Random Character Array for Graphic Effect Purposes
    void setRandomBodyEffectChars() {

        Random initialRandom = new Random();

        randomBodyEffectChars = new char[currentBodyEffectCharSize];

        // Setup Body Effect Character Array
        for (int i = 0; i < currentBodyEffectCharSize; i++) {

            // If character is end of line, insert carriage return, otherwise insert random character
            if ( ( i + 1 ) % currentBodyDisplayCharsPerLine != 0) {

                randomBodyEffectChars[i] = permissibleCharactersString.charAt(initialRandom.nextInt(permissibleCharactersString.length()));
            }
            else {

                randomBodyEffectChars[i] = (char) 10;
            }
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        registerDatabaseUpdateHandler();
    }

    // Setup Handler to Refresh Display & Perform Other Actions Related to Updating the Database
    private void registerDatabaseUpdateHandler() {

        databaseUpdateHandler = new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(Message message) {

                long rowId;
                char[] passcode;
                Bundle bundle;

                switch(message.what) {

                    case NOTE_SAVED:

                        Toast.makeText(getApplicationContext(), "Saved.", Toast.LENGTH_SHORT).show();
                        refreshDisplay();

                        break;

                    case NOTE_DELETED:

                        Toast.makeText(getApplicationContext(), "Note Deleted!", Toast.LENGTH_SHORT).show();
                        refreshDisplay();

                        break;

                    case ALL_UNENCRYPTED_NOTES_DELETED:

                        Toast.makeText(getApplicationContext(), "Unencrypted Notes Deleted!", Toast.LENGTH_SHORT).show();
                        refreshDisplay();

                        break;

                    case NAME_CHANGED:

                        Toast.makeText(getApplicationContext(), "Name Changed.", Toast.LENGTH_SHORT).show();
                        refreshDisplay();

                        break;

                    case NAME_EXISTS:

                        Toast.makeText(getApplicationContext(), "Name Already Exists!", Toast.LENGTH_SHORT).show();

                        break;

                    case PASSCODE_ADDED:

                        bundle = message.getData();
                        rowId = bundle.getLong("rowid");
                        passcode = bundle.getCharArray("newpasscode");
                        updatePasscodeInUI(rowId, passcode);

                        Toast.makeText(getApplicationContext(), "Passphrase Added.", Toast.LENGTH_SHORT).show();
                        refreshDisplay();

                        break;

                    case PASSCODE_CHANGED:

                        bundle = message.getData();
                        rowId = bundle.getLong("rowid");
                        passcode = bundle.getCharArray("newpasscode");
                        updatePasscodeInUI(rowId, passcode);

                        Toast.makeText(getApplicationContext(), "Passphrase Changed.", Toast.LENGTH_SHORT).show();

                        break;

                    case PASSCODES_DONT_MATCH:

                        Toast.makeText(getApplicationContext(), "Passphrases Don't Match!", Toast.LENGTH_SHORT).show();

                        break;

                    case PASSCODE_TOO_LONG:

                        Toast.makeText(getApplicationContext(), "Passphrase Too Long!", Toast.LENGTH_SHORT).show();

                        break;

                    case INCORRECT_PASSCODE:

                        Toast.makeText(getApplicationContext(), "Incorrect Passphrase!", Toast.LENGTH_SHORT).show();

                        break;

                    case NOTE_LOCKED:

                        Toast.makeText(getApplicationContext(), "Note is locked for 5 min!", Toast.LENGTH_SHORT).show();

                        break;

                    case ENCRYPTING:

                        Toast.makeText(getApplicationContext(), "Encrypting...", Toast.LENGTH_LONG).show();

                        break;

                    case DECRYPTING:

                        Toast.makeText(getApplicationContext(), "Decrypting...", Toast.LENGTH_LONG).show();

                        break;

                    case RENAME_PROMPT:

                        bundle = message.getData();
                        rowId = bundle.getLong("rowid");

                        renamePrompt(rowId);

                        break;

                    case CHANGE_PASSOCDE_PROMPT:

                        bundle = message.getData();
                        rowId = bundle.getLong("rowid");
                        passcode = bundle.getCharArray("passcode");

                        changePasscodePrompt(rowId, passcode);

                        break;
                }
            }
        };
    }

    @Override
    protected void onPause() {

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }

    // Open Database
    void openNoteDB() {

        if (myNoteDb==null) {

            myNoteDb = new NoteDBAdapter(this);
            myNoteDb.open();
        }
    }

    // Close Database
    static void closeNoteDB() {

        if (myNoteDb!=null) {

            myNoteDb.close();
            myNoteDb = null;
        }
    }

    // Child Method in Note View Activity is used to update the passcode after a change (for possible later note modification)
    void updatePasscodeInUI(long rowId, char[] newPasscode) {

    }

    // Child Methods are used by Database Update Handler to refresh displays
    void refreshDisplay() {

    }

    // Displays Rename Alert Dialog & launches new thread to change note name in database
    void renamePrompt(final long rowId) {

        // Setup Alert Dialog
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        final View renamePromptView = layoutInflater.inflate(R.layout.rename_prompt, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(renamePromptView);

        final EditText renamePromptEditTextNewName = renamePromptView.findViewById(R.id.renamePromptEditTextNewName);

        // Construct Alert Dialog Responses
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog,int id) {

                                // Close Keyboard
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(renamePromptView.getWindowToken(), 0);

                                // If a new note name has been entered, then start background thread to update database
                                final String newName = renamePromptEditTextNewName.getText().toString();

                                if (!newName.equals("")) {

                                    new Thread(new Runnable() {

                                        public void run() {

                                            // If Database is still in use by another thread, then wait
                                            while (dbInUse) {

                                                // Pause
                                                try {

                                                    Thread.sleep(dbinUsePause);
                                                }
                                                catch (Exception e) {

                                                    //Log.d("A", "Exception to Thread Sleep Request");
                                                }
                                            }

                                            dbInUse = true;

                                            //Check to make sure that note name isn't already in use by another note
                                            Cursor cursor = myNoteDb.getAllNames();
                                            boolean noteNameAlreadyExistsFlag = false;

                                            if (cursor.moveToFirst()) {

                                                do {

                                                    String recordNoteName = cursor.getString(NoteDBAdapter.COL_NAME);
                                                    int recordRowId = cursor.getInt(NoteDBAdapter.COL_ROWID);

                                                    // Only trip flag if note name is equal to name of already existing record that isn't the current record
                                                    if ( (newName.equals(recordNoteName)) & (rowId != recordRowId) ) {

                                                        noteNameAlreadyExistsFlag = true;

                                                        break;
                                                    }
                                                }
                                                while (cursor.moveToNext());

                                                cursor.close();
                                            }

                                            // If note name is not in use, then change note name in database & inform Database Update Handler
                                            if (!noteNameAlreadyExistsFlag) {

                                                myNoteDb.changeName(rowId, newName);

                                                dbInUse = false;

                                                databaseUpdateHandler.sendEmptyMessage(NAME_CHANGED);
                                            }
                                            else {

                                                // Name already exists

                                                dbInUse = false;

                                                databaseUpdateHandler.sendEmptyMessage(NAME_EXISTS);
                                            }

                                        }
                                    }).start();
                                }
                                else {

                                    // Nothing was entered.

                                    Toast.makeText(getApplicationContext(), "Nothing was Entered!", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog,int id) {

                                // Close Keyboard
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(renamePromptView.getWindowToken(), 0);

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

    // Displays Alert Dialog for adding or changing passcode & starts background thread to change passcode in database
    void changePasscodePrompt(final long rowId, final char[] incomingOldPasscode) {

        // Setup background thread
        class ChangePasscodeThread extends Thread {

            private char[] oldPasscode;
            private char[] newPasscode;

            ChangePasscodeThread(char[] inputOldPasscode, char[] inputNewPasscode) {

                oldPasscode = inputOldPasscode;
                newPasscode = inputNewPasscode;
            }

            public void run() {

                // If Database is still in use by another thread, then wait
                while (dbInUse) {

                    // Pause
                    try {

                        Thread.sleep(dbinUsePause);
                    }
                    catch (Exception e) {

                        //Log.d("A", "Exception to Thread Sleep Request");
                    }
                }

                dbInUse = true;

                // Change Passcode in database
                myNoteDb.changePasscode(rowId, oldPasscode, newPasscode);

                // Inform Database Update Handler
                Bundle bundle = new Bundle();
                bundle.putLong("rowid", rowId);
                bundle.putCharArray("newpasscode", newPasscode);
                Message message = new Message();
                message.setData(bundle);

                dbInUse = false;

                if (oldPasscode==null) {

                    message.what = PASSCODE_ADDED;
                    databaseUpdateHandler.sendMessage(message);
                }
                else {

                    message.what = PASSCODE_CHANGED;
                    databaseUpdateHandler.sendMessage(message);
                }
            }
        }

        //Setup Alert Dialog
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        final View changePasscodePromptView = layoutInflater.inflate(R.layout.change_passcode_prompt, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(changePasscodePromptView);

        final EditText changePasscodePromptEditTextNewPasscode = changePasscodePromptView.findViewById(R.id.changePasscodePromptEditTextNewPasscode);
        final EditText changePasscodePromptEditTextConfirmPasscode = changePasscodePromptView.findViewById(R.id.changePasscodePromptEditTextConfirmPasscode);

        // Construct Alert Dialog Responses
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog,int id) {

                                // Close Keyboard
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(changePasscodePromptView.getWindowToken(), 0);

                                // Get Passcodes by reading into Character Arrays (for safe overwriting later)
                                int newPasscodeLength = changePasscodePromptEditTextNewPasscode.getText().length();
                                char[] newPasscode = new char[newPasscodeLength];

                                for (int i = 0; i < newPasscodeLength; i++) {

                                    newPasscode[i] = changePasscodePromptEditTextNewPasscode.getText().charAt(i);
                                }

                                int confirmPasscodeLength = changePasscodePromptEditTextConfirmPasscode.getText().length();
                                char[] confirmPasscode = new char[confirmPasscodeLength];

                                for (int i = 0; i < confirmPasscodeLength; i++) {

                                    confirmPasscode[i] = changePasscodePromptEditTextConfirmPasscode.getText().charAt(i);
                                }

                                // Overwrite EditText Fields
                                changePasscodePromptEditTextNewPasscode.setText("");
                                changePasscodePromptEditTextConfirmPasscode.setText("");

                                // If a passcode was entered, isn't greater than maximum allowed size, and is successfully confirmed, then start background thread
                                if ( ( newPasscodeLength > 0 ) || ( confirmPasscodeLength > 0 ) ) {

                                    if (newPasscode.length <= maxPasscodeSize) {

                                        if ( Arrays.equals(newPasscode, confirmPasscode) ) {

                                            // Start background thread to change passcode in database
                                            ChangePasscodeThread changePasscodeThread = new ChangePasscodeThread(incomingOldPasscode, newPasscode);
                                            changePasscodeThread.start();
                                        }
                                        else {

                                            // New Passcode & Confirm passcode fields do not match
                                            Toast.makeText(getApplicationContext(), "Passphrases Don't Match!", Toast.LENGTH_LONG).show();
                                        }
                                    }
                                    else {

                                        // Passcode Exceeds Max Size
                                        Toast.makeText(getApplicationContext(), "Passphrase Exceeds Max Size!", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                else {

                                    // Nothing was entered.
                                    Toast.makeText(getApplicationContext(), "Passphrase is Blank!", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog,int id) {

                                // Close Keyboard
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(changePasscodePromptView.getWindowToken(), 0);

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

    // Background thread for peforming the Encrypting Graphic Effect when creating or updating an encrypted note
    class EncryptingGraphicEffectThread extends Thread {

        private String body;
        private LinedEditText editTextNoteBody;

        private int bodyCharsPerLine;
        private int bodyEffectCharSize;
        private int realChangeEffectIterations;
        private int randomPositionOrderMixupIterations;
        private int bodyEffectOrientation;
        private char[] bodyEffectChars;
        private char[] encryptedFinalBodyEffectChars;

        EncryptingGraphicEffectThread(String passedBody, LinedEditText passedEditText, int bodyDisplayCharsPerLine, int bodyDisplayLines, int bodyDisplayEffectOrientation) {

            body = passedBody;
            editTextNoteBody = passedEditText;

            bodyCharsPerLine = bodyDisplayCharsPerLine;
            bodyEffectCharSize = bodyDisplayLines * bodyDisplayCharsPerLine;
            realChangeEffectIterations = bodyEffectCharSize / charChangesPerIteration;
            randomPositionOrderMixupIterations = bodyEffectCharSize;
            bodyEffectOrientation = bodyDisplayEffectOrientation;
            bodyEffectChars = new char[bodyEffectCharSize];
            encryptedFinalBodyEffectChars = new char[bodyEffectCharSize];
        }

        public void run() {

            duringEncryptingGraphicalEffect = true;

            Random initialRandom = new Random();
            int decryptedBodyPosition = 0;
            int bodyEffectPosition = 0;
            int decryptedBodyStringLength = body.length();

            // Initialize Body Effect Character Array with Real Note Body
            while ( (bodyEffectPosition < bodyEffectCharSize ) & !isInterrupted()) {

                char bodyChar;

                // If we haven't reached the end of the real note, then use real note character, otherwise use carraige return which will be replaced below
                if (decryptedBodyPosition < decryptedBodyStringLength) {

                    bodyChar = body.charAt(decryptedBodyPosition);
                    decryptedBodyPosition++;
                }
                else {

                    bodyChar = (char) 10;
                }

                // If character is a carriage return (from real note or otherwise), replace with sequence of spaces with carriage return on end for
                // better visual effect.  Else, if character position is at end of line, then it has gotten there without any carriage returns on this
                // line.  Fix word wrap and end of note massive space character issues, and insert carriage return on end of line.
                if ((int) bodyChar == 10) {

                    // Character is a carriage retrun

                    // Replace character with space
                    bodyEffectChars[bodyEffectPosition] = " ".charAt(0);
                    bodyEffectPosition++;

                    // Fill every position from where carriage return was unitl end of line with a space
                    while (bodyEffectPosition % bodyCharsPerLine != 0) {

                        bodyEffectChars[bodyEffectPosition] = " ".charAt(0);
                        bodyEffectPosition++;
                    }

                    // Replace last space with carriage return
                    bodyEffectChars[bodyEffectPosition-1] = bodyChar;
                }
                else if (((bodyEffectPosition+1) % bodyCharsPerLine ) == 0 ){

                    // Reached the last character position without encountering a carriage return on this line

                    bodyEffectChars[bodyEffectPosition] = bodyChar;

                    // Move backwards unitl a space is encountered indicating the beginning of a word, or beginning of line is reached
                    int i = 0;
                    while ((((int) bodyEffectChars[bodyEffectPosition - i]) != 32) & (i != (bodyCharsPerLine - 1))) {

                        i++;
                    }

                    // If last character on line is a space, then replace with a carriage return, otherwise perform word wrapping operations
                    if (i==0) {

                        bodyEffectChars[bodyEffectPosition] = (char) 10;
                        bodyEffectPosition ++;
                    }
                    else if (i != (bodyCharsPerLine - 1)) {

                        // Space is found

                        int lastSpacePosition = bodyEffectPosition - i;

                        // Move Characters in positions between lastSpacePosition & End of Line to Beginning of Next Line, & replace with spaces & carriage return
                        for (int j = 1; j <= i; j++) {

                            if ( ( bodyEffectPosition + j ) < bodyEffectCharSize ) {

                                bodyEffectChars[bodyEffectPosition + j] = bodyEffectChars[lastSpacePosition + j];
                            }

                            // Replace with spaces unless final character, then replace with carriage return
                            if (j == i) {

                                bodyEffectChars[lastSpacePosition + j] = (char) 10;
                            }
                            else {

                                bodyEffectChars[lastSpacePosition + j] = " ".charAt(0);
                            }
                        }

                        // Update Body Effect Postion to after moved characters on new line
                        bodyEffectPosition += (i + 1);
                    }
                    else {

                        // Line has no spaces.  Move last character to next position and replace with carriage return

                        char temp = bodyEffectChars[bodyEffectPosition];
                        bodyEffectChars[bodyEffectPosition] = (char) 10;

                        if ( ( bodyEffectPosition + 1 ) < bodyEffectCharSize ) {

                            bodyEffectChars[bodyEffectPosition + 1] = temp;
                            bodyEffectPosition ++;
                        }

                        bodyEffectPosition++;
                    }
                }
                else {

                    // Character isn't a carriage return, and we're not at end of line, so just copy character

                    bodyEffectChars[bodyEffectPosition] = bodyChar;
                    bodyEffectPosition++;
                }
            }

            // Get Random Version of Body For Final Effect Character Display
            if (!isInterrupted()) {

                for (int i = 0; i < bodyEffectCharSize; i++) {

                    // Add random character from permissible character string to random body
                    encryptedFinalBodyEffectChars[i] = permissibleCharactersString.charAt(initialRandom.nextInt(permissibleCharactersString.length()));
                }
            }

            if (!isInterrupted()) {

                // Change Colors
                if (editTextNoteBody!=null) {

                    editTextNoteBody.post(new Runnable() {

                        public void run() {

                            editTextNoteBody.setBackgroundColor(getResources().getColor(R.color.colorBlack));
                            editTextNoteBody.setTextColor(getResources().getColor(R.color.colorGreen2));

                            // Remove Lines by changing color to background
                            editTextNoteBody.changeLineColor(getResources().getColor(R.color.colorBlack));
                        }
                    });
                }

                // Color Change Pause
                try {

                    Thread.sleep(colorChangePause);
                }
                catch(Exception e) {

                    //Log.d("A", "Exception to Thread Sleep Request");
                }
            }

            if (!isInterrupted()) {

                // Setup Random Position Order Array
                Random random = new Random();
                int[] randomPositionOrder = new int[bodyEffectCharSize];

                for (int i = 0; i < bodyEffectCharSize; i++) {

                    randomPositionOrder[i] = i;
                }

                int mixupValueHolder;
                int mixupReferenceA;
                int mixupReferenceB;

                // Mixup up Random Position Order Array
                for (int i = 0; i < randomPositionOrderMixupIterations; i++) {

                    mixupReferenceA = random.nextInt(bodyEffectCharSize);
                    mixupReferenceB = random.nextInt(bodyEffectCharSize);

                    // Swap Array positions
                    mixupValueHolder = randomPositionOrder[mixupReferenceA];
                    randomPositionOrder[mixupReferenceA] = randomPositionOrder[mixupReferenceB];
                    randomPositionOrder[mixupReferenceB] = mixupValueHolder;
                }

                int effectPosition;

                // Real Change Effect Iterations to match encrypted message to random body
                for (int realChangeCounter = 0; realChangeCounter <= realChangeEffectIterations; realChangeCounter++) {

                    if (!isInterrupted()) {

                        // If not last iteration, then update body effect char array, if Last iteration, then update entire array
                        if (realChangeCounter != realChangeEffectIterations) {

                            for (int j = 0; j < charChangesPerIteration; j++) {

                                if (!isInterrupted()) {

                                    // Get random position (which hasn't been used) to transform real decrypted body to random body
                                    effectPosition = randomPositionOrder[(realChangeCounter * charChangesPerIteration) + j];

                                    // Only replace character if not a carriage return at the end of the line
                                    if ((int) bodyEffectChars[effectPosition] != 10) {

                                        // Replace random position in charcter holder array with character from random body array
                                        bodyEffectChars[effectPosition] = encryptedFinalBodyEffectChars[effectPosition];
                                    }
                                }
                                else {

                                    break;
                                }
                            }

                            // Pause
                            try {

                                Thread.sleep(realChangeEncryptingEffectIterationPause);
                            }
                            catch (Exception e) {

                                //Log.d("A", "Exception to Thread Sleep Request");
                            }
                        }
                        else {

                            // Final Update of Body Effect Character Array for better graphical effect
                            for (int j = 0; j < bodyEffectCharSize; j++) {

                                if (!isInterrupted()) {

                                    // Only replace character if not a carriage return at the end of the line
                                    if ((int) bodyEffectChars[j] != 10) {

                                        bodyEffectChars[j] = encryptedFinalBodyEffectChars[j];
                                    }
                                }
                                else {

                                    break;
                                }
                            }
                        }

                        // Update Note Body in UI Thread
                        if (editTextNoteBody!=null) {

                            editTextNoteBody.post(new Runnable() {

                                public void run() {

                                    editTextNoteBody.setText(new String(bodyEffectChars)); // Convert to string here may be secuity vulnerability
                                }
                            });
                        }
                    }
                    else {

                        break;
                    }
                }

                // Basic Graphic Effect Iterations after edit text has been filled with randomness
                for (int i = 0; i < minBasicGraphicEffectIterations; i++) {

                    if (!isInterrupted()) {

                        for (int j = 0; j < charChangesPerIteration; j++) {

                            if (!isInterrupted()) {

                                // Get random position which isn't a carriage return
                                do {

                                    effectPosition = random.nextInt(bodyEffectCharSize);

                                } while ((int) bodyEffectChars[effectPosition] == 10);

                                // Replace random position in charcter holder array with random character from permissible character string
                                bodyEffectChars[effectPosition] = permissibleCharactersString.charAt(random.nextInt(permissibleCharactersString.length()));
                            }
                            else {

                                break;
                            }
                        }

                        // Update note body in UI thread
                        if (editTextNoteBody!=null) {

                            editTextNoteBody.post(new Runnable() {

                                public void run() {

                                    editTextNoteBody.setText(new String(bodyEffectChars)); // Convert to string here may be secuity vulnerability
                                }
                            });
                        }

                        // Pause
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

                // Pause
                try {

                    Thread.sleep(finalPause);
                }
                catch (Exception e) {

                    //Log.d("A", "Exception to Thread Sleep Request");
                }
            }

            // Setup Result Intent for returning to Main Activity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("randombodyeffectchars", bodyEffectChars);
            resultIntent.putExtra("currentbodyeffectorientation", bodyEffectOrientation);
            setResult(RESULT_OK, resultIntent);

            duringEncryptingGraphicalEffect = false;

            // Close Compose or NoteView Activity & Return to Main Activity
            finish();
        }

        // Package accessible interrupt method
        void cancel() {

            interrupt();
        }
    }

    // Custom Edit Text Class for drawing lines on notepad
    public static class LinedEditText extends AppCompatEditText {

        private Paint mPaint = new Paint();

        public LinedEditText(Context context) {

            super(context);
            initPaint();
        }

        public LinedEditText(Context context, AttributeSet attrs) {

            super(context, attrs);
            initPaint();
        }

        public LinedEditText(Context context, AttributeSet attrs, int defStyle) {

            super(context, attrs, defStyle);
            initPaint();
        }

        private void initPaint() {

            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(getResources().getColor(R.color.colorGreen3));
        }

        @Override
        protected void onDraw(Canvas canvas) {

            int left = getLeft();
            int right = getRight();
            int paddingTop = getPaddingTop();
            int paddingLeft = getPaddingLeft();
            int paddingRight = getPaddingRight();
            int lineHeight = getLineHeight();

            // Calculate number of lines to fill view
            //int paddingBottom = getPaddingBottom();
            //int height = getHeight();
            //int count = (height-paddingTop-paddingBottom) / lineHeight;

            // Draw Max Lines
            int count = maxLines;

            for (int i = 0; i < count; i++) {

                int baseline = lineHeight * (i+1) + paddingTop;
                canvas.drawLine(left+paddingLeft, baseline, right-paddingRight, baseline, mPaint);
            }

            super.onDraw(canvas);
        }

        protected void changeLineColor(int colorID) {

            mPaint.setColor(colorID);
        }
    }
}
