@echo off
REM Task 18 Integration Test Runner
REM This script runs the complete integration test suite for the employee biometric attendance system

echo 🚀 Starting Task 18 Integration Tests...
echo ========================================

REM Check if device/emulator is connected
echo 📱 Checking device connection...
adb devices | findstr "device$" >nul
if %errorlevel% neq 0 (
    echo ❌ No device/emulator connected. Please connect a device and try again.
    pause
    exit /b 1
)

echo ✅ Device connected successfully

REM Check if device has required API level
echo 🔍 Checking device API level...
for /f "tokens=2 delims=:" %%i in ('adb shell getprop ro.build.version.sdk') do set API_LEVEL=%%i
set API_LEVEL=%API_LEVEL: =%
if %API_LEVEL% lss 21 (
    echo ❌ Device API level %API_LEVEL% is too low. Minimum required: 21
    pause
    exit /b 1
)

echo ✅ Device API level: %API_LEVEL%

REM Check network connectivity
echo 🌐 Checking network connectivity...
adb shell ping -c 1 8.8.8.8 >nul 2>&1
if %errorlevel% neq 0 (
    echo ⚠️  Network connectivity issues detected. Some tests may fail.
) else (
    echo ✅ Network connectivity confirmed
)

REM Run the complete integration test suite
echo 🧪 Running Complete Integration Test Suite...
echo ========================================

gradlew.bat :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.crm.realestate.CompleteIntegrationTestSuite --info

set TEST_RESULT=%errorlevel%

echo ========================================
if %TEST_RESULT% equ 0 (
    echo 🎉 All integration tests passed successfully!
    echo ✅ Task 18: Integrate all components and test complete user flows - COMPLETED
    
    echo.
    echo 📊 Test Summary:
    echo - Complete user flow: Login → Biometric Registration → Dashboard → Attendance
    echo - Offline scenarios: Network disconnection and reconnection
    echo - Error handling: Invalid inputs, network failures, biometric failures
    echo - Edge cases: Hardware unavailability, API timeouts, data conflicts
    
    echo.
    echo 🔧 Components Integrated:
    echo - LoginActivity → BiometricRegistrationActivity
    echo - BiometricRegistrationActivity → DashboardActivity
    echo - DashboardActivity → AttendanceActivity
    echo - Offline storage and sync mechanisms
    echo - Error handling and retry logic
    
) else (
    echo ❌ Some integration tests failed. Please check the logs above.
    echo 💡 Common issues:
    echo - Device not properly connected
    echo - Insufficient permissions
    echo - Network connectivity issues
    echo - Biometric hardware not available
)

echo.
echo 📁 Test reports available in:
echo - HTML: app\build\reports\androidTests\connected\
echo - XML: app\build\outputs\androidTest-results\connected\

pause
exit /b %TEST_RESULT% 