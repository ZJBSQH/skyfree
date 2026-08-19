"""LLM 调用重试工具"""

import time
import functools


def retry_on_failure(max_retries=3, delay=2):
    """装饰器：LLM 调用失败时自动重试，指数退避"""

    def decorator(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            last_error = None
            for attempt in range(1, max_retries + 1):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    last_error = e
                    if attempt < max_retries:
                        wait = delay * (2 ** (attempt - 1))
                        print(f"  [重试] 第{attempt}次失败: {e}, {wait}s后重试...")
                        time.sleep(wait)
            raise RuntimeError(
                f"LLM调用失败，已重试{max_retries}次: {last_error}"
            )

        return wrapper

    return decorator
