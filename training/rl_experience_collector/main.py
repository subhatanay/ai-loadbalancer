from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi import status, Request
from typing import Dict, Any
from datetime import datetime
from pydantic import BaseModel
import json
import threading
import logging
from logging.handlers import RotatingFileHandler
from pydantic_settings import BaseSettings

# Settings
class Settings(BaseSettings):
    experience_file: str = "data/rl_experiences.jsonl"
    log_level: str = "INFO"

    class Config:
        env_file = ".env"

settings = Settings()

# Logger
def get_logger(name: str):
    logger = logging.getLogger(name)
    logger.setLevel(settings.log_level)
    if not logger.handlers:
        fmt = "%(asctime)s | %(levelname)-8s | %(name)s | %(message)s"
        handler = RotatingFileHandler("collector.log", maxBytes=10_485_760, backupCount=5)
        handler.setFormatter(logging.Formatter(fmt))
        logger.addHandler(handler)
    return logger

logger = get_logger("collector")

# Models
class StateSnapshot(BaseModel):
    timestamp: datetime
    metrics: Dict[str, Any]

class RLExperience(BaseModel):
    state: StateSnapshot
    action: str
    reward: float
    next_state: StateSnapshot
    metadata: Dict[str, Any] = {}

# Persistence
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

# Alias for clarity
ExperienceSchema = RLExperience

app = FastAPI(title="RL Experience Collector")

# Allow LB CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["POST"],
    allow_headers=["*"],
)

@app.post("/experience", status_code=201)
async def collect_experience(exp: ExperienceSchema):
    """
    Endpoint to receive RL experiences from the load balancer.
    """
    try:
        ExperienceWriter.append(exp)
        logger.info("Experience recorded", extra={"action": exp.action, "reward": exp.reward})
        return {"status": "ok"}
    except Exception as e:
        logger.error("Error recording experience", exc_info=e)
        raise HTTPException(status_code=500, detail="Failed to record experience")

@app.get("/health")
async def health():
    return {"status": "healthy"}

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    import logging
    logging.error(f"422 Error: {exc.errors()} Body: {exc.body}")
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={"detail": exc.errors(), "body": exc.body},
    )