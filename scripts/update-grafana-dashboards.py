#!/usr/bin/env python3

"""
Grafana Dashboard Auto-Provisioning Script
This script automatically imports all dashboards into Grafana via API
"""

import json
import os
import sys
import time
import requests
from pathlib import Path

# Configuration
GRAFANA_URL = "http://localhost:3000"
GRAFANA_USER = "admin"
GRAFANA_PASSWORD = "admin"
DASHBOARD_DIR = "../grafana/dashboards_v2"

class GrafanaDashboardManager:
    def __init__(self, url, username, password):
        self.url = url.rstrip('/')
        self.auth = (username, password)
        self.session = requests.Session()
        self.session.auth = self.auth
        
    def wait_for_grafana(self, max_attempts=30):
        """Wait for Grafana to be ready"""
        print("⏳ Waiting for Grafana to be ready...")
        
        for attempt in range(1, max_attempts + 1):
            try:
                response = self.session.get(f"{self.url}/api/health", timeout=5)
                if response.status_code == 200:
                    print("✅ Grafana is ready!")
                    return True
            except requests.exceptions.RequestException:
                pass
            
            print(f"   Attempt {attempt}/{max_attempts} - Grafana not ready yet...")
            time.sleep(5)
        
        print("❌ Grafana failed to start within expected time")
        return False
    
    def setup_prometheus_datasource(self):
        """Create Prometheus datasource if it doesn't exist"""
        print("🔗 Setting up Prometheus datasource...")
        
        # Check if datasource exists
        try:
            response = self.session.get(f"{self.url}/api/datasources/name/prometheus")
            if response.status_code == 200:
                print("✅ Prometheus datasource already exists")
                return True
        except requests.exceptions.RequestException:
            pass
        
        # Create datasource
        datasource_config = {
            "name": "prometheus",
            "type": "prometheus",
            "url": "http://prometheus:9090",
            "access": "proxy",
            "isDefault": True,
            "basicAuth": False
        }
        
        try:
            response = self.session.post(
                f"{self.url}/api/datasources",
                json=datasource_config,
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code == 200:
                print("✅ Successfully created Prometheus datasource")
                return True
            else:
                print(f"❌ Failed to create Prometheus datasource (HTTP: {response.status_code})")
                print(f"   Response: {response.text}")
                return False
                
        except requests.exceptions.RequestException as e:
            print(f"❌ Error creating datasource: {e}")
            return False
    
    def import_dashboard(self, dashboard_file):
        """Import a single dashboard"""
        dashboard_name = Path(dashboard_file).stem
        print(f"📊 Importing dashboard: {dashboard_name}")
        
        try:
            with open(dashboard_file, 'r') as f:
                dashboard_json = json.load(f)
            
            # Prepare import payload
            import_payload = {
                "dashboard": dashboard_json,
                "overwrite": True,
                "inputs": [],
                "folderId": 0
            }
            
            response = self.session.post(
                f"{self.url}/api/dashboards/import",
                json=import_payload,
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code == 200:
                print(f"✅ Successfully imported: {dashboard_name}")
                return True
            else:
                print(f"❌ Failed to import {dashboard_name} (HTTP: {response.status_code})")
                print(f"   Response: {response.text}")
                return False
                
        except Exception as e:
            print(f"❌ Error importing {dashboard_name}: {e}")
            return False
    
    def import_all_dashboards(self, dashboard_dir):
        """Import all dashboards from directory"""
        dashboard_path = Path(dashboard_dir)
        
        if not dashboard_path.exists():
            print(f"❌ Dashboard directory not found: {dashboard_dir}")
            return False
        
        print(f"📁 Scanning dashboard directory: {dashboard_dir}")
        
        dashboard_files = list(dashboard_path.glob("*.json"))
        if not dashboard_files:
            print("❌ No dashboard files found")
            return False
        
        success_count = 0
        total_count = len(dashboard_files)
        
        for dashboard_file in dashboard_files:
            if self.import_dashboard(dashboard_file):
                success_count += 1
        
        print("=" * 50)
        print("✅ Dashboard import completed!")
        print(f"   Successfully imported: {success_count}/{total_count} dashboards")
        
        if success_count == total_count:
            print("🎉 All dashboards imported successfully!")
            print(f"🌐 Access Grafana at: {self.url}")
            return True
        else:
            print("⚠️  Some dashboards failed to import")
            return False

def main():
    print("🚀 Starting Grafana Dashboard Auto-Provisioning")
    print("=" * 50)
    
    # Initialize manager
    manager = GrafanaDashboardManager(GRAFANA_URL, GRAFANA_USER, GRAFANA_PASSWORD)
    
    # Wait for Grafana
    if not manager.wait_for_grafana():
        sys.exit(1)
    
    # Setup datasource
    manager.setup_prometheus_datasource()
    
    # Import dashboards
    success = manager.import_all_dashboards(DASHBOARD_DIR)
    
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
