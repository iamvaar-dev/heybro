# HeyBro: The Standalone Android AI Agent - No Root + No Python Needed

[](https://github.com/iamvaar-dev/heybro)
[](https://github.com/iamvaar-dev/heybro)
[](https://github.com/iamvaar-dev/heybro)

**HeyBro** is a standalone AI agent for Android designed for complete on-device automation. Built with Flutter and Kotlin, it operates directly on your device—no computer needed after the initial setup.

-----

## ⚠️ Experimental Project Disclaimer

This is an experimental application developed for educational and research purposes. It is not intended for production use. Use this application at your own risk. The creator is not liable for any damages, data loss, or any other issues that may arise from its use.

-----

## ✨ Features

  * **Standalone AI Agent:** Performs tasks and automation directly on your Android device.
  * **On-Device Automation:** No need for a computer or external server connection after setup.
  * **AI-Powered:** Leverages AI (via Google's AI Studio) to understand and execute tasks.
  * **Full Control:** Uses Accessibility and Overlay permissions to interact with and control the device's UI.

## 🚀 Getting Started

Follow these steps to get HeyBro up and running on your device.

### 1\. Clone the Repository

```bash
git clone https://github.com/iamvaar-dev/heybro
cd heybro
```

### 2\. Install Dependencies

Ensure you have the Flutter SDK installed. Run the following command to fetch the project's dependencies:

```bash
flutter pub get
```

### 3\. Run the Application

You have three options to run the app:

**Option A: Android Emulator**

1.  Start your Android Emulator.
2.  Run the app:
    ```bash
    flutter run
    ```

**Option B: Physical Device**

1.  Connect your Android device to your computer via USB.
2.  Enable **USB Debugging** in your device's Developer Options.
3.  Run the app:
    ```bash
    flutter run
    ```

**Option C: Build APK**

1.  Build the release APK:
    ```bash
    flutter build apk
    ```
2.  The APK will be generated in `build/app/outputs/flutter-apk/app-release.apk`.
3.  Transfer this APK to your Android device and install it.

## 🔧 Configuration

To make the app functional, you must complete these setup steps on your Android device.

### 1\. API Key Setup

1.  Go to [Google AI Studio](https://aistudio.google.com/app/api-keys) to get your API key.
2.  Open the HeyBro app on your device.
3.  Navigate to the settings screen and paste your API key.

### 2\. Grant Permissions

The app requires two critical permissions to function:

1.  **Overlay Permission:** This allows the app to display its interface over other applications.
      * Go to: `Settings > Apps > HeyBro > Display over other apps` and enable it.
2.  **Accessibility Service:** This allows the app to read the screen and perform actions on your behalf.
      * Go to: `Settings > Accessibility > HeyBro` and enable the service.

## 💻 Tech Stack

  * **Frontend:** [Flutter](https://flutter.dev/)
  * **Native Android:** [Kotlin](https://kotlinlang.org/)
  * **AI:** [Google AI (Gemini)](https://aistudio.google.com/)

## 🤝 Contributing

Pull requests are welcome\! For major changes, please open an issue first to discuss what you would like to change.

## ⚖️ Legal Disclaimer

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

**This is an experimental project. Use responsibly and at your own risk\!**
