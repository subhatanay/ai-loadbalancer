import logging
from logging.handlers import RotatingFileHandler
from app.settings import settings

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