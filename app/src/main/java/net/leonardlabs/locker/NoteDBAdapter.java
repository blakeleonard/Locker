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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

class NoteDBAdapter {

    // DB info
    private static final String DATABASE_NAME = "NoteDb";
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NOTE_TABLE = "noteTable";

    // Encryption Parameters
    private static String passcodeHashAlgorithm;
    private static String passcodeKeyExtensionAlgorithm;
    private static String encryptionType;
    private static String keyType;
    private static int keyLength;                                           // Key Length in Bits
    private static int iterationCount;                                     // Iterations for Passcode Hashing & Passcode Key Extension Algorithms

    // Encryption Scheme Codes
    private static final int BELOW_API_23 = 0;
    private static final int API_23_TO_25 = 1;
    private static final int API_26 = 2;
    private static int currentScheme;

    // DB Fields
    static final String KEY_ROWID = "_id";
    static final int COL_ROWID = 0;

    static final String KEY_NAME = "name";
    private static final String KEY_DATE = "date";
    private static final String KEY_BODY = "body";
    private static final String KEY_SCHEME = "scheme";
    private static final String KEY_SALT = "salt";
    private static final String KEY_IV = "iv";
    private static final String KEY_PASSCODE_HASH = "passcodehash";
    private static final String KEY_PASSCODE_SALT = "passcodesalt";
    private static final String KEY_BAD_PASSCODE = "badpasscode";

    static final int COL_NAME = 1;
    static final int COL_DATE = 2;
    private static final int COL_BODY = 3;
    private static final int COL_SCHEME = 4;
    private static final int COL_SALT = 5;
    private static final int COL_IV = 6;
    static final int COL_PASSCODE_HASH = 7;
    private static final int COL_PASSCODE_SALT = 8;
    private static final int COL_BAD_PASSCODE = 9;

    private static final String[] ALL_NOTETABLE_KEYS = new String[] {KEY_ROWID, KEY_NAME, KEY_DATE, KEY_BODY, KEY_SCHEME, KEY_SALT, KEY_IV, KEY_PASSCODE_HASH, KEY_PASSCODE_SALT, KEY_BAD_PASSCODE};

    private static final String[] NAME_KEYS = new String[] {KEY_ROWID, KEY_NAME};
    private static final String[] MAIN_LIST_KEYS = new String[] {KEY_ROWID, KEY_NAME, KEY_DATE, KEY_PASSCODE_HASH};

    //DB Table Creation Strings
    private static final String DATABASE_CREATE_NOTE_TABLE_SQL =
            "create table " + DATABASE_NOTE_TABLE
                    + " (" + KEY_ROWID + " integer primary key autoincrement, "

                    + KEY_NAME + " string not null, "
                    + KEY_DATE + " integer not null, "
                    + KEY_BODY + " string not null, "
                    + KEY_SCHEME + " integer not null, "
                    + KEY_SALT + " string not null, "
                    + KEY_IV + " string not null, "
                    + KEY_PASSCODE_HASH + " string not null, "
                    + KEY_PASSCODE_SALT + " string not null, "
                    + KEY_BAD_PASSCODE + " integer not null "
                    + ");";

    // Context of application which uses DB
    private final Context context;

    private NoteDBAdapter.NoteDatabaseHelper myNoteDBHelper;
    private SQLiteDatabase noteDb;

    NoteDBAdapter(Context ctx) {

        this.context = ctx;
        myNoteDBHelper = new NoteDBAdapter.NoteDatabaseHelper(context);
    }

    // Open the database connection.
    NoteDBAdapter open() {

        // Determine API Level of Device & Set Current Encryption Scheme Code for new notes
        int apiLevel = Build.VERSION.SDK_INT;

        if (apiLevel < 23) {

            currentScheme = BELOW_API_23;
        }
        else if (apiLevel >= 23 & apiLevel < 26) {

            currentScheme = API_23_TO_25;
        }
        else {

            currentScheme = API_26;
        }

        noteDb = myNoteDBHelper.getWritableDatabase();
        return this;
    }

    // Close the database connection.
    void close() {
        myNoteDBHelper.close();
    }

    // Set Encryption Scheme Parameters Based on Scheme Code
    private void setSchemeParameters(int schemeCode) {

        switch(schemeCode) {

            case BELOW_API_23:

                passcodeHashAlgorithm = "PBKDF2WithHmacSHA1";
                passcodeKeyExtensionAlgorithm = "PBKDF2WithHmacSHA1";
                encryptionType = "AES/CBC/PKCS5Padding";
                keyType = "AES";
                keyLength = 256;
                iterationCount = 5000;

                break;

            case API_23_TO_25:

                passcodeHashAlgorithm = "PBKDF2WithHmacSHA1";
                passcodeKeyExtensionAlgorithm = "PBKDF2WithHmacSHA1";
                encryptionType = "AES/CBC/PKCS5Padding";
                keyType = "AES";
                keyLength = 256;
                iterationCount = 5000;

                break;

            case API_26:

                passcodeHashAlgorithm = "PBKDF2withHmacSHA256";
                passcodeKeyExtensionAlgorithm = "PBKDF2withHmacSHA256";
                encryptionType = "AES/CBC/PKCS5Padding";
                keyType = "AES";
                keyLength = 256;
                iterationCount = 5000;

                break;
        }
    }

    // Saves New Note in database & encrypts if necessary
    void insertNote(String name, byte[] body, char[] passcode) {

        long date = System.currentTimeMillis();
        String salt = "";
        String iv = "";
        String encodedPasscodeHash = "";
        String encodedPasscodeSaltHalf = "";

        // Setup Row Data
        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_NAME, name);
        initialValues.put(KEY_DATE, date);
        initialValues.put(KEY_SCHEME, currentScheme);
        initialValues.put(KEY_BAD_PASSCODE, 0);

        // If Note is encrypted, then generate new salt & iv & encrypt note body.  Also generate passcode salt & passcode hash.
        if (passcode!=null) {

            // Set Encryption Scheme Parameters
            setSchemeParameters(currentScheme);

            // Generate Salt & IV
            SecureRandom random = new SecureRandom();
            byte[] saltBytes = new byte[16];
            random.nextBytes(saltBytes);
            byte[] ivBytes = generateNewIv();
            salt = Base64.encodeToString(saltBytes, 0);
            iv = Base64.encodeToString(ivBytes, 0);

            // Generate Key from Passcode & encrypt note
            SecretKey key = generateKeyFromPasscode(passcode, salt.getBytes());
            byte[] encryptedBody = encryptBody(body, key, iv.getBytes());
            initialValues.put(KEY_BODY, new String(encryptedBody));

            // 1st half (16 bytes) of passcode salt is hardcoded, 2nd half is generated with PRNG and stored in Note DB & added at runtime (salt length should be same as output)
            byte[] passcodeSalt = new byte[]{'1', 'X', '2', 'h', '^', '+', 'o', 'x', '|', ')', '%', '8', '=', 'r', 'd', 'R', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x'};

            // Create 2nd half of passcode salt
            byte[] passcodeSaltHalf = new byte[16];
            random.nextBytes(passcodeSaltHalf);

            // Combine Passcode Salt Halves
            for (int i = 0; i < 16; i++) {

                passcodeSalt[16 + i] = passcodeSaltHalf[i];
            }

            // Hash Passcode for saving
            try {

                // Generate Passcode Hash
                SecretKeyFactory hashKeyFactory = SecretKeyFactory.getInstance(passcodeHashAlgorithm);
                KeySpec hashKeySpec = new PBEKeySpec(passcode, passcodeSalt, iterationCount, keyLength);
                byte[] hashBytes = hashKeyFactory.generateSecret(hashKeySpec).getEncoded();
                encodedPasscodeHash = Base64.encodeToString(hashBytes, 0);
            }
            catch (NoSuchAlgorithmException noSuchAlgorithm) {

                //Log.d("A", "No Such Alogrithm Exception!  Passcode hash not generated!");

            }
            catch (InvalidKeySpecException invalidKeySpec) {

                //Log.d("A", "Invalid Key Spec Exception!  Passcode hash not generated!");
            }

            encodedPasscodeSaltHalf = Base64.encodeToString(passcodeSaltHalf, 0);
        }
        else {

            initialValues.put(KEY_BODY, new String(body));
        }

        initialValues.put(KEY_SALT, salt);
        initialValues.put(KEY_IV, iv);
        initialValues.put(KEY_PASSCODE_HASH, encodedPasscodeHash);
        initialValues.put(KEY_PASSCODE_SALT, encodedPasscodeSaltHalf);

        // Insert Note into Database
        noteDb.insert(DATABASE_NOTE_TABLE, null, initialValues);
    }

    // Updates Note in database after editing & encrypts if necessary
    void updateNote(long rowId, byte[] newBody, char[] passcode) {

        long date = System.currentTimeMillis();

        // Get appropriate record
        Cursor cursor = getRow(rowId);

        if (cursor!=null) {

            String where = KEY_ROWID + "=" + cursor.getInt(COL_ROWID);
            int scheme = cursor.getInt(COL_SCHEME);
            String salt = "";
            String iv = "";
            String encodedPasscodeHash = "";
            String encodedPasscodeSaltHalf = "";
            byte [] passcodeSaltHalf;

            ContentValues updateValues = new ContentValues();

            // If passcode is supplied (Note is intended to be encrypted), then encrypt note body
            if (passcode!=null) {

                // Set Encryption Scheme Parameters
                setSchemeParameters(scheme);

                String savedPasscodeHash = cursor.getString(COL_PASSCODE_HASH);

                // If a Passcode Hash is saved, then note was previously encrypted, & can use saved iv, salt, & passcode salt, otherwise generate new ones
                if (!savedPasscodeHash.equals("")) {

                    // Get Saved Salt & IV
                    iv = cursor.getString(COL_IV);
                    salt = cursor.getString(COL_SALT);
                    encodedPasscodeSaltHalf = cursor.getString(COL_PASSCODE_SALT);
                    passcodeSaltHalf = Base64.decode(encodedPasscodeSaltHalf, 0);
                }
                else {

                    // Generate Salt & IV
                    SecureRandom random = new SecureRandom();
                    byte[] saltBytes = new byte[16];
                    random.nextBytes(saltBytes);
                    byte[] ivBytes = generateNewIv();
                    salt = Base64.encodeToString(saltBytes, 0);
                    iv = Base64.encodeToString(ivBytes, 0);

                    // Create passcode salt half
                    byte[] newPasscodeSaltHalf = new byte[16];
                    random.nextBytes(newPasscodeSaltHalf);
                    encodedPasscodeSaltHalf = Base64.encodeToString(newPasscodeSaltHalf, 0);
                    passcodeSaltHalf = newPasscodeSaltHalf;
                }

                // Generate Key from passcode & encrypt note
                SecretKey key = generateKeyFromPasscode(passcode, salt.getBytes());
                byte[] encryptedBody = encryptBody(newBody, key, iv.getBytes());

                // Hash Passcode for saving
                try {

                    // 1st half (16 bytes) of passcode salt is hardcoded, 2nd half is generated with PRNG and stored in Note DB & added at runtime (salt length should be same as output)
                    byte[] passcodeSalt = new byte[]{'1', 'X', '2', 'h', '^', '+', 'o', 'x', '|', ')', '%', '8', '=', 'r', 'd', 'R', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x'};

                    // Combine Passcode Salt Halves
                    for (int i = 0; i < 16; i++) {

                        passcodeSalt[16 + i] = passcodeSaltHalf[i];
                    }

                    // Generate Passcode Hash
                    SecretKeyFactory hashKeyFactory = SecretKeyFactory.getInstance(passcodeHashAlgorithm);
                    KeySpec hashKeySpec = new PBEKeySpec(passcode, passcodeSalt, iterationCount, keyLength);
                    byte[] hashBytes = hashKeyFactory.generateSecret(hashKeySpec).getEncoded();
                    encodedPasscodeHash = Base64.encodeToString(hashBytes, 0);
                }
                catch (NoSuchAlgorithmException noSuchAlgorithm) {

                    //Log.d("A", "No Such Alogrithm Exception!  Passcode hash not generated!");

                }
                catch (InvalidKeySpecException invalidKeySpec) {

                    //Log.d("A", "Invalid Key Spec Exception!  Passcode hash not generated!");
                }

                updateValues.put(KEY_BODY, new String(encryptedBody));
            }
            else {

                updateValues.put(KEY_BODY, new String(newBody));
            }

            // Setup Row Data
            updateValues.put(KEY_NAME, cursor.getString(COL_NAME));
            updateValues.put(KEY_DATE, date);
            updateValues.put(KEY_SCHEME, scheme);
            updateValues.put(KEY_SALT, salt);
            updateValues.put(KEY_IV, iv);
            updateValues.put(KEY_PASSCODE_HASH, encodedPasscodeHash);
            updateValues.put(KEY_PASSCODE_SALT, encodedPasscodeSaltHalf);
            updateValues.put(KEY_BAD_PASSCODE, 0);

            // Update Note in database
            noteDb.update(DATABASE_NOTE_TABLE, updateValues, where, null);

            cursor.close();
        }
    }

    // Returns Note Body for Note View Activity (Decrypting if necessary)
    byte[] decryptNote(long rowId, char[] passcode) {

        byte[] body = null;

        Cursor noteCursor = getRow(rowId);

        if (noteCursor != null) {

            byte[] savedBody = noteCursor.getString(COL_BODY).getBytes();

            // If note is encrypted, then decrypt, otherwise just return saved note body
            if (passcode!=null) {

                // Set Encryption Scheme Parameters
                setSchemeParameters(noteCursor.getInt(COL_SCHEME));

                byte[] iv = noteCursor.getString(COL_IV).getBytes();
                byte[] salt = noteCursor.getString(COL_SALT).getBytes();

                // Generate Key from passcode & decrypt note
                SecretKey key = generateKeyFromPasscode(passcode, salt);
                body = decryptBody(savedBody, key, iv);
            }
            else {

               body = savedBody;
            }

            noteCursor.close();
        }

        return body;
    }

    // Generates IV for encrypting Note Body
    private byte[] generateNewIv() {

        SecureRandom random = new SecureRandom();
        byte[] returnIvBytes = null;

        try {

            Cipher cipher = Cipher.getInstance(encryptionType);
            byte[] ivBytes = new byte[cipher.getBlockSize()];
            random.nextBytes(ivBytes);
            returnIvBytes = ivBytes;
        }
        catch (NoSuchAlgorithmException noSuchAlgorithm) {

            //Log.d("A", "No Such Alogrithm Exception!  Note not Encrypted!");
        }
        catch (NoSuchPaddingException noSuchPadding) {

            //Log.d("A", "No Such Padding Exception!  Note not Encrypted!");
        }

        return returnIvBytes;
    }

    // Encrypts and base-64 encodes Note Body byte array
    private byte[] encryptBody (byte[] bodyToEncrypt, SecretKey key, byte[] iv ) {

        byte[] encryptedBody = new byte[bodyToEncrypt.length];
        byte[] decodedIv = Base64.decode(iv, 0);

        try {

            Cipher cipher = Cipher.getInstance(encryptionType);
            IvParameterSpec ivParams = new IvParameterSpec(decodedIv);

            try {

                cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
                encryptedBody = cipher.doFinal(bodyToEncrypt);
            }
            catch (InvalidKeyException invalidKey) {

                //Log.d("A", "Invalid Key Exception!  Note Body not Encrypted!");
            }
            catch (InvalidAlgorithmParameterException invalidAlgorithmParameter) {

                //Log.d("A", "Invalid Algorithm Parameter Exception!  Note Body not Encrypted!");
            }
            catch (IllegalBlockSizeException illegalBlockSize) {

                //Log.d("A", "Illegal Block Size Exception!  Note Body not Encrypted!");
            }
            catch (BadPaddingException badPadding) {

                //Log.d("A", "Bad Padding Exception!  Note Body not Encrypted!");
            }
        }
        catch (NoSuchAlgorithmException noSuchAlgorithm) {

            //Log.d("A", "No Such Alogrithm Exception!  Note Body not Encrypted!");
        }
        catch (NoSuchPaddingException noSuchPadding) {

            //Log.d("A", "No Such Padding Exception!  Note Body not Encrypted!");
        }

        return Base64.encode(encryptedBody, 0);
    }

    // Base-64 decodes and decrypts Note Body byte array
    private byte[] decryptBody (byte[] bodyToDecrypt, SecretKey key, byte[] iv ) {

        byte[] decodedBody = Base64.decode(bodyToDecrypt, 0);
        byte[] decodedIv = Base64.decode(iv, 0);
        byte[] decryptedBody = new byte[decodedBody.length];

        try {

            Cipher cipher = Cipher.getInstance(encryptionType);
            IvParameterSpec ivParams = new IvParameterSpec(decodedIv);

            try {

                cipher.init(Cipher.DECRYPT_MODE, key, ivParams);
                decryptedBody = cipher.doFinal(decodedBody);
            }
            catch (InvalidKeyException invalidKey) {

                //Log.d("A", "Invalid Key Exception!  Note Body not Encrypted!");
            }
            catch (InvalidAlgorithmParameterException invalidAlgorithmParameter) {

                //Log.d("A", "Invalid Algorithm Parameter Exception!  Note Body not Encrypted!");
            }
            catch (IllegalBlockSizeException illegalBlockSize) {

                //Log.d("A", "Illegal Block Size Exception!  Note Body not Encrypted!");
            }
            catch (BadPaddingException badPadding) {

                //Log.d("A", "Bad Padding Exception!  Note Body not Encrypted!");
            }
        }
        catch (NoSuchAlgorithmException noSuchAlgorithm) {

            //Log.d("A", "No Such Alogrithm Exception!  Note Body not Encrypted!");
        }
        catch (NoSuchPaddingException noSuchPadding) {

            //Log.d("A", "No Such Padding Exception!  Note Body not Encrypted!");
        }

        return decryptedBody;
    }

    // Generates Encryption Key from passcode using Key Extension Algorithm
    private SecretKey generateKeyFromPasscode(char[] passcode, byte[] savedSalt ) {

        SecretKey key;

        // 1st half (16 bytes) of salt is hardcoded, 2nd half is generated with PRNG and stored in Note DB & added at runtime (salt length should be same as output, 32 bytes)
        byte[] salt = new byte[] {'a', 'y', 'K', ',', 'k', 'S', '%', 'C', 'd', '$', 'e', 'a', '4', 'D', '=', 'I', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x' };
        byte[] newSalt = Base64.decode(savedSalt, 0);

        // Combine Salt Halves & overwrite
        for ( int i = 0; i < 16; i++) {

            salt[16+i] = newSalt[i];
        }

        try {

            // Generate Key
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(passcodeKeyExtensionAlgorithm);
            KeySpec keySpec = new PBEKeySpec(passcode, salt, iterationCount, keyLength);
            byte[] keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
            key = new SecretKeySpec(keyBytes, keyType);
        }
        catch (NoSuchAlgorithmException noSuchAlgorithm) {

            // Genearate Fake Key to avoid crash

            String badKey = "";

            for ( int i = 0; i < keyLength; i++ ) {

                badKey += "x";
            }

            byte[] badKeyBytes = badKey.getBytes();
            key = new SecretKeySpec(badKeyBytes, keyType);

            //Log.d("A", "No Such Alogrithm Exception!  Real Key not generated!");
        }
        catch (InvalidKeySpecException invalidKeySpec) {

            // Genearate Fake Key to avoid crash

            String badKey = "";

            for ( int i = 0; i < keyLength; i++ ) {

                badKey += "x";
            }

            byte[] badKeyBytes = badKey.getBytes();
            key = new SecretKeySpec(badKeyBytes, keyType);

            //Log.d("A", "Invalid Key Spec Exception!  Real Key not generated!");
        }

        return key;
    }

    // Compares entered passcode with saved database passcode (returns 0 if passcodes don't match, 1 if they do match, or 2 if no passcode is saved)
    int checkPasscode (long rowId, char[] passcode) {

        // Get saved salt or create new salt for hashing passcode
        // 1st half (16 bytes) of passcode salt is hardcoded, 2nd half is generated with PRNG and stored in Note DB & added at runtime (salt length should be same as output)
        byte[] passcodeSalt = new byte[]{'1', 'X', '2', 'h', '^', '+', 'o', 'x', '|', ')', '%', '8', '=', 'r', 'd', 'R', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x'};
        byte[] newPasscodeSalt;
        String savedPasscodeSalt = getPasscodeSalt(rowId);

        // Get Saved Passcode salt if available
        if (!savedPasscodeSalt.equals("")) {

            newPasscodeSalt = Base64.decode(savedPasscodeSalt.getBytes(), 0);
        }
        else {

            // If no Passcode Salt is found, then no passcode is saved, return 2
            return 2;
        }

        // Combine Passcode Salt Halves
        for (int i = 0; i < 16; i++) {

            passcodeSalt[16 + i] = newPasscodeSalt[i];
        }

        String encodedPasscodeHash = "";

        // Hash Passcode for comparison with saved hashed pascode if entered passcode is not blank
        if ( passcode!=null ) {

            // Set Encryption Scheme Parameters
            setSchemeParameters(getScheme(rowId));

            try {

                // Hash Passcode
                SecretKeyFactory hashKeyFactory = SecretKeyFactory.getInstance(passcodeHashAlgorithm);
                KeySpec hashKeySpec = new PBEKeySpec(passcode, passcodeSalt, iterationCount, keyLength);
                byte[] hashBytes = hashKeyFactory.generateSecret(hashKeySpec).getEncoded();
                encodedPasscodeHash = Base64.encodeToString(hashBytes, 0);
            }
            catch (NoSuchAlgorithmException noSuchAlgorithm) {

                //Log.d("A", "No Such Alogrithm Exception!  Passcode hash not generated!");

            }
            catch (InvalidKeySpecException invalidKeySpec) {

                //Log.d("A", "Invalid Key Spec Exception!  Passcode hash not generated!");
            }
        }

        return comparePasscodeHashes(rowId, encodedPasscodeHash);
    }

    // Performs actual comparison of entered passcode Hash with saved database passcode Hash (returns 0 if passcodes don't match, 1 if they do match, or 2 if no passcode is saved)
    private int comparePasscodeHashes(long rowId, String passcodeHash) {

        String savedPasscodeHash;
        int isPasscodeCorrect = 0;

        Cursor cursor = getRow(rowId);
        cursor.moveToFirst();

        savedPasscodeHash = cursor.getString(COL_PASSCODE_HASH);

        if (savedPasscodeHash.equals("")) {

            // No passcode is saved

            isPasscodeCorrect = 2;
        }
        else if (passcodeHash.equals(savedPasscodeHash)) {

            // Passcodes match

            isPasscodeCorrect = 1;

            // If Bad Passcode Field is something other than zero, then reset
            if (cursor.getInt(COL_BAD_PASSCODE)!=0) {

                resetBadPasscode(cursor);
            }
        }
        else {

            // Passcodes don't match

            isPasscodeCorrect = 0;

            // Updates count of bad passcode entries in database for note locking
            updateBadPasscode(cursor);
        }

        cursor.close();

        return isPasscodeCorrect;
    }

    // Resets Bad Passcode Field to Zero after successful passcode entry
    private void resetBadPasscode(Cursor noteCursor) {

        if (noteCursor!=null) {

            String where = KEY_ROWID + "=" + noteCursor.getInt(COL_ROWID);
            ContentValues updateValues = new ContentValues();

            // Setup Row Data
            updateValues.put(KEY_NAME, noteCursor.getString(COL_NAME));
            updateValues.put(KEY_DATE, noteCursor.getLong(COL_DATE));
            updateValues.put(KEY_BODY, noteCursor.getString(COL_BODY));
            updateValues.put(KEY_SCHEME, noteCursor.getInt(COL_SCHEME));
            updateValues.put(KEY_SALT, noteCursor.getString(COL_SALT));
            updateValues.put(KEY_IV, noteCursor.getString(COL_IV));
            updateValues.put(KEY_PASSCODE_HASH, noteCursor.getString(COL_PASSCODE_HASH));
            updateValues.put(KEY_PASSCODE_SALT, noteCursor.getString(COL_PASSCODE_SALT));
            updateValues.put(KEY_BAD_PASSCODE, 0);

            noteDb.update(DATABASE_NOTE_TABLE, updateValues, where, null);
        }
    }

    // Update Bad Passcode Field with number of bad attempts unitl 3 is reached and then store system time stamp for purposes of locking
    private void updateBadPasscode(Cursor noteCursor) {

        if (noteCursor!=null) {

            long badPasscode = noteCursor.getLong(COL_BAD_PASSCODE);

            if (badPasscode == 0) {

                badPasscode = 1;
            }
            else if (badPasscode == 1) {

                badPasscode = 2;
            }
            else if (badPasscode == 2) {

                badPasscode = System.currentTimeMillis();
            }

            String where = KEY_ROWID + "=" + noteCursor.getInt(COL_ROWID);
            ContentValues updateValues = new ContentValues();

            // Setup Row Data
            updateValues.put(KEY_NAME, noteCursor.getString(COL_NAME));
            updateValues.put(KEY_DATE, noteCursor.getLong(COL_DATE));
            updateValues.put(KEY_BODY, noteCursor.getString(COL_BODY));
            updateValues.put(KEY_SCHEME, noteCursor.getInt(COL_SCHEME));
            updateValues.put(KEY_SALT, noteCursor.getString(COL_SALT));
            updateValues.put(KEY_IV, noteCursor.getString(COL_IV));
            updateValues.put(KEY_PASSCODE_HASH, noteCursor.getString(COL_PASSCODE_HASH));
            updateValues.put(KEY_PASSCODE_SALT, noteCursor.getString(COL_PASSCODE_SALT));
            updateValues.put(KEY_BAD_PASSCODE, badPasscode);

            noteDb.update(DATABASE_NOTE_TABLE, updateValues, where, null);
        }
    }

    // Determines whether or not note has been locked due to bad passcode entries
    boolean isNoteLocked(long rowId) {

        Cursor noteCursor = getRow(rowId);

        if (noteCursor.moveToFirst()) {

            long badPasscode = noteCursor.getLong(COL_BAD_PASSCODE);

            // Note is not locked if passcode attempts are less than 3 or if proper time has elapsed ( 300,000 ms = 5 min )
            if ( ( badPasscode >= 0 ) & (badPasscode <= 2) ) {

                noteCursor.close();
                return false;
            }
            else if ( System.currentTimeMillis() >= (badPasscode + 300000)) {

                resetBadPasscode(noteCursor);

                noteCursor.close();
                return false;
            }

            noteCursor.close();
        }

        return true;
    }

    private String getPasscodeSalt (long rowId) {

        Cursor cursor = getRow(rowId);
        String passcodeSalt = "";

        if (cursor != null) {

            passcodeSalt = cursor.getString(COL_PASSCODE_SALT);
            cursor.close();
        }

        return passcodeSalt;
    }

    private int getScheme (long rowId) {

        Cursor cursor = getRow(rowId);
        int scheme = 0;

        if (cursor != null) {

            scheme = cursor.getInt(COL_SCHEME);
            cursor.close();
        }

        return scheme;
    }

    String getPasscodeHash (long rowId) {

        Cursor cursor = getRow(rowId);
        String passcodeHash = "";

        if (cursor.moveToFirst()) {

            passcodeHash = cursor.getString(COL_PASSCODE_HASH);
            cursor.close();
        }

        return passcodeHash;
    }

    String getName (long rowId) {

        Cursor cursor = getRow(rowId);
        String name = "";

        if (cursor.moveToFirst()) {

            name = cursor.getString(COL_NAME);
            cursor.close();
        }

        return name;
    }

    // Changes Note Name in Database
    void changeName (long rowId, String newName) {

        Cursor cursor = getRow(rowId);
        String where = KEY_ROWID + "=" + cursor.getInt(COL_ROWID);
        ContentValues updateValues = new ContentValues();

        updateValues.put(KEY_NAME, newName);
        updateValues.put(KEY_DATE, cursor.getLong(COL_DATE));
        updateValues.put(KEY_BODY, cursor.getString(COL_BODY));
        updateValues.put(KEY_SCHEME, cursor.getInt(COL_SCHEME));
        updateValues.put(KEY_SALT, cursor.getString(COL_SALT));
        updateValues.put(KEY_IV, cursor.getString(COL_IV));
        updateValues.put(KEY_PASSCODE_HASH, cursor.getString(COL_PASSCODE_HASH));
        updateValues.put(KEY_PASSCODE_SALT, cursor.getString(COL_PASSCODE_SALT));
        updateValues.put(KEY_BAD_PASSCODE, cursor.getLong(COL_BAD_PASSCODE));

        noteDb.update(DATABASE_NOTE_TABLE, updateValues, where, null);

        cursor.close();
    }

    // Adds/Changes Note Passcode by decrypting note with old passcode (if necessary) and encrypting with new passcode & saving new passcode hash
    void changePasscode (long rowId, char[] oldPasscode, char[] newPasscode) {

        Cursor cursor = getRow(rowId);

        if (cursor!=null) {

            String where = KEY_ROWID + "=" + cursor.getInt(COL_ROWID);
            int scheme = cursor.getInt(COL_SCHEME);
            String salt;
            String iv;
            String encodedPasscodeHash = "";
            String encodedPasscodeSaltHalf;
            byte [] passcodeSaltHalf;

            byte[] savedBody = cursor.getString(COL_BODY).getBytes();
            byte[] body;

            ContentValues updateValues = new ContentValues();

            // If passcode is supplied (Note is intended to be encrypted), then encrypt note body
            if (newPasscode!=null) {

                // Set Encryption Scheme Parameters
                setSchemeParameters(scheme);

                String savedPasscodeHash = cursor.getString(COL_PASSCODE_HASH);

                // If a Passcode Hash is saved, then note was previously encrypted, & can use saved iv, salt, & passcode salt, otherwise generate new ones
                if (!savedPasscodeHash.equals("")) {

                    // Get Saved IV, Salt, & Passcode Salt
                    iv = cursor.getString(COL_IV);
                    salt = cursor.getString(COL_SALT);
                    encodedPasscodeSaltHalf = cursor.getString(COL_PASSCODE_SALT);
                    passcodeSaltHalf = Base64.decode(encodedPasscodeSaltHalf, 0);

                    // Generate Key from previous passcode and decrypt note
                    SecretKey decryptKey = generateKeyFromPasscode(oldPasscode, salt.getBytes());
                    body = decryptBody(savedBody, decryptKey, iv.getBytes());
                }
                else {

                    // Generate New IV & Salt
                    SecureRandom random = new SecureRandom();
                    byte[] saltBytes = new byte[16];
                    random.nextBytes(saltBytes);
                    byte[] ivBytes = generateNewIv();
                    salt = Base64.encodeToString(saltBytes, 0);
                    iv = Base64.encodeToString(ivBytes, 0);

                    // Generate passcode salt half
                    byte[] newPasscodeSaltHalf = new byte[16];
                    random.nextBytes(newPasscodeSaltHalf);
                    encodedPasscodeSaltHalf = Base64.encodeToString(newPasscodeSaltHalf, 0);
                    passcodeSaltHalf = newPasscodeSaltHalf;

                    body = savedBody;
                }

                // Generate Key from New Passcode & Encrypt Note
                SecretKey key = generateKeyFromPasscode(newPasscode, salt.getBytes());
                byte[] encryptedBody = encryptBody(body, key, iv.getBytes());

                // Hash Passcode for saving
                try {

                    // 1st half (16 bytes) of passcode salt is hardcoded, 2nd half is generated with PRNG and stored in Note DB & added at runtime (salt length should be same as output)
                    byte[] passcodeSalt = new byte[]{'1', 'X', '2', 'h', '^', '+', 'o', 'x', '|', ')', '%', '8', '=', 'r', 'd', 'R', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x'};

                    // Combine Passcode Salt Halves
                    for (int i = 0; i < 16; i++) {

                        passcodeSalt[16 + i] = passcodeSaltHalf[i];
                    }

                    // Hash New Passcode
                    SecretKeyFactory hashKeyFactory = SecretKeyFactory.getInstance(passcodeHashAlgorithm);
                    KeySpec hashKeySpec = new PBEKeySpec(newPasscode, passcodeSalt, iterationCount, keyLength);
                    byte[] hashBytes = hashKeyFactory.generateSecret(hashKeySpec).getEncoded();
                    encodedPasscodeHash = Base64.encodeToString(hashBytes, 0);
                }
                catch (NoSuchAlgorithmException noSuchAlgorithm) {

                    //Log.d("A", "No Such Alogrithm Exception!  Passcode hash not generated!");

                }
                catch (InvalidKeySpecException invalidKeySpec) {

                    //Log.d("A", "Invalid Key Spec Exception!  Passcode hash not generated!");
                }

                updateValues.put(KEY_BODY, new String(encryptedBody));
            }
            else {

                // New passcode wasn't supplied

                cursor.close();

                return;
            }

            // Setup Row Data
            updateValues.put(KEY_NAME, cursor.getString(COL_NAME));
            updateValues.put(KEY_DATE, cursor.getLong(COL_DATE));
            updateValues.put(KEY_SCHEME, scheme);
            updateValues.put(KEY_SALT, salt);
            updateValues.put(KEY_IV, iv);
            updateValues.put(KEY_PASSCODE_HASH, encodedPasscodeHash);
            updateValues.put(KEY_PASSCODE_SALT, encodedPasscodeSaltHalf);
            updateValues.put(KEY_BAD_PASSCODE, cursor.getLong(COL_BAD_PASSCODE));

            // Update Database
            noteDb.update(DATABASE_NOTE_TABLE, updateValues, where, null);

            cursor.close();
        }
    }

    void deleteRow(long keyRowId) {

        String where = KEY_ROWID + "=" + keyRowId;
        noteDb.delete(DATABASE_NOTE_TABLE, where, null);
    }

    // Deletes All Unencrypted Notes from Database
    void deleteAllUnencrypted() {

        Cursor deleteCursor = getAllRows();

        if (deleteCursor.moveToFirst()) {

            do {

                // If note is unencrypted, then delete
                if (deleteCursor.getString(COL_PASSCODE_HASH).equals("")) {

                    deleteRow(deleteCursor.getLong(COL_ROWID));
                }
            }
            while (deleteCursor.moveToNext());

            deleteCursor.close();
        }
    }

    private Cursor getAllRows() {

        Cursor c = 	noteDb.query(true, DATABASE_NOTE_TABLE, ALL_NOTETABLE_KEYS,
                null, null, null, null, null, null);

        if (c != null) {

            c.moveToFirst();
        }

        return c;
    }

    // Method to Reurn all Names in the Note Table
    Cursor getAllNames() {

        Cursor c = 	noteDb.query(true, DATABASE_NOTE_TABLE, NAME_KEYS,
                null, null, null, null, null, null);

        if (c != null) {

            c.moveToFirst();
        }

        return c;
    }

    // Method to Reurn all data for Main Note List
    Cursor getAllNotesForMainList() {

        Cursor c = 	noteDb.query(true, DATABASE_NOTE_TABLE, MAIN_LIST_KEYS,
                null, null, null, null, null, null);

        if (c != null) {

            c.moveToFirst();
        }

        return c;
    }

    Cursor getRow(long keyRowId) {

        String where = KEY_ROWID + "=" + keyRowId;
        Cursor c = 	noteDb.query(true, DATABASE_NOTE_TABLE, ALL_NOTETABLE_KEYS,
                where, null, null, null, null, null);

        if (c != null) {

            c.moveToFirst();
        }

        return c;
    }

    // ***** LOW-LEVEL DATABASE ACCESS FOR CREATION AND UPGRADING ******
    private static class NoteDatabaseHelper extends SQLiteOpenHelper {

        NoteDatabaseHelper(Context context) {

            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase _db) {

            _db.execSQL(DATABASE_CREATE_NOTE_TABLE_SQL);
        }

        @Override
        public void onUpgrade(SQLiteDatabase _db, int oldVersion, int newVersion) {

            // Delete Old Database
            _db.execSQL("DROP TABLE IF EXISTS " + DATABASE_NOTE_TABLE);

            // Recreate new database:
            onCreate(_db);
        }
    }
}
