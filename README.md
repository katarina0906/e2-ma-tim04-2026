# Slagalica

Mobile Android application inspired by the TV quiz show `Slagalica`, developed as part of the `Mobile Applications` course project, Computing and Control Engineering, academic year `2025/26`.

## About the Application

This application is designed to support one-on-one player competition through a set of quiz-based mini-games, along with score tracking, ranking, and user profiles. The system supports both registered and unregistered users.

Registered users have access to additional features such as:

- playing against other users
- user profile and statistics
- leaderboards
- participation in competitions
- ranking and progress tracking

Unregistered users can only play against another player without account-based features.

One match consists of six games:

- Who Knows Knows
- Matching Pairs
- Associations
- Skocko
- Step by Step
- My Number

The project is implemented as an Android application in `Java`.

## Technologies

- Java
- Android Studio
- Gradle
- Android SDK

## Running the Application

To run the application, the following is required:

- Android Studio
- JDK 11
- installed Android SDK
- Android emulator or a physical Android device

### Running in Android Studio

1. Open the project in Android Studio.
2. Wait for Gradle sync to finish.
3. Start an emulator or connect a physical device.
4. Select the `app` module.
5. Click `Run`.

### Running from the Command Line

From the project root directory, run:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
gradlew.bat assembleDebug
```

To install the debug build on an emulator or device:

```bash
./gradlew installDebug
```

On Windows:

```powershell
gradlew.bat installDebug
```

## Note

The current version of the project is a student project developed according to the course specification. The application is intended as a mobile version of the `Slagalica` quiz format.

## Authors

This project is developed by:

- Katarina Zgonjanin
- Teodora Milicevic
- Matea Vidak

Fourth-year students at the Faculty of Technical Sciences, Computing and Control Engineering program.
