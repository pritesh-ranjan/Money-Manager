# Money Manager

> **Your finances, strictly private and beautifully organized.**  
> An offline-first personal finance tracker and intelligent ledger built with a modern Samsung One UI aesthetic.

---

## Overview

**Money Manager** is an intuitive, privacy-first personal expense tracker and financial ledger for Android. Designed around Samsung’s One UI design philosophy, it delivers a clutter-free, tactile experience with squircle cards, bold collapsible headers, vibrant category accent cues, and a pure AMOLED pitch-black dark mode.

Unlike cloud-based budget apps, Money Manager is **100% offline**. It has no internet permissions, zero trackers, and zero external servers—your financial records never leave your device.

---

## Features

### 📅 Dashboard & Daily Ledger
* **7-Day Interactive Date Strip**: Quickly glance at your daily cashflow. Tap any day to isolate transactions, jump to today, or switch to the entire month.
* **Monthly Cashflow Summary**: Real-time totals for income, expenses, and net balance, accompanied by a dynamic visual cashflow bar.
* **Flexible View Modes**: Switch seamlessly between **Daily** grouped view, **Calendar** summary, **Monthly** totals, and **Notes** view.
* **Localized Number Formatting**: Native Indian Numbering System formatting (Lakhs and Crores, e.g., `₹1,12,342.50`) for clear, familiar reading.
* **Visual Category Accents**: Elegant vertical color bars next to each transaction provide instant recognition of spending categories.

---

### ⚡ Rapid Transaction Entry
* **Quick-Add Sheet**: Streamlined input with a large amount field, customizable currency preview, and instant transaction-type toggle (**Expense**, **Income**, **Transfer**, **Balance Adjustment**).
* **Predictive Note Suggestion Engine**: As you type a payee or note, Money Manager suggests matching past entries with historical frequency and automatically pre-selects the previously used category and account.
* **Contextual Tags**: Organize and group transactions across categories with custom tags (e.g., `#vacation`, `#work`, `#party`).
* **Continuous Logging ("Save & Continue")**: Add multiple transactions in a row without having to reopen the sheet.
* **Precise Timestamps**: Backdate or schedule transactions using native date and time pickers.

---

### 🔔 Persistent Quick-Add Notification
* **Always-On Convenience**: An unobtrusive, ongoing status bar notification keeps quick financial tracking just a swipe away.
* **One-Tap Actions**: Dedicated buttons for **Add Expense**, **Add Income**, and **Transfer** let you record a spend directly without launching the full app first.

---

### 💳 Credit Card Statement & Billing Engine
* **Cycle & Cutoff Tracking**: Define statement generation days (1–31) and payment due days (1–31) for each credit card.
* **Smart Bill Breakdown**: Automatically calculates:
  * Billed statement amount
  * Payments made since statement cutoff
  * Remaining statement balance due
  * Unbilled spends in the current cycle
  * Total outstanding card balance
* **Credit Limit & Utilization**: Visual progress bars display your credit utilization percentage with proactive warning indicators for high usage.
* **Due Date Alerts**: Real-time cycle statuses (**Paid**, **Due Soon**, **Overdue**, or **No Bill**).
* **One-Tap Bill Settlement**: Pay your credit card bill directly from any linked savings or cash account. Use quick-snap presets (*"Statement Due"* or *"Total Outstanding"*) or the precision slider to record settlements instantly.

---

### 🏦 Multi-Account & Net Worth Management
* **Diverse Account Types**: Support for Savings Accounts, Cash Wallets, Credit Cards, Linked UPI/Debit Accounts, Investments (FD/PPF/SGB), Equity & Stocks, Mutual Funds, and Loans.
* **Live Net Worth Tracking**: Continuously aggregates total assets and liabilities into an accurate net worth figure.
* **Static Revaluation (Balance Adjustments)**: Easily reconcile your recorded ledger with your real-world bank balance in one step—Money Manager automatically records the difference as an adjustment.
* **Multi-Currency & Custom Rewards**:
  * Track foreign currencies (USD, EUR, GBP, etc.) with custom exchange rates to your base currency.
  * Support for loyalty points, crypto, or miles (e.g., Reward Points, Scapia Coins) with configurable decimal precision.

---

### 📊 Financial Wellbeing & Analytics
* **Interactive Donut & Ring Charts**: Visual breakdown of your top spending categories for any period.
* **7-Day Stacked Bar Chart**: Daily expense patterns visualized with benchmark guidelines and highlighted active days.
* **Multi-Period Filtering**: Analyze spending by **Weekly**, **Monthly**, **Annually**, or **All-Time** windows.
* **Category Drill-Down**: Tap any category to inspect a breakdown of subcategories, transaction counts, and historical spends.

---

### ✨ Monthly Money Story
* **Intelligent Month-Over-Month Insights**: Pull down on the statistics screen to reveal a personalized financial story comparing your performance to the previous month (including equivalent partial-month comparisons for in-progress months).
* **Deep Insight Cards**:
  * **Expense Trends**: Percentage change in living expenses.
  * **Category Spends**: Highlights your most significant category increase or decrease.
  * **Income Growth**: Tracks month-over-month earning fluctuations.
  * **Savings Rate**: Calculates net income retained after living expenses.
  * **Investment Rate**: Measures contributions toward investments, equity, and mutual funds.
* **Drill-Down Inspection**: Tap any story insight to review all transactions contributing to that metric.

---

### 🔒 Vault Security & Privacy
* **100% Offline-First**: Network and internet access permissions are completely removed from the app manifest (`INTERNET` blocked). Your financial data never leaves your device.
* **PIN & Biometric Lock**: Secure your vault with a 4-digit PIN, fingerprint, or facial recognition.
* **PBKDF2-HMAC-SHA256 Cryptography**: Master PINs are salted and hashed using cryptographic industry standards.
* **Brute-Force Protection**: Automatic lockouts after repeated failed PIN attempts.
* **Privacy Screen Shield (`FLAG_SECURE`)**: Automatically obscures app previews in Android's recent apps switcher and protects against unauthorized screenshots.

---

### 💾 Backup, Restore & Legacy Migration
* **Standalone SQLite Backups**: Export your entire ledger (accounts, categories, tags, transactions) into a standard, portable `.sqlite` file at any time.
* **One-Tap Restore**: Restore your backup onto any device with atomic database integrity.
* **Legacy Backup Migration**: Directly import and migrate backups from older Money Manager schemas (`.mmbak` files and legacy tables including `INOUTCOME`, `ASSETS`, `ZCATEGORY`, `TX_TAG`, and `CURRENCY`).

---

### 🎨 Themes & Customization
* **Samsung One UI 9 Styling**: Rounded squircle containers, hairline dividers, pill segment controls, and soft interactive states.
* **AMOLED Pitch Black**: Pure `#000000` dark mode optimized for OLED screens to conserve battery life, alongside a clean light mode and system schedule following.
* **Custom Categories & Icons**: Two-tier category hierarchy (Parent and Subcategories) with emoji iconography.

---

## Tech Stack

* **Platform**: Android (Min SDK 26 / Target SDK 36)
* **Language**: Kotlin
* **UI Framework**: Jetpack Compose with Material 3 (Custom One UI Design System)
* **Local Database**: SQLite via Android Jetpack Room
* **Architecture**: Clean Architecture + MVVM (StateFlow, Coroutines, Repository Pattern)
* **Security & Auth**: Android Biometrics API & PBKDF2-HMAC-SHA256
* **Build System**: Gradle Version Catalog (`libs.versions.toml`) with KSP

---

## Getting Started

### Prerequisites
* Android Studio Ladybug (or newer)
* JDK 17
* Android SDK 36

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/pritesh-ranjan/Money-Manager.git
   cd "Money-Manager"
   ```
2. Open the project in Android Studio.
3. Sync Gradle and build the project:
   ```bash
   ./gradlew assembleDebug
   ```
4. Run on a connected Android device or emulator.
