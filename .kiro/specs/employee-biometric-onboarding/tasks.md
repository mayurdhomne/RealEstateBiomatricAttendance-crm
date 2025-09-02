# Implementation Plan

- [x] 1. Setup project dependencies and core infrastructure

  - Add required dependencies (Retrofit, Room, Location services) to build.gradle.kts without changing existing versions
  - Create base API configuration with proper token handling interceptor
  - Set up Room database configuration for offline attendance storage
  - _Requirements: All requirements depend on proper infrastructure_

- [x] 2. Implement secure token management system

  - Create TokenManager class using EncryptedSharedPreferences for JWT storage
  - Implement JWT token validation and expiration checking logic
  - Create AuthInterceptor for automatic Bearer token injection in API requests
  - Add 401 response handling with automatic logout functionality
  - Write unit tests for token management functionality
  - _Requirements: 1.1, 1.2_

- [x] 3. Create core data models and API interfaces

  - Define LoginResponse, EmployeeInfo, and BiometricRegistrationRequest data classes
  - Create AttendanceRequest/Response models for check-in/check-out operations
  - Implement Retrofit API service interfaces for all endpoints
  - Create Room database entities for offline attendance storage
  - Write unit tests for data model serialization/deserialization
  - _Requirements: 1.1, 2.5, 4.4, 4.5, 5.1_

- [x] 4. Implement authentication repository and login functionality

  - Create AuthRepository implementation with login API integration
  - Modify existing LoginActivity to use new authentication flow
  - Implement proper error handling for login failures and network issues
  - Add biometric registration status checking from login response
  - Create navigation logic based on biometrics_registered flag
  - Write integration tests for login flow
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 5. Transform RegisterActivity into BiometricRegistrationActivity

  - Remove existing form fields (name, email, password, confirm password) from layout
  - Keep only biometric scan buttons with updated styling and labels
  - Implement BiometricRepository for hardware detection and registration
  - Create sequential biometric registration flow (face first, then fingerprint)
  - Integrate with existing FacescanActivity for face registration
  - Add progress indicators and success/failure feedback
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

- [x] 6. Enhance FacescanActivity for biometric registration integration

  - Modify FacescanActivity to return registration success/failure status
  - Implement proper face detection validation for registration purposes
  - Add registration-specific UI feedback and progress indicators
  - Create data passing mechanism between BiometricRegistrationActivity and FacescanActivity
  - Handle face registration completion and return to parent activity
  - _Requirements: 2.3, 2.5_

- [x] 7. Implement fingerprint registration using AndroidX Biometric

  - Create fingerprint registration flow using existing biometric setup
  - Implement hardware availability checking for fingerprint sensors
  - Add fingerprint authentication success validation for registration
  - Create proper error handling for fingerprint registration failures
  - Integrate fingerprint registration with BiometricRegistrationActivity flow
  - _Requirements: 2.2, 2.4, 2.5_

- [x] 8. Create biometric registration API integration

  - Implement BiometricRepository with API call to /biometric/register/ endpoint
  - Create proper request payload with employee_id, face_registered, fingerprint_registered
  - Add Bearer token authentication for biometric registration API
  - Implement error handling for registration API failures
  - Create success flow that redirects to dashboard after registration
  - Write integration tests for biometric registration API
  - _Requirements: 2.5, 2.6_

- [x] 9. Create DashboardActivity with attendance overview

  - Design and implement dashboard layout with navy/gold professional theme
  - Create attendance overview cards showing days present and last check-in
  - Implement pie chart visualization using existing MPAndroidChart dependency
  - Add leave information display (sick leaves, other leaves)
  - Create "Take Attendance" floating action button with proper styling
  - Implement DashboardRepository for fetching attendance data
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 10. Implement location services for attendance tracking

  - Add location permission handling with proper rationale dialogs
  - Create LocationManager wrapper for getting current coordinates
  - Implement Geocoder integration for location validation
  - Add location accuracy checking and retry mechanisms
  - Create fallback options for location unavailability scenarios
  - Write unit tests for location service functionality
  - _Requirements: 4.3, 4.4, 4.5_

- [x] 11. Create AttendanceActivity with biometric selection

  - Design bottom sheet drawer with "Use Face" and "Use Fingerprint" options
  - Implement biometric type selection and validation flow
  - Create integration with existing FacescanActivity for face-based attendance
  - Implement fingerprint authentication for fingerprint-based attendance
  - Add proper loading states and success/failure animations
  - Create attendance type detection (check-in vs check-out) logic
  - _Requirements: 4.1, 4.2, 4.6_

- [x] 12. Implement attendance API integration

  - Create AttendanceRepository with check-in and check-out API calls
  - Implement proper request payload with latitude, longitude, and scan_type
  - Add Bearer token authentication for attendance APIs
  - Create logic to determine check-in vs check-out based on daily status
  - Implement success response handling with user feedback
  - Add comprehensive error handling for attendance API failures
  - _Requirements: 4.4, 4.5, 4.6_

- [x] 13. Implement offline attendance storage and sync

  - Create Room database tables for offline attendance storage
  - Implement offline attendance saving when network is unavailable
  - Create background sync service for uploading pending attendance records
  - Add network connectivity monitoring for automatic sync triggering
  - Implement sync status indicators and user feedback
  - Create conflict resolution for offline/online attendance data
  - Write integration tests for offline functionality
  - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 14. Add duplicate attendance prevention system

  - Create Room database table for tracking daily attendance punches
  - Implement 2-minute cooldown period checking using local storage
  - Add attendance punch validation before API calls
  - Create user feedback for duplicate punch attempts
  - Implement daily attendance status tracking (checked-in/checked-out)
  - Write unit tests for duplicate prevention logic
  - _Requirements: 4.7_

- [x] 15. Implement comprehensive error handling and user feedback

  - Create centralized error handling for network, biometric, and location errors
  - Add user-friendly error messages with actionable solutions
  - Implement retry mechanisms for failed operations
  - Create proper loading states and progress indicators throughout the app
  - Add success animations and feedback for completed operations
  - Implement graceful degradation for hardware unavailability
  - _Requirements: 6.3, 6.4, 6.5_

- [x] 16. Apply professional UI theme and responsive design


  - Implement navy and gold color scheme across all new activities
  - Ensure responsive design works properly on 5-7 inch screens
  - Add smooth animations and transitions between activities
  - Create consistent Material Design 3 styling using existing theme
  - Implement proper accessibility features and content descriptions
  - Add loading indicators and progress feedback throughout the app
  - _Requirements: 6.1, 6.2, 6.5, 6.6_

- [x] 17. Create comprehensive testing suite






  - Write unit tests for all repository classes and business logic
  - Create integration tests for API calls and database operations
  - Implement UI tests for critical user flows (login, biometric registration, attendance)
  - Add end-to-end tests for complete onboarding and attendance cycles
  - Create performance tests for biometric operations and database queries
  - Write security tests for token handling and data encryption
  - _Requirements: All requirements need proper testing coverage_

- [x] 18. Integrate all components and test complete user flows




  - Connect all activities with proper navigation and data flow
  - Test complete employee onboarding flow from login to dashboard
  - Validate full attendance cycle from biometric selection to API success
  - Test offline scenarios with network disconnection and reconnection
  - Verify proper token handling across all authenticated operations
  - Conduct end-to-end testing of all user scenarios and edge cases
  - _Requirements: All requirements integration testing_
