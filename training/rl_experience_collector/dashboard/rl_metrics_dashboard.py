#!/usr/bin/env python3
"""
RL Experience Collector Metrics Dashboard
Real-time visualization of RL training data and system metrics
"""

import streamlit as st
import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import json
import requests
import time
from datetime import datetime, timedelta
import numpy as np
from typing import Dict, List, Any
import asyncio
import aiohttp

# Configure Streamlit page
st.set_page_config(
    page_title="RL Experience Collector Dashboard",
    page_icon="🤖",
    layout="wide",
    initial_sidebar_state="expanded"
)

class RLMetricsDashboard:
    """Real-time dashboard for RL Experience Collector metrics"""
    
    def __init__(self):
        self.collector_url = "http://localhost:8081"
        self.prometheus_url = "http://localhost:9090"
        self.load_balancer_url = "http://localhost:8080"
        
    def fetch_rl_experiences(self) -> List[Dict[str, Any]]:
        """Fetch RL experiences from collector"""
        try:
            response = requests.get(f"{self.collector_url}/experiences", timeout=5)
            if response.status_code == 200:
                return response.json().get('experiences', [])
            return []
        except Exception as e:
            st.error(f"Failed to fetch RL experiences: {e}")
            return []
    
    def fetch_collector_health(self) -> Dict[str, Any]:
        """Fetch collector health status"""
        try:
            response = requests.get(f"{self.collector_url}/health", timeout=5)
            if response.status_code == 200:
                return response.json()
            return {"status": "unhealthy", "error": f"HTTP {response.status_code}"}
        except Exception as e:
            return {"status": "unhealthy", "error": str(e)}
    
    def fetch_prometheus_metrics(self) -> Dict[str, Any]:
        """Fetch key metrics from Prometheus"""
        metrics = {}
        try:
            # CPU usage by service
            cpu_query = 'rate(process_cpu_seconds_total[5m]) * 100'
            response = requests.get(
                f"{self.prometheus_url}/api/v1/query",
                params={'query': cpu_query},
                timeout=5
            )
            if response.status_code == 200:
                metrics['cpu_usage'] = response.json()['data']['result']
            
            # Memory usage by service
            memory_query = 'process_resident_memory_bytes / 1024 / 1024'
            response = requests.get(
                f"{self.prometheus_url}/api/v1/query",
                params={'query': memory_query},
                timeout=5
            )
            if response.status_code == 200:
                metrics['memory_usage'] = response.json()['data']['result']
            
            # Request rate
            request_query = 'rate(http_requests_total[5m])'
            response = requests.get(
                f"{self.prometheus_url}/api/v1/query",
                params={'query': request_query},
                timeout=5
            )
            if response.status_code == 200:
                metrics['request_rate'] = response.json()['data']['result']
                
        except Exception as e:
            st.error(f"Failed to fetch Prometheus metrics: {e}")
        
        return metrics
    
    def process_rl_experiences(self, experiences: List[Dict]) -> pd.DataFrame:
        """Process RL experiences into DataFrame"""
        if not experiences:
            return pd.DataFrame()
        
        processed_data = []
        for exp in experiences:
            try:
                processed_data.append({
                    'timestamp': pd.to_datetime(exp.get('timestamp')),
                    'service_name': exp.get('service_name', 'unknown'),
                    'selected_instance': exp.get('action', {}).get('selected_instance', 'unknown'),
                    'reward': float(exp.get('reward', 0)),
                    'latency_ms': float(exp.get('metrics', {}).get('latency_ms', 0)),
                    'cpu_usage': float(exp.get('state', {}).get('cpu_usage', 0)),
                    'memory_usage': float(exp.get('state', {}).get('memory_usage', 0)),
                    'error_rate': float(exp.get('state', {}).get('error_rate', 0)),
                    'throughput': float(exp.get('state', {}).get('throughput', 0)),
                    'load_balancer_algorithm': exp.get('action', {}).get('algorithm', 'unknown')
                })
            except Exception as e:
                st.warning(f"Error processing experience: {e}")
                continue
        
        return pd.DataFrame(processed_data)
    
    def create_reward_timeline(self, df: pd.DataFrame) -> go.Figure:
        """Create reward timeline visualization"""
        if df.empty:
            return go.Figure().add_annotation(text="No data available", 
                                            xref="paper", yref="paper",
                                            x=0.5, y=0.5, showarrow=False)
        
        fig = px.line(df, x='timestamp', y='reward', color='service_name',
                     title='RL Reward Timeline by Service',
                     labels={'reward': 'Reward Score', 'timestamp': 'Time'})
        
        fig.update_layout(height=400, showlegend=True)
        return fig
    
    def create_action_distribution(self, df: pd.DataFrame) -> go.Figure:
        """Create action distribution pie chart"""
        if df.empty:
            return go.Figure().add_annotation(text="No data available",
                                            xref="paper", yref="paper", 
                                            x=0.5, y=0.5, showarrow=False)
        
        action_counts = df['selected_instance'].value_counts()
        
        fig = px.pie(values=action_counts.values, names=action_counts.index,
                    title='Action Distribution (Pod Selection)')
        
        fig.update_layout(height=400)
        return fig
    
    def create_performance_metrics(self, df: pd.DataFrame) -> go.Figure:
        """Create performance metrics subplot"""
        if df.empty:
            return go.Figure().add_annotation(text="No data available",
                                            xref="paper", yref="paper",
                                            x=0.5, y=0.5, showarrow=False)
        
        fig = make_subplots(
            rows=2, cols=2,
            subplot_titles=('Latency Over Time', 'CPU Usage', 'Memory Usage', 'Error Rate'),
            specs=[[{"secondary_y": False}, {"secondary_y": False}],
                   [{"secondary_y": False}, {"secondary_y": False}]]
        )
        
        # Latency
        fig.add_trace(
            go.Scatter(x=df['timestamp'], y=df['latency_ms'], 
                      mode='lines', name='Latency (ms)',
                      line=dict(color='blue')),
            row=1, col=1
        )
        
        # CPU Usage
        fig.add_trace(
            go.Scatter(x=df['timestamp'], y=df['cpu_usage'],
                      mode='lines', name='CPU %',
                      line=dict(color='red')),
            row=1, col=2
        )
        
        # Memory Usage
        fig.add_trace(
            go.Scatter(x=df['timestamp'], y=df['memory_usage'],
                      mode='lines', name='Memory %',
                      line=dict(color='green')),
            row=2, col=1
        )
        
        # Error Rate
        fig.add_trace(
            go.Scatter(x=df['timestamp'], y=df['error_rate'],
                      mode='lines', name='Error Rate %',
                      line=dict(color='orange')),
            row=2, col=2
        )
        
        fig.update_layout(height=600, showlegend=False)
        return fig
    
    def create_reward_heatmap(self, df: pd.DataFrame) -> go.Figure:
        """Create reward heatmap by service and time"""
        if df.empty:
            return go.Figure().add_annotation(text="No data available",
                                            xref="paper", yref="paper",
                                            x=0.5, y=0.5, showarrow=False)
        
        # Group by service and hour
        df['hour'] = df['timestamp'].dt.floor('H')
        heatmap_data = df.groupby(['service_name', 'hour'])['reward'].mean().reset_index()
        
        pivot_data = heatmap_data.pivot(index='service_name', columns='hour', values='reward')
        
        fig = px.imshow(pivot_data, 
                       title='Average Reward Heatmap (Service vs Time)',
                       labels=dict(x="Time", y="Service", color="Avg Reward"))
        
        fig.update_layout(height=400)
        return fig
    
    def render_dashboard(self):
        """Render the complete dashboard"""
        st.title("🤖 RL Experience Collector Dashboard")
        st.markdown("Real-time monitoring of RL training data and system performance")
        
        # Sidebar controls
        st.sidebar.header("Dashboard Controls")
        auto_refresh = st.sidebar.checkbox("Auto Refresh (30s)", value=True)
        refresh_button = st.sidebar.button("Refresh Now")
        
        # Data source status
        st.sidebar.header("Data Sources")
        
        # Check collector health
        health = self.fetch_collector_health()
        if health.get('status') == 'healthy':
            st.sidebar.success("✅ RL Collector: Healthy")
        else:
            st.sidebar.error(f"❌ RL Collector: {health.get('error', 'Unknown error')}")
        
        # Fetch data
        if auto_refresh or refresh_button:
            with st.spinner("Fetching RL experience data..."):
                experiences = self.fetch_rl_experiences()
                df = self.process_rl_experiences(experiences)
                prometheus_metrics = self.fetch_prometheus_metrics()
        
        # Main metrics
        col1, col2, col3, col4 = st.columns(4)
        
        if not df.empty:
            with col1:
                st.metric("Total Experiences", len(df))
            with col2:
                avg_reward = df['reward'].mean()
                st.metric("Average Reward", f"{avg_reward:.3f}")
            with col3:
                unique_services = df['service_name'].nunique()
                st.metric("Active Services", unique_services)
            with col4:
                latest_timestamp = df['timestamp'].max()
                st.metric("Latest Data", latest_timestamp.strftime("%H:%M:%S") if pd.notna(latest_timestamp) else "N/A")
        else:
            with col1:
                st.metric("Total Experiences", "0")
            with col2:
                st.metric("Average Reward", "N/A")
            with col3:
                st.metric("Active Services", "0")
            with col4:
                st.metric("Latest Data", "N/A")
        
        # Charts
        st.header("📊 RL Training Metrics")
        
        # Row 1: Reward timeline and action distribution
        col1, col2 = st.columns(2)
        with col1:
            reward_fig = self.create_reward_timeline(df)
            st.plotly_chart(reward_fig, use_container_width=True)
        
        with col2:
            action_fig = self.create_action_distribution(df)
            st.plotly_chart(action_fig, use_container_width=True)
        
        # Row 2: Performance metrics
        st.header("🔧 System Performance Metrics")
        perf_fig = self.create_performance_metrics(df)
        st.plotly_chart(perf_fig, use_container_width=True)
        
        # Row 3: Reward heatmap
        st.header("🌡️ Reward Analysis")
        heatmap_fig = self.create_reward_heatmap(df)
        st.plotly_chart(heatmap_fig, use_container_width=True)
        
        # Raw data table
        if st.checkbox("Show Raw Experience Data"):
            st.header("📋 Raw RL Experience Data")
            if not df.empty:
                st.dataframe(df.sort_values('timestamp', ascending=False).head(100))
            else:
                st.info("No experience data available")
        
        # Auto refresh
        if auto_refresh:
            time.sleep(30)
            st.experimental_rerun()

def main():
    dashboard = RLMetricsDashboard()
    dashboard.render_dashboard()

if __name__ == "__main__":
    main()
