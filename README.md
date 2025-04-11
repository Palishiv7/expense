# MoneyPulse - Real-Time SMS Transaction Tracker

MoneyPulse is a privacy-focused expense tracking app that automatically detects and categorizes financial transactions from SMS messages. All processing is done on-device, ensuring your sensitive financial data never leaves your phone.

## Features

- **Real-Time SMS Detection**: Instantly detects and parses transaction SMS messages
- **Automatic Categorization**: Intelligently categorizes transactions based on merchant
- **Financial Dashboard**: View your spending summary and recent transactions
- **Complete Privacy**: All data stays on your device with AES-256 encryption
- **Lightweight & Fast**: Less than 15MB, battery efficient design

## Technical Overview

### Architecture

The app follows Clean Architecture principles with MVVM pattern:

- **UI Layer**: Jetpack Compose for modern, reactive UI
- **Domain Layer**: Contains business logic and models
- **Data Layer**: Manages data access and storage

### Core Technologies

- **Kotlin**: Modern, concise programming language
- **Jetpack Compose**: Declarative UI toolkit
- **Room DB + SQLCipher**: Encrypted local database
- **Hilt**: Dependency injection
- **Coroutines + Flow**: Asynchronous programming
- **SMS Retriever API**: For reading transaction SMS

## Setup Instructions

### Prerequisites

- Android Studio Arctic Fox (2021.3.1) or newer
- JDK 11 or newer
- Android SDK 31+

### Building the Project

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Build and run the app on an Android device with API level 24+ (Android 7.0+)

### Permissions

The app requires the following permissions:

- `RECEIVE_SMS`: To receive and detect transaction messages
- `READ_SMS`: To read transaction messages

## Next Steps

This MVP includes the core functionality to detect and track expenses from SMS. Future versions will include:

- Advanced data visualization and reporting
- Custom categories and tags
- SMS support for more banks and payment services
- Budgeting features
- Backup and restore capabilities (encrypted)

## Privacy Policy

MoneyPulse is designed with privacy as a core principle:

- All data is processed and stored locally on your device
- AES-256 encryption is used for the database
- No analytics or tracking libraries are included
- No data is uploaded to any servers

## License

Copyright (c) 2024 MoneyPulse Technologies 