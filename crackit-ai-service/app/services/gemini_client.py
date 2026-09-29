import os
import logging
from google import genai
from google.genai import types

logger = logging.getLogger(__name__)

# Default cascade order:
# 1. gemini-3.5-flash-lite: High throughput, 1,500 RPD free tier quota
# 2. gemini-3.8-flash: State-of-the-art reasoning fallback
# 3. gemini-2.5-flash: Legacy fallback
DEFAULT_MODELS_CASCADE = [
    os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite"),
    "gemini-3.8-flash",
    "gemini-2.5-flash",
]


def generate_content_with_fallback(
    client: genai.Client,
    contents,
    config: types.GenerateContentConfig = None,
    models: list = None
):
    model_list = models or DEFAULT_MODELS_CASCADE
    # Deduplicate while preserving order
    unique_models = []
    seen = set()
    for m in model_list:
        if m and m not in seen:
            seen.add(m)
            unique_models.append(m)

    last_error = None
    for model_name in unique_models:
        try:
            return client.models.generate_content(
                model=model_name,
                contents=contents,
                config=config
            )
        except Exception as e:
            last_error = e
            err_str = str(e)
            # Transient / Quota errors that warrant falling back to another model
            if any(code in err_str for code in ["429", "RESOURCE_EXHAUSTED", "503", "UNAVAILABLE", "404", "NOT_FOUND"]):
                logger.warning(
                    f"Gemini model '{model_name}' hit transient/quota limit: {err_str[:120]}. "
                    f"Retrying with next model in cascade..."
                )
                continue
            # For non-recoverable errors (like auth or syntax), fail immediately
            raise e

    raise last_error
