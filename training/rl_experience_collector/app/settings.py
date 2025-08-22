from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    experience_file: str = "data/rl_experiences.jsonl"
    log_level: str = "INFO"

    class Config:
        env_file = ".env"

settings = Settings()