# Run Configurations & Code Coverage

This document explains the IntelliJ IDEA run configurations and code coverage setup for the ACS Student Council backend application.

## IntelliJ IDEA Run Configurations

Multiple run configurations have been set up for different environments:

### 1. Development Environment - `BackEndApplication [DEV]`
- Uses the demo database (`demo_db`)
- Shows detailed SQL logs
- Enables Swagger UI
- Points to local development services
- Active profile: `dev`

### 2. Production Environment - `BackEndApplication [PROD]`
- Uses the production database (`stuco`)
- Minimal logging
- Disables Swagger UI
- Points to production services
- Active profile: `prod`

### 3. Testing Environment - `BackEndApplication [TEST]`
- Uses an in-memory H2 database
- Detailed logging for debugging
- Enables H2 console at `/h2-console`
- Active profile: `test`

### Testing with Coverage - `Run All Tests with Coverage`
- Runs all tests with JaCoCo coverage
- Generates coverage reports
- Cleans before running to ensure fresh results

### Verify Coverage - `Check Coverage Verification`
- Runs tests and verifies coverage against configured rules
- Fails if coverage thresholds are not met

## Configuration Files

- `application.properties` - Base configuration with common settings
- `application-dev.properties` - Development-specific settings
- `application-prod.properties` - Production-specific settings
- `application-test.properties` - Test-specific settings

## Code Coverage with JaCoCo

JaCoCo has been configured to provide code coverage reports for the project tests:

### Coverage Reports

After running the `Run All Tests with Coverage` configuration:
1. View the HTML coverage report at: `build/jacocoHtml/index.html`
2. XML reports are also available for CI/CD integration

### Coverage Rules

The following coverage rules have been configured:

- Overall code coverage minimum: 60%
- Order service classes minimum (disabled by default): 70%

You can customize these rules in the `jacocoTestCoverageVerification` section of `build.gradle`.

## Using Run Configurations in IntelliJ IDEA

1. Open the Run Configuration dropdown in the top-right toolbar
2. Select the desired configuration
3. Click the green "Run" or "Debug" button

All configurations are stored in the `.run` directory and will be shared with other team members when committed to version control.
