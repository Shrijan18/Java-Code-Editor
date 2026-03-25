
```markdown
# 🧑‍💻 Java Code Editor (Mini VS Code Clone)

A lightweight and feature-rich **Java-based code editor** built using **Swing** and **RSyntaxTextArea**, inspired by modern IDEs like VS Code.

---

## 🚀 Features

- ✨ Syntax highlighting (Java)
- 🗂 Multiple tabs support
- ❌ Close tabs with cross button
- 📂 File Explorer panel
- 💾 Auto-save functionality
- 🎨 Dark & Light themes
- ↩️ Undo / Redo support
- ▶️ Run Java code directly inside the editor
- 🖥 Console output panel
- 📐 Auto-indentation
- 🧠 Basic code formatting

---

## 🛠 Tech Stack

- **Language:** Java (JDK 17+ recommended)
- **UI Framework:** Swing
- **Libraries:**
  - [RSyntaxTextArea](https://bobbylight.github.io/RSyntaxTextArea/) (for syntax highlighting)

---

## 📂 Project Structure

```

JavaCodeEditor/
│
├── CodeEditor.java
├── lib/
│   └── rsyntaxtextarea-3.3.4.jar
└── README.md

````

---

## ⚙️ Setup & Run

### 1️⃣ Clone the repository
```bash
git clone https://github.com/your-username/java-code-editor.git
cd java-code-editor
````

---

### 2️⃣ Compile the project

```bash
javac -cp ".;lib/rsyntaxtextarea-3.3.4.jar" CodeEditor.java
```

---

### 3️⃣ Run the editor

```bash
java -cp ".;lib/rsyntaxtextarea-3.3.4.jar" CodeEditor
```

> 💡 On macOS/Linux use `:` instead of `;`

---

## ▶️ Running Code Inside Editor

* Write your Java program in a tab
* Click **Run ▶️**
* Output will be shown in the console panel

---

## ⚠️ Limitations

* Input (`Scanner`) support is limited (no interactive console yet)
* Supports only Java language (for now)
* No debugging feature (yet)

---

## 🔮 Future Improvements

* 🔍 Auto-completion (IntelliSense)
* 🐞 Debugging support
* 🌐 Multi-language support (Python, C++)
* 📦 Build & export as JAR
* 🎯 Better UI (VS Code style tabs)

---

## 👨‍💻 Author

**StarkGuide**
B.Tech IT Student 🚀

---

## ⭐ Show Your Support

If you like this project, give it a ⭐ on GitHub!

---

```

---
