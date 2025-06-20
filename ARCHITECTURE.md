# **Minimalist Android Architecture for Voice POC**

This document outlines a lean, modern Android architecture for the Voice Record Creation Proof of Concept (POC). The primary goal is to **minimize initial complexity** to focus on the core user experience: voice interaction and Salesforce data manipulation.

To achieve this, we are intentionally deferring performance optimizations like caching. This architecture prioritizes clarity, testability, and speed of initial development using standard Jetpack components.

### **High-Level Architecture Diagram**

The application is divided into three logical layers: **UI**, **Data**, and **AI/Voice**. This separation ensures that concerns are well-defined and components are loosely coupled.

graph TD  
    subgraph UI Layer  
        A\[VoiceScreen (Composable)\] \--\> B(VoiceViewModel)  
    end

    subgraph Data Layer  
        C\[SalesforceRepository\] \--\> D\[Salesforce Mobile SDK\]  
    end

    subgraph AI/Voice Layer  
        G\[VoiceProcessor\] \--\> H\[Android SpeechRecognizer\]  
        G \--\> I\[Einstein STT/LLM APIs\]  
    end

    B \--\> C  
    B \--\> G

    style A fill:\#cde4ff,stroke:\#6a8eae,stroke-width:2px  
    style B fill:\#cde4ff,stroke:\#6a8eae,stroke-width:2px

    style C fill:\#d5e8d4,stroke:\#82b366,stroke-width:2px  
    style D fill:\#d5e8d4,stroke:\#82b366,stroke-width:2px

    style G fill:\#fff2cc,stroke:\#d6b656,stroke-width:2px  
    style H fill:\#fff2cc,stroke:\#d6b656,stroke-width:2px  
    style I fill:\#fff2cc,stroke:\#d6b656,stroke-width:2px

### **Component Breakdown**

#### **1\. UI Layer**

This layer is responsible for everything the user sees and interacts with. It's built with Jetpack Compose.

* **VoiceScreen (Composable)**: This is the "View". It is a stateless, declarative UI component.  
  * **Responsibilities**:  
    * Renders the UI based on the current state provided by the ViewModel.  
    * Displays transcribed text, assistant messages, and UI controls (e.g., microphone button).  
    * Captures user actions (like a button tap) and passes them as events to the ViewModel. It contains no business logic.  
* **VoiceViewModel**: This is the brain of the UI. It survives configuration changes (like screen rotation).  
  * **Responsibilities**:  
    * Holds the UI's state (e.g., StateFlow\<MyScreenState\>) and exposes it to the VoiceScreen.  
    * Receives events from the VoiceScreen and makes decisions.  
    * Delegates all business logic, data fetching, and voice processing to the appropriate repositories and processors. It orchestrates the work but doesn't perform it.

#### **2\. Data Layer**

This layer handles all communication with Salesforce. For this simplified POC, it consists of a single repository that directly handles network communication.

* **SalesforceRepository**: Acts as the single source of truth for all Salesforce data.  
  * **Responsibilities**:  
    * Uses the **Salesforce Mobile SDK** to execute network calls.  
    * Handles authentication and session management via the SDK.  
    * Exposes simple functions for the ViewModel to call (e.g., getCreatableObjects(), saveMeetingNote(noteData)).  
    * Maps the complex JSON responses from the Salesforce APIs into clean Kotlin data classes that the rest of the app can use.  
* **Salesforce Mobile SDK**: This is the low-level tool provided by Salesforce that handles the raw HTTP requests, OAuth flow, and security. The repository abstracts its complexity away from the rest of the app.

#### **3\. AI/Voice Layer**

This layer encapsulates all logic related to voice processing, from capturing audio to interpreting its meaning.

* **VoiceProcessor**: A dedicated class to manage the entire voice interaction lifecycle.  
  * **Responsibilities**:  
    * Uses the native **Android.SpeechRecognizer** for simple, on-device recognition of commands (e.g., "create meeting note"). This is fast and works offline for basic phrases.  
    * For complex, free-form dictation ("spitballing"), it sends audio or transcribed text to the **Einstein STT/LLM APIs**.  
    * Receives the structured data (e.g., a JSON object of a pre-filled Meeting Note) back from the Einstein LLM.  
    * Provides the final, processed output to the ViewModel.

### **Simplified Data Flow (No Cache)**

Without a cache, the data flow is very direct. Every request for data results in a network call.

sequenceDiagram  
    participant ViewModel  
    participant SalesforceRepository  
    participant SalesforceSDK (Network)

    ViewModel-\>\>SalesforceRepository: getCreatableObjects()

    activate SalesforceRepository  
    Note over SalesforceRepository: No cache to check. Go directly to network.  
    SalesforceRepository-\>\>SalesforceSDK (Network): fetchObjectsFromAPI()  
    activate SalesforceSDK  
    SalesforceSDK (Network)--\>\>SalesforceRepository: returns freshData  
    deactivate SalesforceSDK

    SalesforceRepository--\>\>ViewModel: returns freshData  
    deactivate SalesforceRepository

1. **Request**: The ViewModel needs data and calls a function on the SalesforceRepository.  
2. **Network Call**: The repository immediately uses the Salesforce SDK to make a network call to the appropriate Salesforce API endpoint.  
3. **Response**: The SDK returns the data from the server. The repository might clean it up and then returns the final data to the ViewModel, which then updates the UI state.

### **Dependency Management: Keeping it Simple**

For this POC, we will avoid complex Dependency Injection frameworks like Hilt. Instead, we'll use **Manual Dependency Injection**, which is simply the practice of creating and passing our dependencies explicitly. This reduces boilerplate and keeps the setup straightforward.

**Example in MainActivity.kt:**

class MainActivity : ComponentActivity() {  
    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)

        // 1\. Manually create your single-instance dependencies.  
        val salesforceRepository \= SalesforceRepository() // It uses the SDK internally  
        val voiceProcessor \= VoiceProcessor()

        // 2\. Create a factory to pass these dependencies to the ViewModel.  
        val viewModelFactory \= VoiceViewModelFactory(salesforceRepository, voiceProcessor)  
        val voiceViewModel: VoiceViewModel by viewModels { viewModelFactory }

        setContent {  
            // 3\. Provide the ViewModel to your top-level Composable.  
            YourAppTheme {  
                VoiceScreen(viewModel \= voiceViewModel)  
            }  
        }  
    }  
}

This approach is lean, easy to follow, and perfectly suited for building the first version of the application.