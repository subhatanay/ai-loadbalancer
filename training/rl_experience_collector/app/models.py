from typing import Dict, Any
from datetime import datetime
from pydantic import BaseModel

class StateSnapshot(BaseModel):
    timestamp: datetime
    metrics: Dict[str, Any]

class RLExperience(BaseModel):
    state: StateSnapshot
    action: str
    reward: float
    next_state: StateSnapshot
    metadata: Dict[str, Any] = {}