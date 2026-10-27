# KIU digitalQ

Digital queue management for the **Kampala International University Finance Department**.

Students take a numbered place in line from a kiosk, tellers call and complete tickets from their own terminals, administrators manage counters and the queue, and a waiting-room display shows which counter to go to. Built with Java Swing and the JDK's built-in HTTP server — no external libraries, no database server and no internet connection required.

## Highlights

- Five-digit student registration number → sequential three-digit token (**000–999**)
- Automatic assignment to the ready counter with the fewest students served today
- Live ticket status on the kiosk, with the assigned teller and counter
- Teller availability: **Ready**, **Resting**, **Not Around** and **Closed**
- **Finish** completes a ticket and calls the next waiting student automatically
- Admin console for counters, queue, activity, reset and password changes
- Queue rolls over automatically at **midnight**; tokens start again at **000**
- One server owns the queue; any number of Swing clients can connect over the LAN
- Local TSV persistence, so today's queue survives a restart

## Screens and roles

| Screen | Purpose |
| --- | --- |
| **Launcher** | Choose a role, set the server address and start a local server |
| **Student kiosk** | Enter a registration number with the on-screen keypad or keyboard; view the issued token, queue position, estimated wait and assigned counter |
| **Teller terminal** | Sign in with a PIN, call the current student, finish, mark a no-show, recall, or change availability |
| **Administrator console** | Create and edit counters, override availability, inspect the queue and activity log, reset the queue and change the admin password |
| **Waiting-room display** | Full-screen board with counter tiles, next-up tokens and a live activity ticker (press **F11** for full screen, **Esc** to leave) |

## Requirements

- **JDK 17 or newer** (the project compiles with `--release 17`)
- Windows, macOS or Linux with a graphical desktop
- Optional: **NetBeans 17+** or **Maven 3.8+**
- For LAN mode: all computers on the same network, and port `8765` allowed through the firewall on the server PC

## Quick start (Windows, no Maven)

1. Double-click **`run.bat`**. It builds the project on first run and opens the launcher.
2. The launcher connects to `http://localhost:8765`. If no server is running, it can start one automatically, or press **Start local server**.
3. Open the kiosk, teller, admin and display screens from the launcher.

To build separately:

```bat
build.bat
run.bat
```

To run only the queue server on a dedicated machine:

```bat
server.bat
server.bat 8765 data
```

`build.bat` uses `javac` directly and does not need Maven. It requires Windows PowerShell for the source list.

## Default sign-ins

| Role | Credential | Notes |
| --- | --- | --- |
| Administrator | password `admin123` | Opened from the launcher |
| Teller One | PIN `1111` | Counter 1 |
| Teller Two | PIN `2222` | Counter 2 |
| Teller Three | PIN `3333` | Counter 3 |

**Change the administrator password immediately** from **Admin → Settings**. Edit or delete the seeded tellers from **Admin → Counters** and create the real counters.

## How the queue works

| Rule | Behaviour |
| --- | --- |
| Registration number | Exactly five digits. Registering the same number again returns the same open ticket. |
| Token numbers | Three digits issued in order from `000` to `999` (1000 tickets per day). |
| Assignment | A new student goes to the enabled, **Ready** teller with the fewest students served today; ties go to the teller who has waited longest. If every teller is busy, the student waits in line. |
| Finish | Marks the current ticket **Completed**, counts it for that teller and immediately calls the next waiting student to the same counter. |
| No-show | Marks the current ticket **Missed** and calls the next waiting student. |
| Recall | Re-announces the current ticket on the display and in the activity log. |
| Availability | **Resting** and **Not Around** stop new assignments. **Closed** means the counter is disabled by the administrator. |
| Midnight | The server rolls over automatically. Tokens restart at `000`, and the previous day's file remains in the data folder. |
| Reset | Administrator action. Clears today's tickets, marks tellers ready and starts again at `000`. |

## LAN setup

1. On the server computer, run **`server.bat`** (or use the launcher's **Start local server** button). The console prints the LAN address, for example `http://192.168.1.10:8765`.
2. Allow Java through Windows Defender Firewall on **private networks** when prompted, or add an inbound rule for TCP port `8765`.
3. On each client computer, open the app, enter the server address in the launcher and press **Connect**, for example `http://192.168.1.10:8765`.
4. The client adds `/api` automatically; you only need the host and port.
5. For the waiting-room TV, open the display from a client and press **F11** for full screen.

The server also serves a small status page at `http://<server-ip>:8765/`.

## Open in NetBeans

1. **File → Open Project…**
2. Select the `KIU-digitalQ` folder (the one containing `pom.xml`).
3. Wait for NetBeans to load the Maven project.
4. **Run → Run Project (F6)** starts `com.kiu.digitalq.Main` and opens the launcher.
5. To start the headless server, right-click `ServerMain.java` and choose **Run File (Shift+F6)**.

If NetBeans reports an unsupported source level, open **Project Properties → Sources** and choose **JDK 17** (or newer) plus source/binary format **17**.

## Build with Maven

```bat
mvn clean package
java -jar target\kiu-digitalq.jar
```

Headless server:

```bat
java -cp target\kiu-digitalq.jar com.kiu.digitalq.ServerMain 8765 data
```

Run directly during development:

```bat
mvn compile exec:java
```

## Data and configuration

Everything is stored locally; no cloud service is used.

| File | Contents |
| --- | --- |
| `data/tellers.tsv` | Counters, teller names, PINs and enabled state |
| `data/config.properties` | Administrator password hash (SHA-256) and server settings |
| `data/queue-YYYY-MM-DD.tsv` | Tickets for that day, kept after midnight rollover |

The data folder is created next to the running application (or at the path passed to `ServerMain`). The client remembers the server address in:

```text
%USERPROFILE%\.kiu-digitalq\client.properties
```

Back up the `data` folder regularly. Keep it private: it contains teller PINs.

## Project structure

```text
KIU-digitalQ/
├─ src/main/java/com/kiu/digitalq/
│  ├─ Main.java              desktop entry point (launcher)
│  ├─ ServerMain.java        headless server entry point
│  ├─ client/                Swing screens, polling, HTTP client
│  ├─ server/                queue engine, persistence, HTTP routes
│  ├─ model/                 ticket, teller and status model
│  ├─ json/                  small dependency-free JSON reader/writer
│  └─ ui/                    theme, cards, buttons and Swing helpers
├─ build.bat                 compile with javac (no Maven needed)
├─ run.bat                   build if needed, then open the launcher
├─ server.bat                run the queue server only
├─ pom.xml                   NetBeans / Maven project
└─ data/                     created at runtime, not committed
```

## Troubleshooting

**"Port 8765 is already in use"**
Another server is already running on this computer. Use the existing server, or pass a different port to `server.bat`, for example `server.bat 8766 data`.

**Clients cannot connect over the LAN**
Confirm both computers are on the same network, use the server's actual LAN IP (not `localhost`), and allow TCP `8765` through the server's firewall.

**"Build failed. Make sure a JDK 17 or newer is installed"**
Install a JDK 17+ and confirm `javac -version` works in a new Command Prompt. NetBeans users can build with Maven instead.

**The queue did not reset at midnight**
The server checks the date on every API call and also runs a scheduled rollover. Keep the server running; a restart after midnight also starts a fresh day.

**I forgot the administrator password**
Stop the server and replace `data/config.properties` with a fresh copy (or delete the file to restore the default `admin123`), then restart. This resets the administrator password and server settings but keeps counters and today's tickets.

## Notes

- Tokens and ticket history are per day. The display and kiosk show today's queue only.
- The server is intended for a trusted staff network. Do not expose port `8765` directly to the internet.
- The application is dependency-free: only the JDK is required at runtime.
