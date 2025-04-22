# Advanced Surveyor Mapping Application

A sophisticated geospatial surveying solution engineered for professional surveyors. This Android application integrates advanced MVVM architecture, dynamic Mapbox integration, and robust spatial analytics to deliver precise mapping and data management in challenging field environments.

## Features

- **Advanced MVVM & Dependency Injection:**  
  Designed using a modular MVVM pattern combined with dependency injection (via Hilt/Dagger) to isolate presentation, domain, and data concerns. This approach enables unit testing, enhances code reusability, and simplifies future feature integrations.

- **Mapbox SDK Integration with Custom Overlays:**  
  Utilizes the Mapbox SDK for high-performance vector tile rendering. Implements dynamic multi-layer overlays to visualize geospatial features (lines, points, polygons) with advanced vector styling and real-time interactivity. Supports zoom, pan, and tilt gestures for an immersive mapping experience.

- **Reactive Asynchronous Processing:**  
  Employs Kotlin Coroutines and Flows to handle asynchronous API calls and data streaming. This reactive approach ensures a responsive UI, incorporates robust error handling (with retry and cancellation mechanisms), and optimizes performance under varying network conditions.

- **Offline Geospatial Data Management:**  
  Integrates an SQLite-based GeoPackage system to support offline storage of extensive geospatial datasets. Enables advanced spatial queries, ensuring that survey data remains accessible and accurate even in low or no connectivity scenarios.

- **High-Resolution Sensor Data Integration:**  
  Capable of ingesting and processing high-resolution sensor data in real-time. Leverages sophisticated algorithms for real-time data indexing and spatial analysis, providing up-to-date and actionable insights for field operations.

- **Custom Data Visualization & Analysis Tools:**  
  Includes a suite of built-in tools for spatial analysis, such as heatmaps, contour mapping, and trend analysis. These tools enable surveyors to visualize data patterns, detect anomalies, and make data-driven decisions on the fly.

- **Robust Caching and Data Synchronization:**  
  Implements a smart caching mechanism to minimize redundant network requests and ensure smooth user interactions. Data synchronization strategies maintain consistency between local and remote databases, optimizing both speed and reliability.

- **Dynamic Data Layer Management:**  
  Provides users with the flexibility to toggle between multiple data layers, adjust rendering parameters, and customize visualization settings. This allows for a tailored view of spatial data according to specific survey requirements.

## Architecture

The application is built on a robust MVVM architecture to ensure a clean separation of concerns across three primary layers:

- **Presentation Layer:**  
  Uses ViewModels to manage UI data and state, ensuring that the user interface remains decoupled from business logic. Data binding, LiveData, and Kotlin Flows are used to reactively update the UI in real-time.

- **Domain Layer:**  
  Encapsulates business logic and use cases. This layer defines the core functionality of the application and acts as an intermediary between the presentation and data layers, ensuring consistency and adherence to business rules.

- **Data Layer:**  
  Responsible for data operations, this layer integrates RESTful APIs (via Retrofit) and local storage solutions (using SQLite-based GeoPackage). Repository patterns provide a single source of truth, abstracting the complexities of data retrieval, caching, and persistence.

Additional technical aspects include:
- **Dependency Injection:** Ensures loose coupling and high testability by injecting dependencies via Hilt or Dagger.
  
- **Reactive Programming:** Employs Kotlin Coroutines and Flows for managing asynchronous operations, reducing boilerplate, and handling concurrency with ease.
  
- **Advanced Error Handling:** Implements comprehensive error management, including retries, graceful degradation, and user notifications, to ensure robust and reliable operation.

## Setup & Installation

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/sargunjit591/Mapbox-Layers-App
   
2. **Open in Android Studio:**

- Launch Android Studio.
- Select **File > Open...** and navigate to the cloned repository directory.
- Click **OK** to open the project.

3. **Configure the Project:**

- Ensure you have the latest Android SDK and Build Tools installed.
- Verify that your Gradle version is compatible with the project; the Gradle wrapper is included to simplify this.
- Update any necessary API keys (e.g., Mapbox API key) in the `local.properties` or `gradle.properties` file as per the project instructions.

4. **Sync and Build:**

- Click on **Sync Project with Gradle Files** in the toolbar.
- Once synchronization is complete, build the project using **Build > Make Project**.
- Resolve any dependency warnings or errors as they appear.

5. **Run the Application:**

- Connect an Android emulator or a physical device.
- Click the **Run** button (or press **Shift+F10**) to deploy the application.
- Verify that the application launches and operates as expected.

6. **Optional Setup for Offline Testing:**

- For offline functionality, ensure that sample GeoPackage files are available in the designated local directory.
- Configure any additional local testing parameters as described in the project documentation.

# Dependencies

- Kotlin: Primary programming language.

- XML: UI layout design.

- Mapbox SDK: Advanced mapping and geospatial visualization.

- Kotlin Coroutines & Flows: Asynchronous and reactive programming.

- SQLite (GeoPackage): Offline geospatial data storage and querying.

- Retrofit: For RESTful API communications.

- Hilt/Dagger: Dependency injection framework for modular design.

# Contribution

Contributions are welcome! Please fork the repository and submit pull requests with your improvements.
