[![Alt Text](https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png)](https://play.google.com/store/apps/details?id=net.leonardlabs.locker)

# Locker

Locker is a free, open-source application for composing and storing encrypted notes.  Whether simply protecting a list of passwords or credit card numbers, or guarding your secret manifesto, Locker allows you to encrypt and lock individual notes with a unique passphrase.  Using the same algorithm approved by the US Government to protect Top Secret Classified Information, Locker prevents unauthorized access to your notes completely independent of any device locking mechanisms.  Moreover, it accomplishes this in style, providing a satisfying graphic effect.

Locker currently requires zero permissions, and doesn’t display advertisements or perform data collection.  Relatively minimal in terms of features, a user can compose, edit, save, and delete notes, and edit note names and passphrases.  Note size is currently limited to 200 lines.  Locker uses a key extension algorithm to transform a passphrase into a 256 Bit Key and then encrypts the note using AES (Advanced Encryption Standard).

Designed for operation on Android 4.4 (API 19) and higher.

For API 19 – 25, Locker is currently using PBKDF2withHmacSHA1. 

For API 26 & up, Locker uses PBKDF2withHmacSHA256.


## Future Development

Aside from adding more common notepad type features and addressing some code design issues, priorities are on strengthening the key extension and hashing algorithms (especially for API levels below 26), and making the system more resistant to side-channel attacks.


## Contact

Locker was developed by Blake Leonard at Leonard Labs.

blake@leonardlabs.net

www.leonardlabs.net


## License

Locker is released under the The GNU General Public License v3.0 (GPLv3), which can be found in the LICENSE file in the root of this project.