# **Structured 8-Step POC Journey for Voice Record Creation**

Here is a detailed, step-by-step breakdown of the Proof of Concept journey, with interlaced functional and non-functional requirements derived directly from your initial input.

### **Step 1: The user asks the voice assistant what they can do / create records**

* **Journey Description:** The user starts the interaction with a general discovery question to understand the assistant's purpose.  
* **Functional Requirements:**  
  1. The system must provide a way for the user to initiate a voice interaction.  
  2. The system must understand an open-ended, discovery-oriented question like "What can I do?"  
  3. The system must respond verbally with its general purpose (e.g., "I can help you create or edit records with your voice").  
* **Non-Functional Requirements:**  
  1. The system must have a clear indicator to show it is actively listening to the user.  
  2. The interaction must be contained within the Salesforce mobile app after being launched.

### **Step 2: Which records can I create / gives a number of each the user has create access to, recommends a few of the most common ones**

* **Journey Description:** The assistant responds to the user's specific query by listing available record types, providing counts, and offering recommendations.  
* **Functional Requirements:**  
  1. The system must identify the record types the user has permission to create.  
  2. The system must be able to retrieve and state the number of existing records for specific types.  
  3. The system must provide recommendations for common actions (e.g., "most commonly create... Meeting Notes").  
  4. The system must display the list of creatable records on the screen for the user to see.  
* **Non-Functional Requirements:**  
  1. The voice used by the assistant should be less error-prone and more human-like.  
  2. The recommendations provided should be relevant to a salesperson's needs to make data entry seamless and efficient.

### **Step 3: User chooses meeting notes / who with? User is provided most relevant contacts**

* **Journey Description:** The user chooses to create "meeting notes." The system understands this choice and immediately prompts for the most critical related information, "who with," while providing a list of suggested contacts.  
* **Functional Requirements:**  
  1. The system must understand the user's selection of a specific record type ("meeting notes").  
  2. The system must verbally prompt the user for the next piece of information ("who with?").  
  3. The system must search for and display a list of relevant contacts for the user to choose from.  
* **Non-Functional Requirements:**  
  1. The process of searching for and displaying relevant contacts must be fast to maintain a conversational flow.

### **Step 4: speaks a random name off list / Shown a match, progress, shown a list of fields to fill**

* **Journey Description:** The user speaks a name. The system confirms it has found a match, shows progress, and displays the appropriate form fields for the "meeting notes" record.  
* **Functional Requirements:**  
  1. The system must capture the spoken name from the user's speech.  
  2. The system must confirm a match was found and display it on screen.  
  3. The system must present the fields for a meeting note record on the screen to serve as a visual aid for the user.  
* **Non-Functional Requirements:**  
  1. The user interface for voice should be part of the standard record creation screen for a familiar user experience.

### **Step 5: user starts spitballing / fields are filled and data shown, automatically scrolling down**

* **Journey Description:** The user speaks freely about their meeting. As they speak, their words are transcribed, fields are automatically populated, and the screen scrolls to follow the input.  
* **Functional Requirements:**  
  1. The system must transcribe the user's dictated speech into text fields in real-time.  
  2. The system must use LLMs to parse the unstructured speech and populate structured record fields from it.  
  3. The screen must scroll automatically as content is added and fields are filled.  
* **Non-Functional Requirements:**  
  1. All AI capabilities, including transcription and LLM parsing, must operate within the Salesforce trust boundary.  
  2. This functionality requires users to have an appropriate Einstein SKU.

### **Step 6: user asks what more is required / outlines rest of required fields**

* **Journey Description:** The user asks the assistant to check the record for completeness. The assistant responds by outlining which required fields, if any, are still empty.  
* **Functional Requirements:**  
  1. The system must understand a user's direct question about what fields are still required.  
  2. The system must check the current record against its validation rules to find missing required fields.  
  3. The system must verbally state the names of the remaining required fields.  
  4. The system must visually highlight the missing required fields on the screen.  
* **Non-Functional Requirements:**  
  1. The check for required fields must provide an immediate response.

### **Step 7: user fills them in and says they're done / provided a summary of fields and record is saved, shown an idea to create a related records like a task for themselves**

* **Journey Description:** The user finishes data entry and says they are "done." The assistant provides a summary, saves the record, and then suggests creating a related task based on the content of the saved note.  
* **Functional Requirements:**  
  1. The system must understand a verbal command to finish and save the record.  
  2. The system must provide a summary of the information before saving.  
  3. The system must save the record. This can include saving the record as a draft so it can be completed later.  
  4. After the record is saved, the system must present the user with an option to create related records, such as a task.  
* **Non-Functional Requirements:**  
  1. The user must receive a clear confirmation message when the record is successfully saved.

### **Step 8: user acknowledges / assistant says all done, let me know if you need something else**

* **Journey Description:** The user acknowledges the assistant's suggestion, concluding the interaction. The assistant responds with a polite sign-off.  
* **Functional Requirements:**  
  1. The system must understand a final acknowledgment from the user.  
  2. The assistant must deliver a verbal closing statement.  
  3. The voice session must terminate cleanly.  
* **Non-Functional Requirements:**  
  1. The overall interaction should increase mobile app adoption by making core tasks like record management more valuable and engaging.  
  2. The journey must result in improved user satisfaction by reducing frustration with mobile data entry.