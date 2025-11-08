# HeyBro: The Standalone Android AI Agent - No Root + No Python Needed

### Explanation + Demo

[](https://youtu.be/b0q0bHPGtck)

[](https://github.com/iamvaar-dev/heybro)
[](https://github.com/iamvaar-dev/heybro)
[](https://github.com/iamvaar-dev/heybro)

**HeyBro** is a standalone AI agent for Android designed for complete on-device automation. Built with Flutter and Kotlin, it operates directly on your device—no computer needed.

-----

## ⚠️ Experimental Project Disclaimer

This is an experimental application developed for educational and research purposes. It is not intended for production use. Use this application at your own risk. The creator is not liable for any damages, data loss, or any other issues that may arise from its use.

-----

## ✨ Features

  * **Standalone AI Agent:** Performs tasks and automation directly on your Android device.
  * **On-Device Automation:** No need for a computer or external server connection after setup.
  * **AI-Powered:** Leverages AI (via **Google Gemini AI**) to understand and execute tasks.
  * **Full Control:** Uses Accessibility and Overlay permissions to interact with and control the device's UI.
  * **Voice Control:** Can control the app with voice [but the app should be opened for now. I will move the application to kotlin side to make it run in foreground.

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

### 1\. API Configuration

This app now uses a **Google Service Account** for AI processing. Use this [tutorial](https://www.youtube.com/watch?v=gjAVd784WqE) to enable get google service account json. 

And enable the vertex AI API in with this [tutorial](https://youtu.be/Tt9poEVzQ6g?si=4QeqFaSWnq2MNUD1).


1.  **Obtain your credentials:** You must first have:
      * Your Google Service Account JSON credentials.
2.  **Configure the app:**
      * Open the HeyBro app on your device.
      * Navigate to the **API-Settings Screen**.
      * Follow the on-screen prompts to enter your Vercel API details and provide your Google Service Account credentials. **Everything should be done within the app** in this settings screen.


IF YOU WANT TO ENABLE VOICE ASSISTANT YOU NEED TO HAVE [PORCUPINE](https://picovoice.ai/platform/porcupine/) API KEY.

### 2\. Grant Permissions

The app requires two critical permissions to function:

1.  **Overlay Permission:** This allows the app to display its interface over other applications.
      * Go to: `Settings > Apps > HeyBro > Display over other apps` and enable it.
2.  **Accessibility Service:** This allows the app to read the screen and perform actions on your behalf.
      * Go to: `Settings > Accessibility > HeyBro` and enable the service.

## 💻 Tech Stack

  * **Frontend:** [Flutter](https://flutter.dev/)
  * **Native Android:** [Kotlin](https://kotlinlang.org/)
  * **AI:** [Google AI](https://ai.google/) (via Service Account)

## 🤝 Contributing

Pull requests are welcome\! For major changes, please open an issue first to discuss what you would like to change.

## ⚖️ Legal Disclaimer

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

**This is an experimental project. Use responsibly and at your own risk\!**
