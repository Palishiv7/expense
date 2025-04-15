# Play Store Submission Guide for MoneyPulse

This document provides step-by-step instructions for the manual parts of the Google Play Store submission process, with a focus on SMS permissions approval and privacy policy requirements.

## 1. Permissions Declaration Form

The Permissions Declaration Form is a critical part of your app submission when requesting sensitive permissions like SMS access.

### Where to Find It
1. Log in to your [Google Play Console](https://play.google.com/console)
2. Select your app
3. Go to "App content" in the left menu
4. Click on "Sensitive app permissions"
5. You will see a section for "SMS & Call Log permissions" - click "Start declaration"

### How to Fill It Out

#### Step 1: Select Permission Usage
- Check the boxes for the SMS permissions your app uses: 
  - ✓ READ_SMS
  - ✓ RECEIVE_SMS

#### Step 2: Select App Type/Core Functionality
- Select "Financial/Banking App" as your app category
- Explain that your app is a personal finance management tool that helps users track expenses through SMS notifications from banks

#### Step 3: Justify Permission Usage
Write the following in the justification field:

```
MoneyPulse uses SMS permissions to automatically detect and categorize financial transactions from banking and payment SMS notifications. This core functionality:

1. Automatically extracts transaction details (amounts, merchants, dates) from bank SMS messages
2. Categorizes expenses in real-time without manual input
3. Helps users track spending patterns based on their bank transaction messages
4. Removes the need for manual entry of financial data, which is the primary value proposition

Without SMS access, users would need to manually enter all transaction data, significantly degrading the app's core automatic expense tracking functionality.

All SMS processing happens locally on the device. No SMS data is transmitted to external servers, and only financial transaction messages are processed.
```

#### Step 4: Record and Submit Demo Video
You must submit a 1-3 minute screen recording showing how SMS permissions are used:

1. Record a demo showing:
   - The permission request flow with explanation dialog
   - How bank SMS messages are detected
   - How transactions are automatically categorized
   - The value it provides to users

2. Upload this video to a non-public platform (YouTube unlisted or Google Drive)
3. Provide the link in the declaration form

## 2. Privacy Policy Submission

### Preparing Your Privacy Policy
1. Host your privacy policy online at a publicly accessible URL 
   - Use your company website or platforms like Firebase Hosting
   - Example: https://www.moneypulse.com/privacy-policy

2. Ensure your privacy policy includes all required elements:
   - What data is collected (SMS transaction data)
   - How it's processed and stored
   - Security measures in place
   - User rights and data deletion options
   - Third-party sharing practices (or lack thereof)
   - Contact information

### Submitting in Play Console
1. Go to your app in Play Console
2. Navigate to "App content" > "Privacy policy"
3. Enter your privacy policy URL
4. Click "Save"

### App Store Listing
1. In Play Console, go to "Store presence" > "Store listing"
2. Scroll to "Privacy policy"
3. Enter the same privacy policy URL
4. Save changes

## 3. Data Safety Section

1. In Play Console, go to "App content" > "Data safety"
2. Click "Start" or "Edit"
3. For Data Collection, check:
   - ✓ Financial Info (Used: Yes, Shared: No)
   - ✓ Messages (SMS/MMS) (Used: Yes, Shared: No)

4. For each data type, specify:
   - Collection is optional
   - Data is processed on device only
   - Data is not shared with third parties
   - Data is encrypted
   - Data can be deleted by user

5. Review and submit

## 4. Content Rating Questionnaire

1. In Play Console, go to "App content" > "Content rating"
2. Complete the questionnaire:
   - App category: Finance/Banking
   - No adult content
   - No violence
   - No profanity
   - No controlled substances

## 5. Target Audience and Content

1. In Play Console, go to "App content" > "Target audience"
2. Select target age groups (likely 18+ for a financial app)
3. Confirm your app doesn't target children

## Common Pitfalls to Avoid

1. **Permission Justification**: Be extremely clear about why SMS permissions are essential
2. **Video Demo**: Ensure your video clearly shows the permission explanation dialog
3. **Privacy Policy**: Make sure your privacy policy explicitly addresses SMS data handling
4. **App Description**: Mention SMS functionality in your app description
5. **Screenshots**: Include SMS functionality in your app screenshots if possible

## Final Checklist Before Submission

- [ ] SMS permission explanation dialog implemented in app
- [ ] Privacy policy implemented in app and hosted online
- [ ] Permissions Declaration Form completed with detailed justification
- [ ] Demo video created and uploaded
- [ ] Data Safety section accurately reflects SMS data usage
- [ ] App listing mentions SMS functionality
- [ ] Privacy policy URL added to both required locations in Play Console
- [ ] Final app testing with SMS permissions on real devices

Following these instructions will maximize your chances of a successful Google Play Store submission for an app with SMS permissions. 