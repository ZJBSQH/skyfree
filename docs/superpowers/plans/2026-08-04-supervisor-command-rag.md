# Supervisor + Command 路由重构 & RAG 向量库接入 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 LangGraph 工作流改为 supervisor 模式（节点用 `Command(goto=...)` 路由），并把 FAISS RAG 写作资料库接入 writer/setting/character/plot 四个 Agent。

**Architecture:** 删除 `route_supervisor`/`route_reviewer` 条件边，supervisor/reviewer 节点直接返回 `Command(goto=...)` 决定下一跳；删除 state 中 `next_action`/`task_queue`；`RagStore` 从 `data/reference/*.txt` 预载素材，`get_store()` 单例供四个 Agent 在 invoke 内自检索并注入 prompt。

**Tech Stack:** langgraph 1.2.10、faiss-cpu、sentence-transformers、pytest。

## Global Constraints

- 测试命令统一从 `AgentSky/` 目录执行：`venv/Scripts/python.exe -m pytest tests/ -v`（`python -m pytest` 会把 cwd 加入 sys.path，`from state import ...` 才能解析）
- supervisor 决策值直接使用节点名：`setting` / `character` / `plot` / `writer` / `finish`；`finish` 在节点层映射为 `END`
- `next_action` 键保留在 agent 返回字典中（节点层 `pop` 掉，**不进 state**）；`task_context` 字段保留
- `data/reference/` 目录为空或嵌入模型加载失败时，各 Agent 静默跳过 RAG 段，不影响原有流程
- 依赖 `faiss-cpu` + `sentence-transformers` 体积较大（sentence-transformers 会带入 torch CPU 包），一次性安装
- `.env`、`venv/` 不得提交

---

### Task 1: RAG 向量库重构（依赖安装 + RagStore）

**Files:**
- Modify: `AgentSky/requirements.txt`
- Modify: `AgentSky/agents/rag.py`（整体重写）
- Test: `AgentSky/tests/test_rag.py`（新建）
- Test: `AgentSky/tests/__init__.py`（新建，空文件）

**Interfaces:**
- Consumes: 无（本项目首个任务）
- Produces:
  - `class RagStore`: `__init__(self, embed_model="all-MiniLM-L6-v2")`、`ingest(documents: list[str])`、`ingest_from_folder(folder: str) -> int`、`search(query: str, top_k: int = 3) -> list[dict]`（dict 含 `content`/`score`）
  - `get_store() -> RagStore`：模块级单例，首次调用从 `AgentSky/data/reference/` 载入，嵌入模型加载失败时返回空 store（`search` 返回 `[]`）

- [ ] **Step 1: 安装依赖**

Run（在项目根 `F:/Workspace/Agent/Freesky`）：
```bash
AgentSky/venv/Scripts/python.exe -m pip install faiss-cpu sentence-transformers pytest numpy
```
Expected: 安装成功。sentence-transformers 会带入 torch CPU 包（体积较大，耐心等待）。

- [ ] **Step 2: 更新 requirements.txt**

Edit `AgentSky/requirements.txt`，在末尾追加：
```
faiss-cpu>=1.7.0
sentence-transformers>=2.5.0
pytest>=7.0.0
numpy>=1.24.0
```

- [ ] **Step 3: 写失败测试**

Create `AgentSky/tests/__init__.py`（空文件）。

Create `AgentSky/tests/test_rag.py`：
```python
"""RagStore 单元测试 — 依赖已装且模型可加载时才运行"""
import pytest

pytest.importorskip("faiss")
pytest.importorskip("sentence_transformers")

from agents.rag import RagStore, get_store


@pytest.fixture(scope="module")
def store():
    store = RagStore()
    if store.embedder is None:
        pytest.skip("嵌入模型加载失败")
    store.ingest([
        "主角是废柴少年，意外觉醒仇恨值系统，别人越恨他越强。",
        "修真界宗门林立，天玄门以剑修著称，擅长御剑飞行。",
    ])
    return store


def test_search_returns_relevant_docs(store):
    results = store.search("宗门修炼 剑法", top_k=2)
    assert len(results) >= 1
    assert "content" in results[0]
    assert "score" in results[0]


def test_ingest_from_folder(tmp_path):
    (tmp_path / "a.txt").write_text("关于炼丹术的参考素材。", encoding="utf-8")
    (tmp_path / "b.txt").write_text("关于宗门等级制度的参考素材。", encoding="utf-8")
    s = RagStore()
    if s.embedder is None:
        pytest.skip("嵌入模型加载失败")
    n = s.ingest_from_folder(str(tmp_path))
    assert n == 2
    assert len(s.search("炼丹 等级", top_k=3)) >= 1


def test_empty_store_search_returns_empty():
    s = RagStore()
    assert s.search("anything") == []


def test_get_store_singleton():
    assert get_store() is get_store()
```

- [ ] **Step 4: 运行测试确认失败**

Run:
```bash
cd AgentSky && venv/Scripts/python.exe -m pytest tests/test_rag.py -v
```
Expected: FAIL —— `ModuleNotFoundError: No module named 'agents.rag'`（旧 rag.py 顶层 `import faiss`/`sentence_transformers` 会导致 import 失败）。

- [ ] **Step 5: 重写 agents/rag.py**

整体重写 `AgentSky/agents/rag.py`：
```python
"""写作资料向量库 — FAISS 检索，供 writer/setting/character/plot 检索参考素材"""

import os
from pathlib import Path

try:
    import numpy as np
    import faiss
    from sentence_transformers import SentenceTransformer
    _EMBED_AVAILABLE = True
except ImportError:
    _EMBED_AVAILABLE = False
    np = None


class RagStore:
    """向量存储与检索：每个 .txt 文件作为一条文档"""

    def __init__(self, embed_model: str = "all-MiniLM-L6-v2"):
        self.docs: list[str] = []
        self.index = None
        self.embedder = None
        if _EMBED_AVAILABLE:
            try:
                self.embedder = SentenceTransformer(embed_model)
            except Exception as e:
                print(f"  [RAG] 加载嵌入模型失败: {e}")

    def ingest(self, documents: list[str]):
        self.docs = list(documents)
        if not self.docs or self.embedder is None:
            return
        vecs = self.embedder.encode(self.docs, normalize_embeddings=True)
        self.index = faiss.IndexFlatIP(vecs.shape[1])
        self.index.add(np.asarray(vecs, dtype=np.float32))

    def ingest_from_folder(self, folder: str) -> int:
        docs = []
        for path in sorted(Path(folder).glob("*.txt")):
            docs.append(path.read_text(encoding="utf-8"))
        self.ingest(docs)
        return len(docs)

    def search(self, query: str, top_k: int = 3) -> list[dict]:
        if self.index is None or not self.docs or self.embedder is None:
            return []
        q = self.embedder.encode([query], normalize_embeddings=True).astype(np.float32)
        scores, idxs = self.index.search(q, min(top_k, len(self.docs)))
        return [
            {"content": self.docs[i], "score": float(scores[0][j])}
            for j, i in enumerate(idxs[0]) if i >= 0
        ]


_store = None
_store_loaded = False


def get_store() -> RagStore:
    """模块级单例 — 首次调用从 data/reference/ 载入素材；失败则返回空 store"""
    global _store, _store_loaded
    if _store_loaded:
        return _store
    _store_loaded = True

    folder = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data", "reference")
    store = RagStore()
    n = 0
    if os.path.isdir(folder):
        try:
            n = store.ingest_from_folder(folder)
        except Exception as e:
            print(f"  [RAG] 素材载入失败: {e}")
    print(f"  [RAG] 素材库就绪: {n}篇文档")
    _store = store
    return _store
```

删除旧文件中的 `RagAgent` 类。

- [ ] **Step 6: 运行测试确认通过**

Run:
```bash
cd AgentSky && venv/Scripts/python.exe -m pytest tests/test_rag.py -v
```
Expected: PASS（首次会下载 all-MiniLM-L6-v2 模型约 80MB；无网络/无模型时相关用例 skip，空 store 用例仍 PASS）。

- [ ] **Step 7: 提交**

```bash
git add AgentSky/requirements.txt AgentSky/agents/rag.py AgentSky/tests/
git commit -m "feat: RAG 向量库重构 - ingest_from_folder + get_store 单例"
```

---

### Task 2: 四个 Agent 接入 RAG（base + writer/setting/character/plot + 示例素材）

**Files:**
- Modify: `AgentSky/agents/base.py`
- Modify: `AgentSky/agents/writer.py`
- Modify: `AgentSky/agents/setting.py`
- Modify: `AgentSky/agents/character.py`
- Modify: `AgentSky/agents/plot.py`
- Create: `AgentSky/data/reference/修仙世界观.txt`
- Create: `AgentSky/data/reference/写作风格示例.txt`
- Test: `AgentSky/tests/test_agents_rag.py`（新建）

**Interfaces:**
- Consumes: `RagStore.search(query, top_k=3) -> list[dict]`（Task 1）
- Produces:
  - `BaseAgent.__init__(self, model, system_prompt, name=None, store=None)`：新增可选 `store`
  - `BaseAgent._retrieve_rag(state, top_k=3) -> list[dict]`：用 `user_request + task_context` 检索；`store` 为 None 或查不到返回 `[]`
  - 四个 Agent 构造函数新增 `store=None`；prompt 末尾新增 `## 参考资料（向量检索）` 段

- [ ] **Step 1: 写失败测试**

Create `AgentSky/tests/test_agents_rag.py`：
```python
"""Agent RAG 注入测试 — 用 FakeStore/FakeModel 验证 prompt 注入，不触发真实 LLM"""
from types import SimpleNamespace

from agents.rag import RagStore
from agents.writer import WriterAgent, SYSTEM_PROMPT as WP
from agents.setting import SettingAgent, SYSTEM_PROMPT as STP
from agents.character import CharacterAgent, SYSTEM_PROMPT as CP
from agents.plot import PlotAgent, SYSTEM_PROMPT as PP
from state import make_initial_state


class FakeStore:
    def search(self, query, top_k=3):
        return [{"content": "天玄门剑修以御剑术闻名，弟子入门先习养剑三年。", "score": 0.91}]


class FakeModel:
    def __init__(self, payload: str):
        self.payload = payload
        self.last_messages = []

    def invoke(self, messages):
        self.last_messages = messages
        return SimpleNamespace(content=self.payload, response_metadata={})


WRITER_PAYLOAD = '{"chapter_id": "ch_01", "chapter_title": "测试", "content": "正文", "foreshadowing_resolved": [], "foreshadowing_planted": [], "self_check": {}}'
SETTING_PAYLOAD = '{"new_settings": [], "conflict_report": "无冲突", "summary": "测试"}'
CHARACTER_PAYLOAD = '{"new_characters": [], "consistency_check": "无冲突", "summary": "测试"}'
PLOT_PAYLOAD = '{"plot_nodes": [], "foreshadowing_plan": [], "structure_analysis": "测试", "summary": "测试"}'


def _user_prompt(model: FakeModel) -> str:
    return model.last_messages[1].content


def test_writer_prompt_includes_rag_section():
    model = FakeModel(WRITER_PAYLOAD)
    agent = WriterAgent(model, WP, store=FakeStore())
    state = make_initial_state("写一部修仙小说")
    state["task_context"] = "撰写第1章"
    agent.invoke(state)
    prompt = _user_prompt(model)
    assert "## 参考资料（向量检索）" in prompt
    assert "天玄门剑修" in prompt


def test_writer_prompt_without_store_skips_rag():
    model = FakeModel(WRITER_PAYLOAD)
    agent = WriterAgent(model, WP, store=None)
    agent.invoke(make_initial_state("写一部修仙小说"))
    assert "## 参考资料（向量检索）" not in _user_prompt(model)


def test_setting_prompt_includes_rag_section():
    model = FakeModel(SETTING_PAYLOAD)
    agent = SettingAgent(model, STP, store=FakeStore())
    agent.invoke(make_initial_state("设计修真世界观"))
    assert "## 参考资料（向量检索）" in _user_prompt(model)


def test_character_prompt_includes_rag_section():
    model = FakeModel(CHARACTER_PAYLOAD)
    agent = CharacterAgent(model, CP, store=FakeStore())
    agent.invoke(make_initial_state("设计人物"))
    assert "## 参考资料（向量检索）" in _user_prompt(model)


def test_plot_prompt_includes_rag_section():
    model = FakeModel(PLOT_PAYLOAD)
    agent = PlotAgent(model, PP, store=FakeStore())
    agent.invoke(make_initial_state("设计大纲"))
    assert "## 参考资料（向量检索）" in _user_prompt(model)
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd AgentSky && venv/Scripts/python.exe -m pytest tests/test_agents_rag.py -v
```
Expected: FAIL —— `TypeError: __init__() got an unexpected keyword argument 'store'`（构造函数尚无 store 参数）。

- [ ] **Step 3: base.py 加 store 参数与检索助手**

Edit `AgentSky/agents/base.py`：
- 构造函数改为：
```python
def __init__(self, model, system_prompt: str, name: Optional[str] = None, store=None):
    self.model = model
    self.system_prompt = system_prompt
    self.name = name or self.__class__.__name__
    self.store = store
```
- 在类内新增方法（放在 `_log` 之前）：
```python
def _retrieve_rag(self, state, top_k: int = 3) -> list:
    """用 user_request + task_context 检索写作资料；无 store 或无结果返回 []"""
    if self.store is None:
        return []
    query = f"{state.get('user_request', '')} {state.get('task_context', '')}".strip()
    if not query:
        return []
    return self.store.search(query, top_k=top_k)
```

- [ ] **Step 4: writer.py 接入**

Edit `AgentSky/agents/writer.py`：
- 构造函数继承自动获得 `store`；`invoke` 内检索：
```python
rag_materials = self._retrieve_rag(state)
prompt = self._build_user_prompt(
    state["user_request"], plot_outline, characters,
    world_settings, foreshadowing_bank, completed,
    review_issues, current_draft, rag_materials
)
```
- `_build_user_prompt` 签名加尾参 `rag_materials: list`，在 `parts` 组装前追加：
```python
if rag_materials:
    parts.append("\n## 参考资料（向量检索）")
    for i, m in enumerate(rag_materials, 1):
        parts.append(f"{i}. {m['content'][:500]}")
```
（放在 `parts = [f"## 故事需求\n{user_request}"]` 之后即可）

- [ ] **Step 5: setting.py 接入**

Edit `AgentSky/agents/setting.py`：
- `invoke` 内：
```python
rag_materials = self._retrieve_rag(state)
prompt = self._build_user_prompt(task_context, existing, rag_materials)
```
- `_build_user_prompt` 签名改 `(self, task_context, existing, rag_materials)`，开头追加：
```python
if rag_materials:
    parts.append("\n## 参考资料（向量检索）")
    for i, m in enumerate(rag_materials, 1):
        parts.append(f"{i}. {m['content'][:500]}")
```

- [ ] **Step 6: character.py 接入**

Edit `AgentSky/agents/character.py`：
- `invoke` 内：
```python
rag_materials = self._retrieve_rag(state)
prompt = self._build_user_prompt(task_context, existing, world_settings, rag_materials)
```
- `_build_user_prompt` 签名改 `(self, task_context, existing, settings, rag_materials)`，开头追加与 Step 5 相同的 RAG 段。

- [ ] **Step 7: plot.py 接入**

Edit `AgentSky/agents/plot.py`：
- `invoke` 内：
```python
rag_materials = self._retrieve_rag(state)
prompt = self._build_user_prompt(task_context, existing_plot, characters, world_settings, rag_materials)
```
- `_build_user_prompt` 签名改 `(self, task_context, existing_plot, characters, settings, rag_materials)`，开头追加与 Step 5 相同的 RAG 段。

- [ ] **Step 8: 创建示例素材**

Create `AgentSky/data/reference/修仙世界观.txt`：
```
天玄门是修真界第一剑修宗门，坐落于青云山脉主峰。弟子入门先习养剑术三年，
以心养剑，方能引剑出鞘。宗门等级从外门弟子、内门弟子、真传弟子、长老到掌门。
天玄门的剑法讲究以意驭剑，剑出必见血，因此门规严禁弟子私斗伤人。
```

Create `AgentSky/data/reference/写作风格示例.txt`：
```
本书采用快节奏爽文写法：每章以冲突开场，主角在绝境中依靠金手指逆转，
结尾留悬念钩子。描写多用短句与动作推进，少用冗长心理描写。
角色对话体现性格：主角冷静少言，反派嚣张跋扈。
```

- [ ] **Step 9: 运行测试确认通过**

Run:
```bash
cd AgentSky && venv/Scripts/python.exe -m pytest tests/test_agents_rag.py -v
```
Expected: PASS（5 个用例全绿）。

- [ ] **Step 10: 提交**

```bash
git add AgentSky/agents/base.py AgentSky/agents/writer.py AgentSky/agents/setting.py AgentSky/agents/character.py AgentSky/agents/plot.py AgentSky/data/ AgentSky/tests/
git commit -m "feat: writer/setting/character/plot 接入 RAG 写作资料检索"
```

---

### Task 3: Supervisor + Command 图重构

**Files:**
- Modify: `AgentSky/state.py`
- Modify: `AgentSky/agents/supervisor.py`
- Modify: `AgentSky/graph/workflow.py`
- Test: `AgentSky/tests/test_workflow.py`（新建）

**Interfaces:**
- Consumes: `get_store()`（Task 1）、`WriterAgent(..., store=...)` 等四 Agent 构造函数（Task 2）
- Produces:
  - `AgentSkyState` 不再含 `next_action`/`task_queue`
  - `supervisor_node(state) -> Command(goto=节点名|END, update=...)`
  - `reviewer_node(state) -> Command(goto='supervisor'|END, update=...)`
  - `create_workflow(model=None) -> CompiledStateGraph`（6 节点、固定边，无条件边）

- [ ] **Step 1: 写失败测试**

Create `AgentSky/tests/test_workflow.py`：
```python
"""图重构测试 — Command 路由 + 图结构，不触发真实 LLM"""
from types import SimpleNamespace
from langgraph.types import Command
from langgraph.graph import END

import graph.workflow as wf
from state import make_initial_state


class FakeAgent:
    def __init__(self, result: dict):
        self.result = result

    def invoke(self, state):
        return self.result


class FakeStore:
    def search(self, query, top_k=3):
        return []


def test_supervisor_node_returns_command_with_node_name(monkeypatch):
    wf._AGENTS["supervisor"] = FakeAgent(
        {"next_action": "writer", "phase": "writing", "task_context": "写第1章", "supervisor_log": ["x"]}
    )
    result = wf.supervisor_node({})
    assert isinstance(result, Command)
    assert result.goto == "writer"
    assert result.update["phase"] == "writing"
    assert "next_action" not in result.update


def test_supervisor_node_finish_maps_to_end(monkeypatch):
    wf._AGENTS["supervisor"] = FakeAgent({"next_action": "finish", "phase": "done"})
    assert wf.supervisor_node({}).goto == END


def test_reviewer_node_passed_goes_end(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": True, "review_round": 1, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == END


def test_reviewer_node_failed_goes_supervisor(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": False, "review_round": 1, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == "supervisor"


def test_reviewer_node_max_rounds_goes_end(monkeypatch):
    wf._AGENTS["reviewer"] = FakeAgent({"review_passed": False, "review_round": 3, "messages": []})
    assert wf.reviewer_node({"max_review_rounds": 3}).goto == END


def test_create_workflow_structure(monkeypatch):
    monkeypatch.setattr(wf, "get_store", lambda: FakeStore())
    workflow = wf.create_workflow(model=object())
    nodes = set(workflow.get_graph().nodes.keys())
    assert {"supervisor", "setting", "character", "plot", "writer", "reviewer"} <= nodes
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
cd AgentSky && venv/Scripts/python.exe -m pytest tests/test_workflow.py -v
```
Expected: FAIL —— 当前 `supervisor_node` 返回普通 dict（无 `goto`）、`create_workflow` 仍有条件边，且测试中 `next_action not in update` 断言失败。

- [ ] **Step 3: state.py 删除字段**

Edit `AgentSky/state.py`：
- 从 `AgentSkyState` 删除两行：
```python
    next_action: str
    task_queue: list[str]
```
- 从 `make_initial_state` 删除两行：
```python
        next_action="",
        task_queue=[],
```

- [ ] **Step 4: supervisor.py 决策值改节点名**

Edit `AgentSky/agents/supervisor.py`：
- SYSTEM_PROMPT 中路由规则改为：
```
## 路由规则（next_action取值）
- "setting"   — 缺少世界观/势力/规则设定，派给设定师
- "character" — 缺少人物卡/人物关系，派给人物设计师
- "plot"      — 缺少大纲/主线支线/伏笔，派给剧情策划
- "writer"    — 蓝图（设定+人物+大纲）齐备，派给写手
- "finish"    — 全部完成，结束流程
```
- 输出格式 JSON 中的取值说明改为：
```
  "next_action": "setting|character|plot|writer|finish",
```
- `_route_blueprint_ready`：`"next_action": "writer_agent"` → `"next_action": "writer"`；删除 `"task_queue": ["writer_agent"],` 行
- `_route_by_llm`：
  - `if next_action == "writer_agent":` → `if next_action == "writer":`
  - 删除 `"task_queue": [next_action] if next_action != "finish" else [],` 行
- `_handle_review`：
  - 删除 `action_map = {...}` 与 `next_action = action_map.get(target, "writer_agent")`，改为直接：
    ```python
    next_action = actionable[0].get("target_agent", "writer")
    ```
  - 删除该分支里的 `"task_queue"`（若有）
  - 检查其余 `"writer_agent"` 出现处全部改为 `"writer"`（`_route_blueprint_ready`、minor 分支 `"next_action": "writer_agent"`）
- `_route_by_llm` 返回值保留 `"next_action": next_action`（节点层会 pop）

- [ ] **Step 5: workflow.py 改 Command**

Edit `AgentSky/graph/workflow.py`：
- 顶部导入改为：
```python
from langgraph.graph import StateGraph, START, END
from langgraph.types import Command
from state import AgentSkyState, make_initial_state
from llm.config import get_model
from agents.rag import get_store
```
- 删除 `route_supervisor`、`route_reviewer` 两个函数
- `supervisor_node` 改为：
```python
def supervisor_node(state: AgentSkyState) -> Command:
    """主编节点 — 解析需求，返回 Command 决定下一跳"""
    result = _AGENTS["supervisor"].invoke(state)
    target = result.pop("next_action", "finish")
    goto = END if target == "finish" else target
    print(f"  [Command] supervisor → {goto}")
    return Command(goto=goto, update=result)
```
- `reviewer_node` 改为：
```python
def reviewer_node(state: AgentSkyState) -> Command:
    """审核节点 — 通过/超轮次→END，否则回 supervisor"""
    result = _AGENTS["reviewer"].invoke(state)
    passed = result.get("review_passed", False)
    review_round = result.get("review_round", 0)
    max_rounds = state.get("max_review_rounds", 3)
    goto = END if (passed or review_round >= max_rounds) else "supervisor"
    print(f"  [Command] reviewer → {goto} (passed={passed}, round={review_round})")
    return Command(goto=goto, update=result)
```
- `_ensure_agents` 注入 store：
```python
def _ensure_agents(model):
    if _AGENTS:
        return _AGENTS

    store = get_store()

    from agents.supervisor import SupervisorAgent, SYSTEM_PROMPT as SP
    from agents.setting import SettingAgent, SYSTEM_PROMPT as STP
    from agents.character import CharacterAgent, SYSTEM_PROMPT as CP
    from agents.plot import PlotAgent, SYSTEM_PROMPT as PP
    from agents.writer import WriterAgent, SYSTEM_PROMPT as WP
    from agents.reviewer import ReviewerAgent, SYSTEM_PROMPT as RP

    _AGENTS["supervisor"] = SupervisorAgent(model, SP)
    _AGENTS["setting"] = SettingAgent(model, STP, store=store)
    _AGENTS["character"] = CharacterAgent(model, CP, store=store)
    _AGENTS["plot"] = PlotAgent(model, PP, store=store)
    _AGENTS["writer"] = WriterAgent(model, WP, store=store)
    _AGENTS["reviewer"] = ReviewerAgent(model, RP)

    return _AGENTS
```
- `create_workflow`：删除两处 `add_conditional_edges` 块，保留固定边：
```python
    builder.add_edge(START, "supervisor")
    builder.add_edge("setting", "supervisor")
    builder.add_edge("character", "supervisor")
    builder.add_edge("plot", "supervisor")
    builder.add_edge("writer", "reviewer")

    return builder.compile()
```

- [ ] **Step 6: 运行测试确认通过**

Run:
```bash
cd AgentSky && venv/Scripts/python.exe -m pytest tests/ -v
```
Expected: 全部 PASS（test_rag.py + test_agents_rag.py + test_workflow.py）。

- [ ] **Step 7: 提交**

```bash
git add AgentSky/state.py AgentSky/agents/supervisor.py AgentSky/graph/workflow.py AgentSky/tests/
git commit -m "refactor: supervisor 模式 + Command 路由，删除条件边与 next_action/task_queue"
```

---

### Task 4: 端到端验证

**Files:**
- 无新增；仅运行验证 + 视情况修复

- [ ] **Step 1: 图结构打印**

Run:
```bash
cd AgentSky && venv/Scripts/python.exe -m graph.workflow
```
Expected: 打印 6 个节点；`workflow.invoke` 若 `.env` 有有效 DEEPSEEK_API_KEY 则完整跑通全流程（设定→人物→大纲→正文→审核），最终 `phase=done`；无 Key 则提示 `DEEPSEEK_API_KEY 未设置`（属预期，图编译本身成功）。

- [ ] **Step 2: 全量测试**

Run:
```bash
cd AgentSky && venv/Scripts/python.exe -m pytest tests/ -v
```
Expected: 全部 PASS。

- [ ] **Step 3: 确认 RAG 生效日志**

在 Step 1 输出中确认出现：
```
  [RAG] 素材库就绪: 2篇文档
```
及 writer 节点检索到资料时 prompt 注入正常。

- [ ] **Step 4: 提交（如有修复）**

若验证中发现需修复，修复后：
```bash
git add AgentSky/
git commit -m "fix: 端到端验证修复"
```

---

## Self-Review

- **Spec 覆盖**：Part1（Command 路由、删条件边、删 next_action/task_queue、决策值改节点名）→ Task 3；Part2（RagStore 重构、get_store、四 Agent 注入、data/reference 素材）→ Task 1/2；server.py/main.py 不涉及 → 无需任务。✓
- **占位符**：无 TBD/TODO，所有代码步骤含完整代码。✓
- **类型一致性**：`get_store()`（Task1 产出）在 Task2 测试与 Task3 `_ensure_agents` 使用一致；`RagStore.search(query, top_k=3)` 签名一致；`_retrieve_rag(state, top_k=3)` 在 Task2 Step3 定义、Step4-7 使用一致；四 Agent `_build_user_prompt` 新尾参在各自 Step 内签名与调用一致。✓
