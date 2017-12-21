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
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends BaseActivity {

    Toolbar mainToolbar;
    FloatingActionButton mainFloatingActionButtonCompose;
    TextView mainTextViewEncryptedBackground;
    ListView  mainListViewNoteList;
    TextView mainTextViewNoNotesMessage;

    private static Handler mainListHandler;
    private static SimpleCursorAdapter mainListCursorAdapter;
    private static MatrixCursor displayNoteListCursor;
    private static MainListLoaderThread mainListLoaderThread;
    private Context listContext;

    private static boolean displayRefreshInProgress = false;

    // Main List Handler Codes
    private static final int INITIALIZE_LIST = 0;
    private static final int UPDATE_LIST = 1;
    private static final int NO_NOTES = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Open Note Database
        openNoteDB();

        // Setup UI
        setContentView(R.layout.activity_main);
        mainTextViewEncryptedBackground = findViewById(R.id.mainTextViewEncryptedBackground);
        mainListViewNoteList = findViewById(R.id.mainNoteListView);
        mainTextViewNoNotesMessage = findViewById(R.id.mainTextViewNoNotesMessage);
        mainFloatingActionButtonCompose = findViewById(R.id.mainFloatingActionButtonCompose);

        mainToolbar = findViewById(R.id.mainToolbar);
        setSupportActionBar(mainToolbar);
        mainToolbar.setTitle("Notes (0)");
        
        registerMainListHandler();
        setupMainListeners();
    }

    // Setup Handler to Update Main Note List
    private void registerMainListHandler() {

        mainListHandler = new Handler(Looper.getMainLooper()) {

            int numOfnotes;
            int noteCounter;

            @Override
            public void handleMessage(Message message) {

                Bundle bundle;

                switch(message.what) {

                    case INITIALIZE_LIST:

                        // Initializes a New List by initializing cursor & cursor adapter, & linking cursor, cursor adapter, & list view

                        String[] fromFieldNames = new String[]{NoteDBAdapter.KEY_NAME, "date", "time", "encryptedMarkerImageUri"};
                        int[] toViewIDs = new int[]{R.id.mainTextViewName, R.id.mainTextViewDate, R.id.mainTextViewTime, R.id.mainImageViewEncryptedMarker};
                        displayNoteListCursor = new MatrixCursor(new String[]{NoteDBAdapter.KEY_ROWID, NoteDBAdapter.KEY_NAME, "date", "time", "encryptedMarkerImageUri"});

                        mainListCursorAdapter =
                                new SimpleCursorAdapter(
                                        listContext,               // Context
                                        R.layout.main_note_row,    // Row layout template
                                        displayNoteListCursor,     // cursor (set of DB records to map)
                                        fromFieldNames,            // DB Column names
                                        toViewIDs,                 // View IDs to put information in
                                        0                    // Automatic Requery (0 = false, 1 = true)
                                );

                        mainListViewNoteList.setAdapter(mainListCursorAdapter);

                        // Extract Number of Records from List Loader Thread Message & Update Toolbar Title
                        bundle = message.getData();
                        numOfnotes = bundle.getInt("numofrecords");
                        noteCounter = 0;

                        mainToolbar.setTitle("Notes (" + numOfnotes + ")");

                        break;

                    case UPDATE_LIST:

                        // Update List with New Note supplied from List Loader Background Thread

                        // If No Notes Text View is set to visibile due to there previously being zero notes, then set to invisible
                        if (mainTextViewNoNotesMessage.getVisibility()==View.VISIBLE) {

                            mainTextViewNoNotesMessage.setVisibility(View.INVISIBLE);
                        }

                        // Extract New Note Row Informtion from List Loader Thread Message
                        bundle = message.getData();
                        long rowId = bundle.getLong("rowid");
                        String name = bundle.getString("name");
                        String date = bundle.getString("date");
                        String time = bundle.getString("time");
                        String marker = bundle.getString("marker");

                        // Add New Row to Cursor, & notify data set is changed to automatically update list view
                        displayNoteListCursor.addRow(new Object[]{rowId, name, date, time, marker});
                        mainListCursorAdapter.notifyDataSetChanged();

                        noteCounter++;

                        // Close Cursor if last note
                        if ((displayNoteListCursor != null) & (noteCounter==numOfnotes)) {

                            displayNoteListCursor.close();
                        }

                        break;

                    case NO_NOTES:

                        // No notes to load.  Display "No Notes" View
                        mainTextViewNoNotesMessage.setVisibility(View.VISIBLE);

                        break;
                }
            }
        };
    }

    private void setupMainListeners() {

        // Compose Action Button Click (Start Compose Activity)
        mainFloatingActionButtonCompose.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                // If database is in use, do nothing
                if (!dbInUse) {

                    Intent intent = new Intent(MainActivity.this,ComposeActivity.class);
                    startActivityForResult(intent, NOTE_ACTIVITY_REQUEST);
                }
            }
        });

        // Note Click Listener to Open Individual Note (Start Note View Activity)
        mainListViewNoteList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View viewClicked,
                                    int position, final long idInDB) {

                // If database is in use, do nothing
                if (!dbInUse) {

                    dbInUse = true;
                    boolean isNoteLocked = myNoteDb.isNoteLocked(idInDB);
                    dbInUse = false;

                    // Check if note is locked, and open if not
                    if (!isNoteLocked) {

                        // Note is not Locked
                        // Start Note View Activity & pass database rowId for appropriate record & random body string

                        Intent intent = new Intent(MainActivity.this, NoteViewActivity.class);
                        intent.putExtra("rowId", idInDB);
                        intent.putExtra("randombodyeffectchars", randomBodyEffectChars);
                        intent.putExtra("currentbodyeffectorientation", currentBodyEffectOrientation);
                        startActivityForResult(intent, NOTE_ACTIVITY_REQUEST);
                    }
                    else {

                        // Note is Locked
                        Toast.makeText(getApplicationContext(), "Note is locked!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        // Note Long Click Listener to bring up individual note menu
        mainListViewNoteList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, final long idInDB) {

                // If database is in use, do nothing
                if (!dbInUse) {

                    dbInUse = true;
                    boolean isNoteLocked = myNoteDb.isNoteLocked(idInDB);
                    dbInUse = false;

                    // Check if note is locked, and inflate menu if not locked
                    if (!isNoteLocked) {

                        // Note is not Locked.

                        // Setup Note Action Menu
                        PopupMenu popup = new PopupMenu(view.getContext(), view);
                        MenuInflater inflater = popup.getMenuInflater();

                        dbInUse = true;

                        // Display different menu options depending on whether note is encrypted or not
                        if (myNoteDb.getPasscodeHash(idInDB).equals("")) {

                            inflater.inflate(R.menu.plain_note_list_menu, popup.getMenu());
                        }
                        else {

                            inflater.inflate(R.menu.encrypted_note_list_menu, popup.getMenu());
                        }

                        dbInUse = false;

                        // Show Menu & Setup Click Listener
                        popup.show();
                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                            @Override
                            public boolean onMenuItemClick(MenuItem item) {

                                noteListMenuAction(item.getItemId(), idInDB);

                                return true;
                            }
                        });
                    }
                    else {

                        // Note is Locked.
                        Toast.makeText(getApplicationContext(), "Note is locked!", Toast.LENGTH_LONG).show();
                    }
                }

                return true;
            }
        });
    }

    // If returning to Main After Encryption Background or Effect & Orientations match, then use passed random body, otherwise reset graphic effect parameters & generate new random body
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == NOTE_ACTIVITY_REQUEST) {

            if (resultCode == RESULT_OK) {

                int passedBodyEffectOrientation = data.getIntExtra("currentbodyeffectorientation", 0);
                char[] passedBodyEffectChars = data.getCharArrayExtra("randombodyeffectchars");

                if ( (passedBodyEffectOrientation == currentBodyEffectOrientation) & passedBodyEffectChars != null) {

                    randomBodyEffectChars = passedBodyEffectChars;
                }
                else {

                    setGraphicEffectParameters();
                    setRandomBodyEffectChars();
                }
            }
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        // Issue if returning to Main Activity if screen orientation has occurred during encrypting graphical effect,
        // bodyeffectchars somehow getting set to null, if so, reset graphical parameters before setting body text
        // ***** Test removing this code *****
        if (randomBodyEffectChars !=null) {

            mainTextViewEncryptedBackground.setText(new String(randomBodyEffectChars));
        }
        else {

            setGraphicEffectParameters();
            setRandomBodyEffectChars();

            mainTextViewEncryptedBackground.setText(new String(randomBodyEffectChars));
        }
        // ***************************************

        refreshDisplay();
    }

    @Override
    protected void onPause() {

        super.onPause();

        // If Main Activity is paused, then interrupt Main List Loading Background Thread
        if (mainListLoaderThread!=null) {

            mainListLoaderThread.cancel();
        }
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        // If Main Activity is closed, then interrupt Main List Loading Background Thread
        if (mainListLoaderThread!=null) {

            mainListLoaderThread.cancel();
        }

        // Close Note Database
        //closeNoteDB();
    }

    // Method to Populate Note List View
    void refreshDisplay() {

        // If a previous Main List Background Loading thread has not finished, then interrupt before starting new one
        if (mainListLoaderThread!=null) {

            mainListLoaderThread.cancel();
        }

        // Start New Main List Loading Background thread
        listContext = this;
        mainListLoaderThread = new MainListLoaderThread();
        mainListLoaderThread.start();
    }

    // Main List Loading Thread for populating main note list from database
    private class MainListLoaderThread extends Thread {

        private int refreshInProgressPause = 50;

        public void run() {

            // If another main list loading thread is in progress, then wait until it finished
            while (displayRefreshInProgress) {

                // Delay
                try {

                    Thread.sleep(refreshInProgressPause);
                }
                catch (Exception e) {

                    //Log.d("A", "Exception to Thread Sleep Request");
                }
            }

            if (!isInterrupted()) {

                displayRefreshInProgress = true;

                // Get Note Info from Database
                // ***** This is the only place in the application where we do not wait until another thread has finsihed using the database in order to access it *****
                // ***** If another thread is using the database, it will trigger a new main list refresh when finished ****
                dbInUse = true;
                Cursor noteListCursor = myNoteDb.getAllNotesForMainList();
                dbInUse = false;

                int numOfRecords = noteListCursor.getCount();

                // Construct Main List Initialize UI Handler Message
                Bundle initialBundle = new Bundle();
                initialBundle.putInt("numofrecords", numOfRecords);
                Message initialMessage = new Message();
                initialMessage.setData(initialBundle);
                initialMessage.what = INITIALIZE_LIST;

                // Notify Main List Handler to Initialize List
                mainListHandler.sendMessage(initialMessage);

                // If records are present, then sort and display note list, otherwise display "No Notes" Message
                if (numOfRecords > 0) {

                    long[] dateOrder = new long[numOfRecords];
                    int[] rowIdOrder = new int[numOfRecords];

                    // Get rowId's & corresponding dates
                    if (noteListCursor.moveToFirst()) {

                        int i = 0;

                        do {
                            dateOrder[i] = noteListCursor.getLong(NoteDBAdapter.COL_DATE);
                            rowIdOrder[i] = noteListCursor.getInt(NoteDBAdapter.COL_ROWID);

                            i++;
                        }
                        while (noteListCursor.moveToNext());

                        noteListCursor.close();
                    }

                    if (!isInterrupted()) {

                        // Sort rowId's by date with larger date numbers (more recent) first
                        boolean swapped;

                        do {

                            swapped = false;

                            for (int i = 0; i < (numOfRecords - 1); i++) {

                                if (dateOrder[i] < dateOrder[i + 1]) {

                                    long dateHolder = dateOrder[i];
                                    dateOrder[i] = dateOrder[i + 1];
                                    dateOrder[i + 1] = dateHolder;

                                    int rowIdHolder = rowIdOrder[i];
                                    rowIdOrder[i] = rowIdOrder[i + 1];
                                    rowIdOrder[i + 1] = rowIdHolder;

                                    swapped = true;
                                }
                            }
                        }
                        while (swapped);

                        // Iterate thru notes and notify main list UI Handler to update list
                        dbInUse = true;

                        for (int i = 0; i < numOfRecords; i++) {

                            if (!isInterrupted()) {

                                // Get correct note based on date sorted order
                                Cursor singleNoteCursor = myNoteDb.getRow(rowIdOrder[i]);

                                if (singleNoteCursor.moveToFirst()) {

                                    String savedPasscodeHash = singleNoteCursor.getString(NoteDBAdapter.COL_PASSCODE_HASH);
                                    String encryptedMarkerImageUri = "";

                                    // If note is encrytped, then add encrypted marker image URI
                                    if (!savedPasscodeHash.equals("")) {

                                        Uri imageLockUri = Uri.parse("android.resource://net.leonardlabs.locker/drawable/lock");
                                        encryptedMarkerImageUri = imageLockUri.toString();
                                    }

                                    // Format Date
                                    Date dateObject = new Date(dateOrder[i]);
                                    String date = new SimpleDateFormat("M/d/yy").format(dateObject);
                                    String time = new SimpleDateFormat("h:mm a").format(dateObject);

                                    // Construct Main List UI Handler Message
                                    Bundle bundle = new Bundle();
                                    bundle.putLong("rowid", rowIdOrder[i]);
                                    bundle.putString("name", singleNoteCursor.getString(NoteDBAdapter.COL_NAME));
                                    bundle.putString("date", date);
                                    bundle.putString("time", time);
                                    bundle.putString("marker", encryptedMarkerImageUri);
                                    Message message = new Message();
                                    message.setData(bundle);
                                    message.what = UPDATE_LIST;

                                    // Notify Main List UI Handler to update list with supplied note
                                    if (!isInterrupted()) {

                                        mainListHandler.sendMessage(message);
                                    }

                                    singleNoteCursor.close();
                                }
                            }
                        }

                        dbInUse = false;
                    }
                }
                else {

                    // No Notes Found.  Notify Main List UI Handler to display "No Notes."

                    mainListHandler.sendEmptyMessage(NO_NOTES);
                }

                displayRefreshInProgress = false;
            }
        }

        // Package accessible thread interrupt method
        void cancel() {

            interrupt();
        }
    }

    // Handle Note List Long-Click Listener Menu Actions
    private void noteListMenuAction(final int itemId, final long rowId) {

        // Declaration of Passcode Checking Background Thread for actions on encrypted notes
        class CheckPasscodeThread extends Thread {

            private char[] testPasscode;

            CheckPasscodeThread(char[] inputPasscode) {

                testPasscode = inputPasscode;
            }

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

                // Check Entered Passcode against saved database passcode
                dbInUse = true;
                int checkPasscodeResult = myNoteDb.checkPasscode(rowId, testPasscode);
                dbInUse = false;

                // If passcode is incorrect, don't do anything else
                if (checkPasscodeResult == 0) {

                    // Passcodes don't match

                    databaseUpdateHandler.sendEmptyMessage(INCORRECT_PASSCODE);
                }
                else if (checkPasscodeResult == 1) {

                    // Passcodes do match

                    // Perform selected action
                    Bundle bundle;
                    Message message;

                    switch (itemId) {

                        case R.id.action_delete:

                            // Delete Note

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

                            break;

                        case R.id.action_rename:

                            // Rename Note

                            // Construct Database Update Handler Message & Notify Handler to Initiate Note Rename Prompt
                            bundle = new Bundle();
                            bundle.putLong("rowid", rowId);
                            message = new Message();
                            message.setData(bundle);
                            message.what = RENAME_PROMPT;

                            databaseUpdateHandler.sendMessage(message);

                            break;

                        case R.id.action_change_passcode:

                            // Add / Change Note Passcode

                            // Construct Database Update Handler Message & Notify Handler to Initiate Change Passcode Prompt
                            bundle = new Bundle();
                            bundle.putLong("rowid", rowId);
                            bundle.putCharArray("passcode", testPasscode);
                            message = new Message();
                            message.setData(bundle);
                            message.what = CHANGE_PASSOCDE_PROMPT;

                            databaseUpdateHandler.sendMessage(message);

                            break;
                    }
                }
                else if (checkPasscodeResult == 2) {

                    // No passcode is saved (Execution shouldn't reach this point)
                    //Log.d("A", "No Passcode is saved!");
                }
            }
        }

        // Check if a passcode hash is saved for note (i.e. note is encrypted)
        dbInUse = true;
        String passcodeHash = myNoteDb.getPasscodeHash(rowId);
        dbInUse = false;

        if (!passcodeHash.equals("")) {

            // A Passcode is saved => Note is encrypted
            // Initiate Prompt for passcode entry

            // Setup Passcode Prompt Alert Dialog
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            final View passcodePromptView = layoutInflater.inflate(R.layout.passcode_prompt, null);
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setView(passcodePromptView);
            final EditText passcodePromptEditTextPasscode = passcodePromptView.findViewById(R.id.passcodePromptEditTextPasscode);
            final TextView passcodePromptTextViewPrompt = passcodePromptView.findViewById(R.id.passcodePromptTextViewPrompt);

            // Set Prompt Text depending on action
            if (itemId == R.id.action_delete) {

                passcodePromptTextViewPrompt.setText("Enter Passphrase to Confirm:");
            }
            else if (itemId == R.id.action_change_passcode) {

                passcodePromptTextViewPrompt.setText("Enter Current Passphrase:");
            }

            // Construct Alert Dialog Responses
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog,int id) {

                                    // Close Keyboard
                                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                    imm.hideSoftInputFromWindow(passcodePromptView.getWindowToken(), 0);

                                    // Get Passocde Field by reading into Character array (for secure overwriting later.)
                                    int passcodeLength = passcodePromptEditTextPasscode.getText().length();
                                    char[] passcode = new char[passcodeLength];

                                    for (int i = 0; i < passcodeLength; i++) {

                                        passcode[i] = passcodePromptEditTextPasscode.getText().charAt(i);
                                    }

                                    // Overwrite Passcode EditText Field for security
                                    passcodePromptEditTextPasscode.setText("");

                                    // If passcode was entered & less than max size, then start background thread to check passcode, & direct further action
                                    if ((passcodeLength <= maxPasscodeSize) & (passcodeLength > 0)) {

                                       // Start Passcode Checking Background Thread
                                       CheckPasscodeThread checkPasscodeThread = new CheckPasscodeThread(passcode);
                                       checkPasscodeThread.start();
                                    }
                                    else {

                                        // Passcode wasn't entered or is greater than max allowable passcode size
                                        Toast.makeText(getApplicationContext(), "Incorrect Passcode!", Toast.LENGTH_LONG).show();
                                    }
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog,int id) {

                                    // Close Keyboard
                                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                    imm.hideSoftInputFromWindow(passcodePromptView.getWindowToken(), 0);

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

            // No passcode is saved => Key File is not encrypted

            // Perform selected action
            switch (itemId) {

                case R.id.action_delete:

                    // Delete Note

                    // Setup Delete Note Confirmation Prompt Alert Dialog
                    LayoutInflater layoutInflater = LayoutInflater.from(this);
                    final View deletePromptView = layoutInflater.inflate(R.layout.delete_prompt, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                    alertDialogBuilder.setView(deletePromptView);

                    // Construct Alert Dialog Responses
                    alertDialogBuilder
                            .setCancelable(false)
                            .setPositiveButton("OK",
                                    new DialogInterface.OnClickListener() {

                                        public void onClick(DialogInterface dialog,int id) {

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
                                        }
                                    })
                            .setNegativeButton("Cancel",
                                    new DialogInterface.OnClickListener() {

                                        public void onClick(DialogInterface dialog,int id) {

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

                    break;

                case R.id.action_rename:

                    // Rename Note

                    renamePrompt(rowId);

                    break;

                case R.id.action_change_passcode:

                    // Add Passcode (convert unencrypted note to encrypted)

                    changePasscodePrompt(rowId, null);

                    break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle Action Bar Menu Item Clicks

        int id = item.getItemId();

        // If Database is in use, do nothing.
        if (!dbInUse) {

            if (id == R.id.action_delete_all) {

                // Delete All Unencrypted Notes

                // Setup Delete All Confirmation Prompt Alert Dialog
                LayoutInflater layoutInflater = LayoutInflater.from(this);
                View deletePromptView = layoutInflater.inflate(R.layout.delete_prompt, null);
                TextView deletePromptTextViewPrompt = deletePromptView.findViewById(R.id.deletePromptTextViewPrompt);
                deletePromptTextViewPrompt.setText("Delete All Unencrypted?");
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setView(deletePromptView);

                // Construct Alert Dialog Responses
                alertDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int id) {

                                        // Start seperate thread to Delete All Unencrypted Notes in database
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

                                                // Delete All Unencrypted Notes in database & notify Database Update Handler
                                                dbInUse = true;
                                                myNoteDb.deleteAllUnencrypted();
                                                dbInUse = false;

                                                databaseUpdateHandler.sendEmptyMessage(ALL_UNENCRYPTED_NOTES_DELETED);
                                            }
                                        }).start();
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
        }

        return super.onOptionsItemSelected(item);
    }
}
