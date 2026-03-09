# Blackbird Android Application Implementation Plan

This document outlines the architecture and approach for developing the "Blackbird" Android application using Kotlin Native and Jetpack Compose.

## Proposed Changes

We are pivoting to a purely native **Kotlin Android** project to ensure maximum performance and tight platform integration. 

### 1. Technology Stack
* **UI Framework:** Jetpack Compose (for declarative and responsive programmatic layouts).
* **Networking:** Ktor or native Android WebRTC wrappers for Peer-to-Peer gameplay.
* **Architecture:** MVVM (Model-View-ViewModel) using Coroutines and StateFlow for robust reactive state management.
* **Animations:** Compose Animation APIs for realistic card interactions (`Animatable`, `updateTransition`).

### 2. Peer-to-Peer Networking & Voice Chat
* **Host Mode:** The user starts a game, opening a localized Ktor WebSocket server, turning the device into the active server. It collects connection info (IP address, Port).
* **Join Mode (Smart Links):** The Host generates a deep link where their connection information is converted to JSON and **Base64 encoded** to create a clean, shareable URL (e.g., `blackbird://join?payload=eyJpcCI6IjE5Mi4xNjguMS4xMDAiLCJwb3J0Ijo4MDgwfQ==`). This hides the routing details and looks purely like a game link.
* **Game Server (State & Audio):** The Host handles two types of data over the WebSocket connection:
  * **Text Frames (JSON):** Trick calculations, scoring, card throws, bidding, and Trump selections.
  * **Binary Frames (Audio):** Real-time UDP-style voice data. Users will have a Push-to-Talk (PTT) mic icon. When held, `AudioRecord` captures PCM data, sends it over the socket, and the Host broadcasts the binary shards to other peers who play it via `AudioTrack`.

### 3. Game Engine
* **Deck & Rules:** Standard Blackbird/Rook rules, a 57-card deck (4 colors 1-14 + 1 Blackbird).
* **Scoreboard:** Tracks trick points during the play phase and updates global match scores per round.

### 4. Hardware-Accelerated AI (Monte Carlo)
* **AI Players:** Open seats at the table are automatically filled by AI players. If a human joins via a deep link, they can seamlessly take over an AI seat.
* **Algorithm:** Monte Carlo Tree Search (MCTS) combined with a highly optimized neural network value/policy function, allowing the AI to "think" faster and make strategic bidding/playing decisions.
* **Hardware Acceleration:** The AI engine will utilize **TensorFlow Lite (TFLite)** for Android. We will configure TFLite to prefer the **NNAPI Delegate** (to leverage the device's NPU) and fallback to the **GPU Delegate** or XNNPACK (CPU) if the NPU is unavailable. This ensures the simulations run extremely fast without locking up the UI thread.
* **Concurrency:** The MCTS rollout phases will run inside Kotlin Coroutines (`Dispatchers.Default`), batching evaluations through the hardware-accelerated TFLite model.

### 5. User Interface & Design
* **Responsive Layout:**
  * Support for seamless configuration changes (Portrait vs. Landscape) built heavily around Compose modifiers and weight assignments.
  * *Landscape:* Table layout with opponents equally spaced; user hand displayed optimally across the wide bottom.
  * *Portrait:* Tighter hand stacking at bottom, and physically condensed top/left/right opponents.
* **Voice Chat UI:** A persistent Push-to-Talk (PTT) Microphone button positioned ergonomically in the bottom corner (above or next to the player's hand) for easy thumb access during active play.
* **Card Animations:**
  * Custom Compose modifiers taking an `offset(x, y)` and `rotationZ()` to dynamically animate cards from a player's hand/edge to the center of the table. The landing position will be skewed slightly toward the throwing player.
* **Theming:** A dark, modern polished Material 3 theme mimicking a premium felt table environment.

## Verification Plan

### Automated Tests
* Standard JUnit/MockK tests for evaluating internal rule processing (e.g., scoring logic, bidding logic).

### Manual Verification
* Using multiple instances of an Android Emulator to simulate Host/Join P2P intent sharing.
* Evaluating Compose animations for frame skips during complex rendering phases.
