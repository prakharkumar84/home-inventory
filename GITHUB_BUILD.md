# Build the Android app with GitHub Actions

## First-time setup

1. Create a new GitHub repository.
2. Extract the Home Inventory project ZIP.
3. Copy the extracted project contents to the repository root.
4. Ensure `.github/workflows/android-build.yml` is present.
5. Commit and push the files to `main`.

The workflow starts automatically after each push or pull request. It can also be started manually:

1. Open the repository on GitHub.
2. Select **Actions**.
3. Select **Build Android APK**.
4. Select **Run workflow**.
5. Wait for the green check mark.
6. Open the completed workflow run.
7. Under **Artifacts**, download `home-inventory-debug-<run number>`.
8. Extract the downloaded artifact to get `app-debug.apk`.

## What the workflow does

- Uses Ubuntu on a GitHub-hosted runner.
- Installs Java 17.
- Configures Gradle 8.7.
- Runs the debug unit tests.
- Builds `app-debug.apk`.
- Keeps the downloadable APK artifact for 30 days.
- Uploads test reports for 14 days if the build fails.

## Important

This workflow produces a **debug APK** for testing and sideloading. It is not a Google Play release build. A Play Store release must use a private signing key stored as encrypted GitHub repository secrets. Never commit a `.jks` or `.keystore` file, passwords, or service-account credentials to the repository.
