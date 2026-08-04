"""AgentSky FastAPI 服务 — HTTP API 包装多Agent写作工作流"""

import sys
import json
import io
from contextlib import redirect_stdout

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from state import make_initial_state
from graph.workflow import create_workflow

app = FastAPI(title="AgentSky API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class CreateRequest(BaseModel):
    idea: str


class CreateResponse(BaseModel):
    success: bool
    logs: list[str]
    result: dict
    error: str = ""
    token_usage: dict = {}


@app.post("/api/create", response_model=CreateResponse)
def create_novel(req: CreateRequest):
    """接收创作灵感，运行完整多Agent创作流程"""
    logs = []

    def log(msg: str):
        logs.append(msg)

    idea = req.idea.strip()
    if not idea:
        return CreateResponse(success=False, logs=["[ERROR] idea为空"], result={}, error="创作灵感不能为空")
    if len(idea) > 2000:
        return CreateResponse(success=False, logs=[f"[ERROR] idea过长({len(idea)}字符)"], result={}, error="创作灵感不能超过2000字符")

    # 重置 Token 追踪器
    from llm.config import reset_tracker, get_tracker
    reset_tracker()

    log(f"[START] idea={idea[:100]}")

    try:
        from llm.config import get_model
        model = get_model()
        log("[INIT] model ready (deepseek-chat)")
    except Exception as e:
        return CreateResponse(success=False, logs=logs, result={}, error=f"模型初始化失败: {e}")

    try:
        workflow = create_workflow(model)
        log("[INIT] workflow compiled")
    except Exception as e:
        return CreateResponse(success=False, logs=logs, result={}, error=f"工作流编译失败: {e}")

    state = make_initial_state(idea)

    try:
        # 捕获 stdout 中的 agent 日志
        buf = io.StringIO()
        with redirect_stdout(buf):
            result = workflow.invoke(state, config={"recursion_limit": 60})

        # 提取 agent 日志
        for line in buf.getvalue().split("\n"):
            line = line.strip()
            if line:
                logs.append(line)

        # 汇总结果
        settings = result.get("world_settings", [])
        characters = result.get("characters", [])
        plot = result.get("plot_outline", [])
        draft = result.get("current_draft", "")
        review_issues = result.get("review_issues", [])
        review_passed = result.get("review_passed", False)
        review_round = result.get("review_round", 0)

        tracker = get_tracker()
        log(f"[DONE] 设定:{len(settings)}条 人物:{len(characters)}个 大纲:{len(plot)}章 正文:{len(draft)}字 审核:{'PASS' if review_passed else 'ISSUES'}(r{review_round})")
        log(f"[COST] {tracker.summary()}")

        # 序列化结果
        serializable = {
            "phase": result.get("phase", ""),
            "settings_count": len(settings),
            "characters_count": len(characters),
            "plot_count": len(plot),
            "draft_length": len(draft),
            "review_passed": review_passed,
            "review_round": review_round,
            "issues_count": len(review_issues),
            "world_settings": _serialize_settings(settings),
            "characters": _serialize_characters(characters),
            "plot_outline": _serialize_plot(plot),
            "current_draft": draft,
            "review_issues": review_issues,
        }

        return CreateResponse(success=True, logs=logs, result=serializable, token_usage=tracker.to_dict())

    except Exception as e:
        import traceback
        log(f"[ERROR] {e}")
        log(traceback.format_exc())
        return CreateResponse(success=False, logs=logs, result={}, error=str(e), token_usage=get_tracker().to_dict())


def _serialize_settings(settings: list) -> list:
    return [dict(s) if hasattr(s, 'items') else s for s in settings]


def _serialize_characters(chars: list) -> list:
    result = []
    for c in chars:
        d = dict(c) if hasattr(c, 'items') else c
        if "relationships" in d and isinstance(d["relationships"], str):
            try:
                d["relationships"] = json.loads(d["relationships"])
            except json.JSONDecodeError:
                pass
        result.append(d)
    return result


def _serialize_plot(plot: list) -> list:
    return [dict(p) if hasattr(p, 'items') else p for p in plot]


@app.get("/api/health")
def health_check():
    """快速测试 API 连通性"""
    try:
        from llm.config import get_model
        model = get_model()
        from langchain_core.messages import HumanMessage
        resp = model.invoke([HumanMessage(content="hi")], config={"timeout": 15})
        return {"status": "ok", "model": "deepseek-chat", "response": resp.content[:50]}
    except Exception as e:
        return {"status": "error", "detail": str(e)}


app.mount("/", StaticFiles(directory="static", html=True), name="static")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8765)
