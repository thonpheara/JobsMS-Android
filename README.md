# Job Management System (Android App)

An enterprise-grade native Android mobile application for managing **Internships & Job Opportunities**, built with **Java** and **Material Design 3**. This application connects seamlessly with the Laravel RESTful API backend to serve both **Students / Job Seekers** and **Employer Companies**.

> 🔗 **Web Application & REST API Repository**: [thonpheara/JobsMS](https://github.com/thonpheara/JobsMS)

---

## ✨ Key Features

### 🎓 Student / Candidate Portal
- **Dashboard & Analytics**: View application progress, upcoming interview alerts, and personalized job/internship recommendations.
- **Explore Opportunities**: Search and filter internships, full-time, part-time, and contract positions by category, work mode (`On-Site`, `Remote`, `Hybrid`), and compensation.
- **One-Tap Application**: Apply directly with an uploaded PDF Resume/CV and custom Cover Letter.
- **Application & Interview Tracking**: Track real-time status updates (`Pending`, `Under Review`, `Shortlisted`, `Interviewed`, `Accepted`, `Rejected`), confirm interview attendance, or request interview rescheduling.
- **Resume & Profile Management**: Manage academic credentials (`GPA`, `Major`, `Department`, `Cohort Year`, `Student ID`), technical skills chips, and upload/download PDF resumes.
- **Real-Time Messaging & Notifications**: Direct chat with company recruiters and instant push/in-app notifications.

### 🏢 Company / Recruiter Portal
- **Company Dashboard**: Overview of active job/internship postings, candidate pipeline metrics with live placement rate, verification status alerts, upcoming interviews preview, reschedule request indicators, and quick access to **Post Opportunity**, **Interviews**, and **Hiring Reports**.
- **Job Postings Management**: Create, edit, duplicate, close/reopen, and submit job or internship postings (`Draft`, `Pending Review`, `Approved`, `Rejected`, `Closed`) with full support for experience levels, duration, salary ranges, and work modes.
- **Job Details & Pipeline Distribution**: View applicant status breakdown, collapsible role overview, and quick actions.
- **Applicants Management**:
  - Filter candidates by status, opportunity type (`🎓 Internships` / `💼 Jobs`), specific role, or interview reschedule requests.
  - Sort candidates by **Newest Applied** or **★ Highest GPA First**.
  - **Clean Selection Mode & Bulk Actions**: Long-press any candidate card to open the floating Bulk Action Bar and batch-update candidate statuses (`Review`, `Shortlist`, `Accept`, `Reject`) with confirmation dialogs for final decisions.
  - **Smart Card Actions**: Dynamic status-aware action buttons (`Review`, `Schedule`, `Interview`, `Details`) and quick-chat access.
- **Applicant Profile**: View full academic & contact credentials, collapsible technical skills, cover letters, internal evaluation notes, and view/download official PDF resumes.
- **Direct Messaging & Chat**:
  - Filter conversations by `All`, `Unread`, `Interviewing`, and `Hired` with status badges.
  - Role-aware chat header with online indicator, tap-to-view profile, and in-chat candidacy stage changer.
  - Pinned interview context banner with one-tap `Join Call`, Quick Reply templates, date separators, clickable URLs, and read receipts (`Sent ✓` / `Read ✓✓`).
- **Interview Scheduling & Management**: Schedule in-person or online video interviews (Google Meet / Zoom), handle candidate reschedule requests, and mark interview outcomes.
- **Hiring Reports**: Filterable recruitment analytics and conversion rate tracking.
- **Company Profile & Branding**: Upload company logo and cover banner, track 9-point profile completeness with clickable missing-item chips, view verification status alerts, and update account password with a live security checklist.

---

## 🛠 Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Java (JDK 11) |
| **UI Framework** | Android XML Layouts, Material Design 3 (`com.google.android.material`), Light & Dark Mode support |
| **Networking** | Retrofit 2, OkHttp 3 (with Logging & Bearer Token Auth Interceptor) |
| **JSON Parsing** | Google Gson |
| **Authentication** | Laravel Sanctum Bearer Token (`SessionManager` with `SharedPreferences`) |
| **Backend API** | Laravel 11 RESTful API ([thonpheara/InternshipMS](https://github.com/thonpheara/InternshipMS)) |

---

## 🚀 Getting Started & Installation

### 1. Prerequisites
- **Android Studio** (Ladybug / Koala or newer recommended)
- **Android SDK** (Min SDK 24 / Target SDK 35)
- Running instance of the **Laravel Backend API** ([thonpheara/InternshipMS](https://github.com/thonpheara/InternshipMS))

### 2. Clone the Repository
```bash
git clone https://github.com/thonpheara/JobsMS-Android.git
cd JobsMS-Android
```

### 3. Configure the Backend API URL
Open `app/src/main/java/com/example/internshipms/network/ApiClient.java` and verify `BASE_URL`:

```java
// Default for Android Emulator connecting to local Laravel server (php artisan serve):
public static final String BASE_URL = "http://10.0.2.2:8000/api/";
```
*(If testing on a physical Android device over Wi-Fi, replace `10.0.2.2` with your computer's local IPv4 address, e.g., `http://192.168.1.100:8000/api/`).*

### 4. Build & Run
1. Open the project folder in **Android Studio**.
2. Wait for **Gradle Sync** to finish.
3. Start your Laravel backend server:
   ```bash
   php artisan serve
   ```
4. Click **Run ▶** in Android Studio to launch the app on an emulator or physical device.

---

## 📂 Project Structure

```text
app/src/main/
├── java/com/example/internshipms/
│   ├── network/             # ApiClient, ApiService, AuthInterceptor, AvatarLoader, Json utilities
│   ├── repository/          # AuthRepository, StudentRepository, CompanyRepository, MessageRepository
│   ├── session/             # SessionManager (token & user role persistence)
│   ├── view/                # Custom views (PullRefreshScrollView)
│   ├── Company*Activity.java# Company Portal screens (Dashboard, Jobs, Applicants, Interviews, Reports, Profile)
│   ├── Student*Activity.java# Student Portal screens (Dashboard, Explore, Applications, Resume, Profile)
│   └── ChatActivity.java    # Direct messaging between companies and applicants
└── res/
    ├── layout/              # Material 3 XML layouts for Activities, Cards, and BottomSheets
    ├── drawable/            # Vector icons and custom UI backgrounds
    ├── values/              # Light theme colors, strings, and styles
    └── values-night/        # Dark mode theme overrides
```

---

## 👤 Author

- **Thon Pheara** — [@thonpheara](https://github.com/thonpheara)
