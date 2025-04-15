# Keystore Setup Guide for Play Store Submission

This guide explains how to create a keystore for signing your app and set up GitHub Secrets for automated building.

## 1. Creating a Keystore (One-time Setup)

You need to create a keystore file to sign your app. This is a critical step - **you must keep this keystore secure and never lose it**. If you lose your keystore, you cannot update your app on the Play Store.

### Using Android Studio (Recommended)
If you have temporary access to Android Studio:

1. Open Android Studio
2. Go to Build → Generate Signed Bundle/APK
3. Click "Create new..."
4. Fill in the form:
   - Keystore path: Choose a secure location
   - Password: Create a strong password
   - Alias: Create a key alias
   - Key password: Create a key password
   - Validity: 25+ years recommended
   - Certificate info: Fill in your details
5. Click "OK" to generate the keystore

### Using Command Line
If you don't have Android Studio:

```bash
keytool -genkey -v -keystore moneypulse.keystore -alias moneypulse -keyalg RSA -keysize 2048 -validity 10000
```

Follow the prompts to enter your details and passwords.

## 2. Encoding Keystore for GitHub

Since you're using GitHub Actions to build your app, you need to store your keystore securely in GitHub Secrets.

1. Encode your keystore to Base64:

```bash
base64 -i moneypulse.keystore -o keystore-base64.txt
```

2. On Windows:
```
certutil -encode moneypulse.keystore keystore-base64.txt
```

3. Open the generated file and copy all content.

## 3. Setting Up GitHub Secrets

Go to your GitHub repository:

1. Click Settings → Secrets and variables → Actions
2. Add the following secrets:

| Secret Name | Value |
|-------------|-------|
| `KEYSTORE_BASE64` | The entire content of keystore-base64.txt |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | Your key alias (e.g., "moneypulse") |
| `KEY_PASSWORD` | Your key password |

## 4. Testing the Build

After setting up the secrets:

1. Go to the "Actions" tab in your GitHub repository
2. Select the "Android Build & Release" workflow
3. Click "Run workflow"
4. Select "release" from the dropdown
5. Click "Run workflow"

This will trigger a signed release build that you can download from the build artifacts.

## 5. Creating a Release for Play Store

To create an official release:

1. Tag your release in git:
```bash
git tag -a v1.0.0 -m "Version 1.0.0"
git push origin v1.0.0
```

2. This will automatically trigger the workflow to:
   - Build a signed .aab bundle for Play Store
   - Build a signed .apk for direct distribution
   - Create a GitHub release with both files attached

3. Download the .aab file from the GitHub release
4. Upload this .aab file to the Play Store

## Important Notes

1. **NEVER commit your keystore file to git**
2. Keep secure backups of your keystore file
3. Document your keystore password, key alias, and key password somewhere secure
4. If you lose any of these, you cannot update your app on the Play Store 