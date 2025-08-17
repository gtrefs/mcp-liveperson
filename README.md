# MCP LivePerson Server

This project provides an MCP server for LivePerson built with Spring Boot.

## Configuration

Copy `.env.example` to `.env` and fill in the required environment variables:

```bash
cp .env.example .env
# edit .env with your LivePerson credentials and domains
```

The `.env` file supplies the values for variables such as `LP_ACCOUNT_ID`, `LP_CLIENT_ID`, and `LP_CLIENT_SECRET`. Docker 
Compose reads this file automatically, and you can source it in your shell or IDE to run the server locally.

## Run with Docker

Ensure Docker and Docker Compose are installed, then start the server:

```bash
docker compose -f docker-compose.image.yml up
```

The server will listen on port `8080`.

## Run from the IDE or Command Line

1. Make the variables in `.env` available in your environment. For example:

   ```bash
   export $(grep -v '^#' .env | xargs)
   ```

2. Run the Spring Boot application:

   ```bash
   ./mvnw spring-boot:run
   ```

   Alternatively, run the `McpServerApplication` class from your IDE with the same environment variables configured in the run configuration.


## Connect with Gemini

Once the server is running, register it with Gemini using:

```bash
gemini mcp add -t=sse --trust=true --timeout=30000 LivePerson http://localhost:8080/mcp/sse
```

## LivePerson conversation with Gemini

1. Start a chat session:
   ```bash
   gemini
   ```

2. (Optional) In the chat, list available MCP servers, tools, and prompts to verify your LivePerson server is connected:
   ```
   /mcp list
   ```

3. Run the prompt exposed by the LivePerson MCP server:
   ```
   /conversation_flow
   ```

4. Continue the conversation as usual; the prompt’s output will appear in the chat.
