"""
RL Model Performance Dashboard Server
Provides a web interface to monitor RL model performance in real-time
"""

from flask import Flask, render_template, jsonify, request
import requests
import json
from datetime import datetime, timedelta
import logging

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Configuration
RL_API_BASE_URL = "http://localhost:8088"
PROMETHEUS_BASE_URL = "http://localhost:9090"

@app.route('/')
def dashboard():
    """Main dashboard page"""
    return render_template('dashboard.html')

@app.route('/api/health')
def health_check():
    """Health check for the dashboard"""
    try:
        # Check RL API health
        rl_response = requests.get(f"{RL_API_BASE_URL}/health", timeout=5)
        rl_healthy = rl_response.status_code == 200
        
        return jsonify({
            "status": "healthy" if rl_healthy else "degraded",
            "rl_api_connected": rl_healthy,
            "timestamp": datetime.now().isoformat()
        })
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return jsonify({
            "status": "error",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }), 500

@app.route('/api/performance')
def get_performance_data():
    """Get comprehensive performance data"""
    try:
        # Get performance metrics from RL API
        response = requests.get(f"{RL_API_BASE_URL}/performance", timeout=10)
        response.raise_for_status()
        
        data = response.json()
        
        # Add timestamp
        data['dashboard_timestamp'] = datetime.now().isoformat()
        
        return jsonify(data)
        
    except Exception as e:
        logger.error(f"Failed to get performance data: {e}")
        return jsonify({
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }), 500

@app.route('/api/stats')
def get_rl_stats():
    """Get RL agent statistics"""
    try:
        response = requests.get(f"{RL_API_BASE_URL}/stats", timeout=10)
        response.raise_for_status()
        
        return jsonify(response.json())
        
    except Exception as e:
        logger.error(f"Failed to get RL stats: {e}")
        return jsonify({
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }), 500

@app.route('/api/prometheus/<metric_name>')
def get_prometheus_metric(metric_name):
    """Get specific metric from Prometheus"""
    try:
        # Get time range from query params
        hours = request.args.get('hours', 1, type=int)
        end_time = datetime.now()
        start_time = end_time - timedelta(hours=hours)
        
        # Query Prometheus
        params = {
            'query': metric_name,
            'start': start_time.timestamp(),
            'end': end_time.timestamp(),
            'step': '30s'
        }
        
        response = requests.get(
            f"{PROMETHEUS_BASE_URL}/api/v1/query_range",
            params=params,
            timeout=10
        )
        response.raise_for_status()
        
        return jsonify(response.json())
        
    except Exception as e:
        logger.error(f"Failed to get Prometheus metric {metric_name}: {e}")
        return jsonify({
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8089, debug=True)
