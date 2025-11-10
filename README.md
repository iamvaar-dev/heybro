# HeyBro: Your Standalone Android AI Agent (No Root + No Python Needed)

### Demo Video

[![Watch the video](https://img.youtube.com/vi/b0q0bHPGtck/maxresdefault.jpg)](https://youtu.be/b0q0bHPGtck)

## What is HeyBro?

**HeyBro** is a standalone AI agent that controls your phone directly to complete tasks for you. It can automatically send a WhatsApp message, send your friend money, send an email, capture a photo, and more, all based on your instructions.

-----

## ⚠️ Warning: Experimental Project

This app is experimental and was made for learning and research. It's not meant for everyday, reliable use. Please use it at your own risk. The creator is not responsible for any problems, data loss, or other issues that might happen.

-----

## ✨ Features

  * **Standalone AI:** Works directly on your Android device to get tasks done.
  * **On-Device Automation:** No computer needed after setup.
  * **AI-Powered:** Uses Google Gemini AI to understand what you want.
  * **Full Control:** Uses Accessibility and Overlay permissions to see your screen and tap buttons for you.
  * **Voice Control:** You can use your voice to give commands (the app must be open for this to work right now).

## 🚀 Getting Started

Follow these steps to get HeyBro running.

### 1\. Clone the Repository

```bash
git clone https://github.com/iamvaar-dev/heybro
cd heybro
```

### 2\. Install Dependencies

Make sure you have the Flutter SDK. Run this command to get the project's packages:

```bash
flutter pub get
```

### 3\. Run the Application

You have three options:

**Option A: Android Emulator**

1.  Start your Android Emulator.
2.  Run the app:
    ```bash
    flutter run
    ```

**Option B: Physical Device**

1.  Connect your Android phone to your computer with a USB cable.
2.  Turn on **USB Debugging** in your phone's Developer Options.
3.  Run the app:
    ```bash
    flutter run
    ```

**Option C: Build APK (Manual Install)**

1.  Build the installer file:
    ```bash
    flutter build apk
    ```
2.  Find the file at `build/app/outputs/flutter-apk/app-release.apk`.
3.  Copy this file to your phone and install it.

## 🔧 Configuration (Important\!)

You must do these steps on your phone to make the app work.

### 1\. API Setup

This app uses a **Google Service Account** for its AI brain.

1.  **Get Google Credentials:**

      * You need to get your **Google Service Account JSON file**. Use this [tutorial](https://www.youtube.com/watch?v=gjAVd784WqE) to get it.
      * You also need to **enable the Vertex AI API**. Use this [tutorial](https://youtu.be/Tt9poEVzQ6g?si=4QeqFaSWnq2MNUD1) to do that.

2.  **Configure the App:**

      * Open the HeyBro app on your phone.
      * Go to the **API-Settings Screen**.
      * Follow the instructions on that screen to enter your Google Service Account credentials. **You must do this inside the app.**

3.  **(Optional) Voice Assistant:**

      * If you want to use voice commands, you need a [Porcupine](https://picovoice.ai/platform/porcupine/) API key. Enter this in the settings as well.

### 2\. Grant Permissions

The app needs two powerful permissions to work:

1.  **Overlay Permission:** (Lets the app show things over other apps)
      * Go to: `Settings > Apps > HeyBro > Display over other apps` and turn it ON.
2.  **Accessibility Service:** (Lets the app read the screen and tap for you)
      * Go to: `Settings > Accessibility > HeyBro` and turn the service ON.

## 💻 Tech Stack

  * **Frontend:** [Flutter](https://flutter.dev/)
  * **Native Android:** [Kotlin](https://kotlinlang.org/)
  * **AI:** [Google AI](https://ai.google/) (via Service Account)

## 🤝 Contributing

Pull requests are welcome\! If you want to make a big change, please open an issue first so we can talk about it.

## ⚖️ Legal Disclaimer

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

**This is an experimental project. Use responsibly and at your own risk\!**
