# 📈 Comprehensive Guide to the AI Load Balancer Load Testing Framework

This document provides a detailed walkthrough of the load testing framework used to benchmark and validate the AI Load Balancer. It is designed to be powerful and flexible, simulating realistic user behavior to generate meaningful performance data.

## 🎯 1. Overview

The load testing framework is a Python-based solution built to:

*   **Simulate Realistic User Traffic**: It goes beyond simple API calls by simulating user sessions with varying behaviors, think times, and actions (browsing, searching, adding to cart, ordering).
*   **Benchmark Algorithms**: It provides an automated way to run fair, head-to-head comparisons between different load balancing algorithms (AI-based RL, Round Robin, Least Connections).
*   **Train the RL Agent**: It generates the specific, varied traffic patterns needed for the reinforcement learning agent's training cycles.
*   **Collect Rich Metrics**: The framework gathers detailed performance data, including latency, throughput, error rates, and success rates for in-depth analysis.

## 🛠️ 2. Tech Stack & Architecture

The framework is built with the following technologies:

*   **Language**: Python 3.x
*   **Core Libraries**:
    *   `requests`: For making HTTP requests to the application's API endpoints.
    *   `concurrent.futures`: To simulate concurrent users and generate parallel load.
*   **Configuration**: Test scenarios are defined in easy-to-edit `JSON` files.

### Architectural Flow

1.  **Configuration Loading**: A script reads a JSON configuration file to get all parameters for the test run, such as target URLs, test duration, number of users, and traffic mix.
2.  **User Simulation**: The framework spawns multiple threads or processes, where each one represents a single user.
3.  **Session Execution**: Each simulated user executes a series of actions (a "session") based on probabilities defined in the configuration. This mimics real-world user journeys.
4.  **API Interaction**: User actions translate into HTTP requests to the various microservices, routed through the load balancer.
5.  **Metrics Collection**: During the test, the framework logs the outcome of each request, including response time and status (success/failure).
6.  **Reporting**: After the test completes, results are aggregated and printed to the console, and can be saved to files for later analysis.

## 🚀 3. How to Run Tests

Setting up and running tests is straightforward.

### Prerequisites

1.  **Python 3**: Ensure you have Python 3 installed.
2.  **Install Dependencies**: Navigate to the `load-testing/` directory and install the required packages:
    ```bash
    pip install -r requirements.txt
    ```
3.  **Running Application**: The AI Load Balancer and the backend microservices must be deployed and running.

### Running a Load Test

There are two primary scripts for running tests:

#### A. Automated Benchmarking (`automated_benchmark.py`)

This is the main script for comparing load balancing algorithms.

**Purpose**: To run a full benchmark suite that tests multiple algorithms under the same load conditions and generates a comparative report.

**How to Run**:

```bash
python automated_benchmark.py --config <path_to_config.json>
```

Example:
```bash
python automated_benchmark.py --config default_benchmark_config.json
```

**What it Does**:

1.  **Health Checks**: Pings the system to ensure it's ready.
2.  **Algorithm Iteration**: For each algorithm specified in its internal config (`['rl', 'round-robin', 'least-connections']`):
    *   It calls the load balancer's admin API to switch to that algorithm.
    *   It resets the metrics.
    *   It runs a diversified load test based on the provided JSON configuration.
    *   It collects the final metrics for that run.
3.  **Report Generation**: After testing all algorithms, it prints a detailed report comparing their performance on throughput, latency, errors, and success rates.

#### B. RL Training Orchestration (`rl_training_executor.py`)

This script orchestrates the execution of various RL training scenarios. It automates running different traffic patterns, tracks progress, and generates summary reports.

**Purpose**: To run pre-defined or custom sequences of load tests specifically designed for generating training data for the reinforcement learning agent.

**How to Run**:

The script can be run in several modes:

**Option 1: Quick Validation (2 minutes)**

For a quick check to ensure the system is working correctly.

```bash
python rl_training_executor.py --mode validation
```

**Option 2: Comprehensive Training (3+ hours)**

Runs the full suite of traffic patterns to generate a rich dataset for RL model training.

```bash
python rl_training_executor.py --mode comprehensive
```

**Option 3: Custom Scenario**

Runs a specific scenario from a given configuration file.

```bash
python rl_training_executor.py --mode custom --config <config_file.json> --scenario <scenario_name>
```

*Example*:

```bash
python rl_training_executor.py --mode custom --config comprehensive_rl_training_config.json --scenario comprehensive_rl_training
```

**Key Features**:

*   **Complex User Journeys**: Simulates users who may browse, search for specific items, add multiple items to a cart, and checkout.
*   **Error Injection**: Can simulate failures to test how the system and the RL agent respond to errors.
*   **Dynamic Scenarios**: The traffic mix and user behavior can be finely tuned in the config file.

## ⚙️ 4. Configuration Files

The power of the framework lies in its JSON-based configuration. Here is a breakdown of a typical config file (`quick_test_config.json` is a good example):

```json
{
  "base_url": "http://localhost:8080",
  "request_timeout": 10,
  "scenarios": [
    {
      "name": "Read-Heavy Traffic",
      "duration_minutes": 1,
      "concurrent_users": 10,
      "ramp_up_time_seconds": 5,
      "traffic_mix": {
        "browse_and_search": 0.8,
        "add_to_cart_and_order": 0.2
      }
    }
  ],
  "user_profiles": {
    "default": {
      "actions": [
        {"name": "browse_products", "weight": 50},
        {"name": "search_products", "weight": 30},
        {"name": "view_product_details", "weight": 15},
        {"name": "add_to_cart", "weight": 3},
        {"name": "place_order", "weight": 2}
      ]
    }
  }
}
```

### Key Configuration Parameters:

*   `base_url`: The entry point for the application (the load balancer's address).
*   `request_timeout`: Max time in seconds to wait for a response.
*   `scenarios`: An array of test scenarios to run sequentially.
    *   `name`: A descriptive name for the scenario.
    *   `duration_minutes`: How long the test should run.
    *   `concurrent_users`: The number of virtual users to simulate.
    *   `ramp_up_time_seconds`: The time over which to gradually increase to the full number of concurrent users.
    *   `traffic_mix`: Defines the high-level distribution of user journeys.
*   `user_profiles`: Defines the specific actions a user might take and their probabilities.
    *   `actions`: An array of actions.
        *   `name`: The name of the function to call (e.g., `browse_products`).
        *   `weight`: The relative probability of this action being chosen.

### Customizing Scenarios

To create a new test:

1.  **Copy an existing JSON file**: For example, copy `quick_test_config.json` to `my_custom_test.json`.
2.  **Adjust `concurrent_users`**: To increase or decrease the load.
3.  **Change `duration_minutes`**: For a longer or shorter test.
4.  **Modify `traffic_mix` and `user_profiles`**: To simulate different user behaviors. For example, to test a checkout-heavy scenario, increase the weight for `place_order`.

## 📊 5. Interpreting Results

The `automated_benchmark.py` script produces a summary table that is key for analysis. Here’s what to look for:

| Algorithm         | Total Requests | Throughput (req/s) | Avg Latency (ms) | P95 Latency (ms) | Success Rate | Error Rate |
|-------------------|----------------|--------------------|------------------|------------------|--------------|------------|
| rl                | 1204           | 20.07              | 150.5            | 350.1            | 99.5%        | 0.5%       |
| round-robin       | 1150           | 19.17              | 180.2            | 450.8            | 98.0%        | 2.0%       |

*   **Throughput**: Higher is better. It shows how many requests the system can handle per second.
*   **Latency (Avg, P95)**: Lower is better. P95 latency is a crucial metric, as it shows the response time for 95% of users, ignoring the worst outliers. A high P95 latency indicates a poor user experience for a significant number of users.
*   **Success/Error Rate**: A high success rate and low error rate are essential. Any errors should be investigated.

When comparing algorithms, look for a balance. The best algorithm will deliver high throughput with low latency and a near-perfect success rate.
