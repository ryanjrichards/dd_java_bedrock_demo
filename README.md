# DD Java Bedrock Demo

This project demonstrates how to use the AWS Bedrock Runtime to invoke a model with response streaming and send trace spans to Datadog.

## Prerequisites

- Java 11 or higher
- Maven
- AWS credentials configured
- Datadog API key

## Setup

1. Clone the repository:
    ```sh
    git clone https://github.com/yourusername/dd_java_bedrock_demo.git
    cd dd_java_bedrock_demo
    ```

2. Create a `.env` file in the root directory and add your Datadog API key:
    ```sh
    touch .env
    echo "DD_API_KEY=your_datadog_api_key" >> .env
    ```

3. Build the project using Maven:
    ```sh
    mvn clean install
    ```

## Running the Application

To run the application, execute the following command:
```sh
mvn exec:java -Dexec.mainClass="com.datadoghq.InvokeModelWithResponseStream"
```

## Usage

1. The application will prompt you to enter a message.
2. Enter your message and press Enter.
3. The application will invoke the model with the provided message and print the response.
4. The application will also send trace spans to Datadog.

To exit the application, type `exit` and press Enter.

## Testing

To run the tests, execute the following command:
```sh
mvn test
```

## Project Structure

- `src/main/java/com/datadoghq/InvokeModelWithResponseStream.java`: Main class that handles invoking the model and sending trace spans.
- `src/test/java/com/datadoghq/AppTest.java`: Test class for the application.
- `.gitignore`: Git ignore file.
- `pom.xml`: Maven project file.

