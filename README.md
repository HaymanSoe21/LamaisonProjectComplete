# Project Name
Lamaison Property and Tenant Management System
## Prerequisites
* Android Studio (latest version)
* IntelliJ IDEA
* OpenCobolIDE

## 1. Running the Ktor Backend & COBOL
1. Open the `lamaison-backendfianl` folder in IntelliJ IDEA.
2. Allow Gradle to sync and download dependencies.
3. Note: The backend relies on a COBOL executable and dll files (runtime libraries required by your COBOL executable) in root folder of ktor backend project.
4. Run the `Application.kt` file to start the server. The server will run on `http://localhost:8080`.

## 2. Running the Android Frontend
1. Open the `La_Maison` folder in Android Studio.
2. Allow Gradle to sync.
3. Launch an Android Emulator or connect a physical device.
4. Click the Run (Play) button. 

## 3. Firebase Configuration
* The Firebase `google-services.json` is already included in the Android `app` directory. 
* The backend Firebase Admin SDK key is included in the Ktor resources directory. 
*(Note: Keep this repo private since these keys are included!)*
