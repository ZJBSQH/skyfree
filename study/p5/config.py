"""LLM 配置 — 统一初始化入口"""

import os
from dotenv import load_dotenv
from langchain.chat_models import init_chat_model

load_dotenv()


def get_model():
    return init_chat_model(
        model=os.getenv("LLM_MODEL", "qwen2.5:3b"),
        model_provider="openai",
        base_url=os.getenv("LLM_BASE_URL", "http://localhost:11434/v1"),
        api_key=os.getenv("LLM_API_KEY", "ollama"),
        temperature=0.7,
    )
