# Contributing to ATV ADB Commander

Thank you for your interest in contributing to **ATV ADB Commander**! We welcome community contributions, bug reports, feature suggestions, and pull requests.

## How Can I Contribute?

### 1. Reporting Bugs
- Before submitting an issue, search the issue tracker to ensure it hasn't already been reported.
- When filing a bug report, please include:
  - Phone device model and Android version.
  - TV device model (e.g. Chromecast with Google TV, Google TV Streamer, Fire TV Cube) and TV OS version.
  - Steps to reproduce the issue.
  - Expected vs. actual behavior.
  - Relevant logcat or console output if available.

### 2. Suggesting Enhancements
- Have an idea for a feature or UI improvement? Open an issue with the prefix `[Feature Request]` and describe your proposal in detail.

### 3. Submitting Pull Requests
1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/atv-adb-commander.git
   cd atv-adb-commander
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feature/my-cool-feature
   ```
4. Make your changes adhering to existing code conventions:
   - Kotlin style guidelines.
   - Jetpack Compose best practices (state hoisting, unidirectional data flow).
   - Modern Material 3 dark OLED styling (`#08090A` backgrounds, tonal containers).
5. Build and verify the project:
   ```bash
   ./gradlew assembleDebug
   ```
6. Commit your changes with descriptive commit messages:
   ```bash
   git commit -m "Add custom keymap support to smart remote"
   ```
7. Push to your fork and submit a **Pull Request** against `main`.

## Code of Conduct
Please be polite, respectful, and collaborative.

## Contact & Maintainer
- **Author & Maintainer**: Sekhar
- **Email**: [message2sekhar@gmail.com](mailto:message2sekhar@gmail.com)
