@echo off
echo MoneyPulse Installation Helper
echo ============================
echo.

set ADB_PATH="C:\Users\shiva\AppData\Local\Android\Sdk\platform-tools\adb.exe"

echo Checking if device is connected...
%ADB_PATH% devices

echo.
echo Ready to install MoneyPulse app
echo.
echo Please make sure:
echo 1. Your phone is connected via USB
echo 2. USB debugging is enabled on your phone
echo 3. You accepted any authorization prompts on your phone
echo.
pause

echo Installing MoneyPulse (may take a few moments)...
%ADB_PATH% install -r "https://github.com/Palishiv7/expense/releases/download/v1.0/app-debug.apk"

echo.
echo Installation complete!
echo If successful, you should now see MoneyPulse in your app drawer.
echo.
pause 