import logging
from config.settings import settings

import logging
from config.settings import settings

class PlainTextLoggerAdapter(logging.LoggerAdapter):
    def process(self, msg, kwargs):
        # If extra keyword arguments are given, append them as text
        extras = kwargs.pop('extra', {})
        # Include any direct kwargs passed to the log method
        extras.update({k: v for k, v in kwargs.items() if k not in ('exc_info', 'stack_info')})
        if extras:
            # Format as key1=value1 | key2=value2 ...
            extras_str = " | ".join(f"{k}: {v}" for k, v in extras.items())
            msg = f"{msg} | {extras_str}"
            # Remove the extra keys from kwargs so logging doesn't complain
            for k in list(extras.keys()):
                kwargs.pop(k, None)
        return msg, kwargs

def setup_logger():
    logger = logging.getLogger("rl_agent")
    logger.setLevel(getattr(logging, settings.log_level.upper(), logging.INFO))
    if logger.hasHandlers():
        logger.handlers.clear()
    handler = logging.StreamHandler()
    handler.setLevel(getattr(logging, settings.log_level.upper(), logging.INFO))
    formatter = logging.Formatter(
        fmt="%(asctime)s [%(levelname)8s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    logger.propagate = False
    # Use the adapter for plain text formatting
    return PlainTextLoggerAdapter(logger, {})

logger = setup_logger()