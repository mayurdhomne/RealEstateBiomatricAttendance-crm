# Requirements Document

## Introduction

This feature transforms the existing RealEstate-CRM Android app registration flow into an employee onboarding system with biometric attendance capabilities. The system allows admins to register employees via a web panel, while employees use the mobile app to complete onboarding, register biometrics (face/fingerprint), and punch attendance (check-in/check-out). The existing UI components from RegisterActivity will be reused but modified to focus solely on biometric registration after employee login.

## Requirements

### Requirement 1

**User Story:** As an employee, I want to log in with credentials provided by my admin, so that I can access the biometric onboarding process.

#### Acceptance Criteria

1. WHEN an employee enters valid username and password THEN the system SHALL authenticate via https://shubhamgharde28.pythonanywhere.com/login/ API endpoint
2. WHEN authentication succeeds THEN the system SHALL store JWT token in EncryptedSharedPreferences
3. IF biometrics_registered is false in login response THEN the system SHALL redirect to biometric registration
4. IF biometrics_registered is true THEN the system SHALL skip onboarding and redirect to dashboard

### Requirement 2

**User Story:** As an employee, I want to register my face and fingerprint biometrics during onboarding, so that I can use them for attendance punching.

#### Acceptance Criteria

1. WHEN biometric registration screen loads THEN the system SHALL detect available hardware (camera for face, biometric sensor for fingerprint)
2. IF both biometric types are available THEN the system SHALL prompt to register both sequentially
3. WHEN face registration is initiated THEN the system SHALL use existing FacescanActivity with CameraX integration
4. WHEN fingerprint registration is initiated THEN the system SHALL use AndroidX Biometric API
5. WHEN both biometrics are successfully captured THEN the system SHALL call https://shubhamgharde28.pythonanywhere.com/biometric/register/ API with employee_id, face_registered, and fingerprint_registered fields
6. WHEN biometric registration API succeeds THEN the system SHALL redirect to dashboard

### Requirement 3

**User Story:** As an employee, I want to view my attendance overview on the dashboard, so that I can track my presence and leave information.

#### Acceptance Criteria

1. WHEN dashboard loads THEN the system SHALL display days present count
2. WHEN dashboard loads THEN the system SHALL display last check-in time
3. WHEN dashboard loads THEN the system SHALL show pie chart visualization of present vs absent days
4. WHEN dashboard loads THEN the system SHALL display leave information (sick leaves, other leaves)
5. WHEN dashboard loads THEN the system SHALL provide "Take Attendance" CTA button

### Requirement 4

**User Story:** As an employee, I want to punch attendance using face or fingerprint scan, so that my check-in/check-out is recorded with location data.

#### Acceptance Criteria

1. WHEN "Take Attendance" is tapped THEN the system SHALL show bottom drawer with "Use Face" and "Use Fingerprint" options
2. WHEN biometric option is selected THEN the system SHALL capture the respective biometric
3. WHEN biometric capture succeeds THEN the system SHALL get current location via Geocoder
4. IF no previous check-in exists for the day THEN the system SHALL call https://shubhamgharde28.pythonanywhere.com/attendance/check-in/ API with latitude, longitude, and scan_type
5. IF check-in exists for the day THEN the system SHALL call https://shubhamgharde28.pythonanywhere.com/attendance/check-out/ API with latitude, longitude, and scan_type
6. WHEN attendance API succeeds THEN the system SHALL show success animation
7. WHEN attendance is punched THEN the system SHALL prevent re-punch within 2 minutes using Room DB

### Requirement 5

**User Story:** As an employee, I want the app to work offline for attendance punching, so that network issues don't prevent me from recording attendance.

#### Acceptance Criteria

1. WHEN network is unavailable during attendance punch THEN the system SHALL save attendance data in Room DB
2. WHEN network becomes available THEN the system SHALL automatically sync pending attendance records
3. WHEN offline data exists THEN the system SHALL show sync status indicator
4. WHEN sync completes successfully THEN the system SHALL remove synced records from local storage

### Requirement 6

**User Story:** As an employee, I want a professional and responsive UI experience, so that the app is easy to use on my device.

#### Acceptance Criteria

1. WHEN app loads THEN the system SHALL use existing navy and gold professional theme
2. WHEN app runs on 5-7 inch screens THEN the system SHALL display responsive design
3. WHEN biometric capture fails THEN the system SHALL provide retry options
4. WHEN any operation is in progress THEN the system SHALL show loading indicators
5. WHEN transitions occur THEN the system SHALL display smooth animations
6. WHEN using existing RegisterActivity UI THEN the system SHALL remove name, email, password fields and keep only biometric scan buttons