# OMNIX

OMNIX is a modular, local-first AI assistant for Android built using Jetpack Compose. The project follows a layered architecture that separates user interface, runtime management, inference, model management, and AI reasoning into independent modules. It is designed to support multiple on-device AI runtimes while maintaining an offline-first and privacy-focused execution model.

The project is currently under active development.

---

## Overview

OMNIX is being developed as an extensible platform for on-device artificial intelligence. The architecture is designed to support:

- Multi-agent reasoning
- Local AI model execution
- Runtime provider abstraction
- Intelligent model management
- Long-term conversation memory
- Vision and document understanding
- Offline-first execution
- Future plugin extensibility

The modular architecture allows new capabilities to be integrated with minimal impact on existing components.

---

## Current Status

**Current Development Phase:** Phase 6 – Runtime Architecture

### Completed

- Modular Android architecture
- Material 3 user interface
- Home dashboard
- Chat interface
- AI Council framework
- Model Manager
- Device capability profiling
- Provider-based inference engine
- Runtime architecture
- Runtime controller
- Runtime lifecycle management
- Runtime state management
- Runtime event system
- Runtime metrics
- Session management
- Model loading infrastructure
- Streaming inference pipeline
- Prompt formatting
- Conversation context management
- Room persistence
- Hilt dependency injection
- Navigation architecture

---

## Architecture

```
Jetpack Compose UI
        │
        ▼
Feature Modules
(Home, Chat, Council, Model Manager)
        │
        ▼
Inference Engine
        │
        ▼
Runtime Controller
        │
        ▼
Runtime Provider
        │
 ┌──────────────┬──────────────┬──────────────┬──────────────┐
 │              │              │              │
Llama.cpp   MediaPipe LLM    Ollama     Simulated Runtime
```

Supporting components:

- AI Council
- Runtime Manager
- Runtime Metrics
- Runtime Events
- Session Manager
- Room Database
- DataStore

---

## Project Structure

```text
app/
core-data/
core-model/
core-ui/
feature-chat/
feature-council/
feature-home/
feature-modelmanager/
```

---

## Modules

| Module | Description |
|---------|-------------|
| `app` | Application entry point and navigation |
| `core-model` | Shared domain models |
| `core-data` | Repository layer, runtime, inference engine, Room database, DataStore and dependency injection |
| `core-ui` | Shared UI components and Material 3 design system |
| `feature-home` | Home dashboard |
| `feature-chat` | Chat experience |
| `feature-council` | Multi-agent reasoning framework |
| `feature-modelmanager` | Device profiling and model management |

---

## Features

### AI

- Multi-agent AI Council
- Provider-based inference engine
- Runtime controller
- Runtime lifecycle management
- Runtime event system
- Runtime metrics
- Runtime session management
- Streaming response pipeline
- Prompt formatting
- Conversation context management

### Runtime

- Runtime manager
- Runtime provider abstraction
- Model loading infrastructure
- Simulated runtime implementation

### Model Management

- Device capability detection
- Hardware profiling
- Model recommendation engine
- Model installation workflow

### Platform

- Jetpack Compose
- Material 3
- Material You support
- Room persistence
- DataStore
- Hilt dependency injection
- Modular architecture

---

## Technology Stack

- Kotlin
- Jetpack Compose
- Material 3
- Hilt
- Room
- DataStore
- Kotlin Coroutines
- Flow

---

## Roadmap

### Phase 1
- Project scaffold
- Core architecture
- Design system

### Phase 2
- Chat experience
- Persistence layer

### Phase 3
- AI Council
- Multi-agent orchestration
- Council visualization

### Phase 4
- Model Manager
- Device profiling
- Model recommendation engine

### Phase 5
- Provider-based inference engine
- Runtime abstraction
- Streaming inference
- Prompt formatting

### Phase 6
- Runtime architecture
- Runtime controller
- Runtime lifecycle
- Runtime metrics
- Runtime event system
- Session management
- Model loading infrastructure

### Phase 7
- Local LLM integration
- Runtime provider selection
- llama.cpp integration
- MediaPipe LLM integration
- Ollama development runtime
- AI Council runtime integration

### Phase 8
- Long-term memory
- Semantic retrieval
- Vision
- Document understanding
- Plugin framework

### Phase 9
- Performance optimization
- Testing
- Production stabilization

---

## Development

```bash
git clone https://github.com/rohanr-10/OMNIX.git
```

Open the project in Android Studio and synchronize the Gradle configuration.

---

## License

This project is currently under active development. A license will be added before the first stable release.
