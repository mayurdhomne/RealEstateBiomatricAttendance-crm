# Implementation Plan

- [ ] 1. Add missing DAO methods to support test requirements
  - Add getOfflineAttendanceById method to OfflineAttendanceDao
  - Ensure method signature matches test usage patterns
  - _Requirements: 3.1, 3.2_

- [ ] 2. Fix database import paths in all test files
  - [ ] 2.1 Update DatabaseIntegrationTest imports
    - Change AppDatabase import from data.database to database package
    - Fix entity imports to use correct package paths
    - Update DAO imports to use correct package paths
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ] 2.2 Update DatabasePerformanceTest imports
    - Fix AppDatabase import path
    - Correct entity and DAO import paths
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ] 2.3 Update DataEncryptionTest imports
    - Fix AppDatabase import path
    - Update entity and DAO imports
    - _Requirements: 2.1, 2.2, 2.3_

- [ ] 3. Fix Result.Success type parameter issues
  - [ ] 3.1 Fix BiometricRepositoryIntegrationTest type parameters
    - Add explicit type parameters to Result.Success usage
    - Fix generic type assertions in test methods
    - _Requirements: 1.1, 1.2, 1.3_

  - [ ] 3.2 Fix BiometricRepositoryTest type parameters
    - Add explicit type parameters to Result.Success usage
    - Fix data property access on Success results
    - _Requirements: 1.1, 1.2, 1.3_

- [ ] 4. Fix API response type mismatches in AuthRepository tests
  - Replace LoginResponse with NetworkLoginResponse in mock setups
  - Update all Response<LoginResponse> to Response<NetworkLoginResponse>
  - Ensure test mocks match actual API service definitions
  - _Requirements: 4.1, 4.2, 4.3_

- [ ] 5. Fix null safety issues in repository tests
  - [ ] 5.1 Add safe call operators for nullable string access
    - Fix AuthRepositoryTest nullable string access
    - Fix BiometricRepositoryTest nullable string access
    - _Requirements: 5.1, 5.2, 5.3_

  - [ ] 5.2 Fix null value assignments in AttendanceConflictResolverTest
    - Replace null assignments with proper nullable handling
    - _Requirements: 5.1, 5.2, 5.3_

- [ ] 6. Fix coroutine scope issues in performance tests
  - [ ] 6.1 Wrap async calls in BiometricPerformanceTest
    - Replace deprecated async usage with coroutineScope blocks
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ] 6.2 Wrap async calls in DatabasePerformanceTest
    - Replace deprecated async usage with coroutineScope blocks
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ] 6.3 Wrap async calls in DataEncryptionTest
    - Replace deprecated async usage with coroutineScope blocks
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ] 6.4 Wrap async calls in TokenSecurityTest
    - Replace deprecated async usage with coroutineScope blocks
    - _Requirements: 6.1, 6.2, 6.3_

- [ ] 7. Fix assertion library imports and usage
  - [ ] 7.1 Fix ComprehensiveErrorHandlingTest assertions
    - Replace kotlin.test imports with JUnit imports
    - Update all assertion method calls to use JUnit syntax
    - _Requirements: 2.4, 8.1, 8.2_

  - [ ] 7.2 Fix ErrorHandlingTest assertions
    - Replace kotlin.test imports with JUnit imports
    - Update all assertion method calls to use JUnit syntax
    - _Requirements: 2.4, 8.1, 8.2_

- [ ] 8. Add missing properties and constants
  - [ ] 8.1 Add reason property to conflict resolution data classes
    - Identify data classes used in AttendanceConflictResolverTest
    - Add reason property to relevant classes
    - _Requirements: 7.1, 7.3_

  - [ ] 8.2 Add QUICK_RETRY_CONFIG constant
    - Define QUICK_RETRY_CONFIG in appropriate utility class
    - Ensure constant is accessible from error handling tests
    - _Requirements: 7.2, 7.3_

- [ ] 9. Fix type inference issues in mock setups
  - [ ] 9.1 Fix TokenManagerTest type inference
    - Add explicit type parameters to mock configurations
    - Resolve type inference issues in test setup
    - _Requirements: 8.1, 8.2, 8.3_

  - [ ] 9.2 Fix DataEncryptionTest type inference
    - Add explicit type parameters where needed
    - Resolve mock setup type issues
    - _Requirements: 8.1, 8.2, 8.3_

  - [ ] 9.3 Fix TokenSecurityTest type inference
    - Add explicit type parameters to mock configurations
    - Resolve type inference issues in test setup
    - _Requirements: 8.1, 8.2, 8.3_

- [ ] 10. Fix AuthRepositoryTest mockk usage issues
  - Replace Runs with Awaits where appropriate for suspend functions
  - Fix mockk configuration for coroutine-based methods
  - _Requirements: 4.1, 4.2, 8.1_

- [ ] 11. Validate all fixes with compilation test
  - Compile entire test suite to ensure all errors are resolved
  - Verify no new compilation errors were introduced
  - Run basic test execution to ensure runtime compatibility
  - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1_