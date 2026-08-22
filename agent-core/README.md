# SkyPulse AI Weather Agent

## Agent Core

This module introduces the AI Agent layer for SkyPulse.

Architecture:

```
User
 |
Weather Agent Runtime
 |
+----------------+
| Tool Registry  |
+----------------+
 | Weather Tool
 | AQI Tool
 | Location Tool
 |
LLM Gateway
```

Planned components:

- AgentRuntime
- AgentPlanner
- ToolRegistry
- MemoryStore
- PromptManager
- LLMClient
