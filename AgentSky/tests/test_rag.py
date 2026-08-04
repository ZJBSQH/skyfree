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
