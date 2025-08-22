import json
import threading
from app.settings import settings
from app.logger import logger
from app.models import RLExperience

class ExperienceWriter:
    _lock = threading.Lock()

    @classmethod
    def append(cls, exp: RLExperience):
        line = exp.json()
        try:
            with cls._lock, open(settings.experience_file, "a") as f:
                f.write(line + "\n")
        except Exception as e:
            logger.error("Failed to write experience", exc_info=e)