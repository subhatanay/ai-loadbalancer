# AI Load Balancer - RL Agent Documentation

## Overview

The **RL-Agent** is an intelligent load balancing system using **Reinforcement Learning (Q-Learning)** to make optimal routing decisions for microservices. It learns from experience to route requests to best-performing server instances based on real-time metrics.

## Problem Description

### Traditional Load Balancing Issues
- **Static algorithms** (round-robin, least-connections) don't adapt to real-time performance
- **No learning** from past routing decisions
- **Poor handling** of varying pod performance
- **Overloading** of slow or unhealthy instances

### Example Problem
```
Service: inventory-service, Pods: [pod-A, pod-B, pod-C]

Round-Robin Result:
Request 1 → pod-A (50ms, CPU: 20%) ✓ Good
Request 2 → pod-B (500ms, CPU: 90%) ❌ Overloaded
Request 3 → pod-C (100ms, CPU: 40%) ✓ OK
Request 4 → pod-A (55ms, CPU: 25%) ✓ Good
Request 5 → pod-B (600ms, CPU: 95%) ❌ Still overloaded

Problem: Keeps routing to overloaded pod-B
```

## Solution Description

### RL-Based Intelligent Routing
1. **Observe** system state (CPU, memory, response times, errors)
2. **Take action** (select pod to route to)
3. **Receive feedback** (response time, success/failure)
4. **Learn** from outcomes to improve future decisions

### Benefits
- **Adaptive**: Learns optimal routing patterns
- **Multi-objective**: Optimizes latency, errors, throughput, load balance
- **Self-improving**: Gets better with more data
- **Load-aware**: Prevents pod overloading

## Architecture

```
Load Balancer (Java) ←→ RL-Agent (Python) ←→ Prometheus + Service Registry

Main Flow:
1. Request → Load Balancer
2. Load Balancer → RL-Agent (/decide)
3. RL-Agent → Get metrics + Select pod
4. RL-Agent → Load Balancer (selected pod)
5. Load Balancer → Forward to pod
6. Load Balancer → RL-Agent (/feedback)
7. RL-Agent → Learn from outcome
```

## Core Components

### 1. **rl_decision_api.py** - Main API
- FastAPI REST service
- `/decide` endpoint for routing decisions
- `/feedback` endpoint for learning
- Caching for performance optimization

### 2. **q_learning_agent.py** - Learning Core
- Q-table maintenance
- Action selection with epsilon-greedy
- Q-value updates using Bellman equation
- Model persistence

### 3. **state_encoder.py** - State Discretization
- Convert continuous metrics to discrete states
- Handle 5 metrics: CPU, Memory, Response Time, Error Rate, Request Rate
- Caching for performance

### 4. **action_selector.py** - Action Selection
- Epsilon-greedy with enhancements
- Upper Confidence Bound exploration
- Load balancing through rotation logic

### 5. **reward_calculator.py** - Reward Function
- Multi-objective reward calculation
- Combines latency, errors, throughput, balance, stability
- Weighted scoring system

## Q-Learning Algorithm

### Mathematical Foundation
```
Q(s,a) = Q(s,a) + α[r + γ * max(Q(s',a')) - Q(s,a)]

Where:
- Q(s,a) = Quality of action 'a' in state 's'
- α = Learning rate (0.1)
- r = Immediate reward
- γ = Discount factor (0.95)
- s' = Next state
- max(Q(s',a')) = Best future value
```

### Implementation
```python
def _update_q_value(self, state, action, reward, next_state):
    current_q = self.q_table[(state, action)]
    
    # Find best next action
    next_q_values = [self.q_table[(next_state, a)] for a in next_actions]
    max_next_q = max(next_q_values) if next_q_values else 0.0
    
    # Bellman equation
    td_target = reward + self.config.discount_factor * max_next_q
    new_q = current_q + self.config.learning_rate * (td_target - current_q)
    
    self.q_table[(state, action)] = new_q
```

## State Encoder Algorithm

### Purpose
Convert continuous metrics into discrete states for Q-learning.

### Discretization Process
```python
Input: CPU=45.7%, Memory=62.3%, ResponseTime=127ms, ErrorRate=0.8%, RequestRate=23.4rps

Binning:
- CPU: 45.7% → Bin 1 (25-50% range)
- Memory: 62.3% → Bin 2 (50-75% range) 
- ResponseTime: 127ms → Bin 1 (100-200ms range)
- ErrorRate: 0.8% → Bin 0 (0-5% range)
- RequestRate: 23.4rps → Bin 0 (0-50rps range)

Output State: (1, 2, 1, 0, 0)
```

### Algorithm
```python
def _fast_encode_state(self, metrics_dict):
    # CPU bins: 0-4 (25% each)
    cpu_bin = min(4, int(cpu_usage / 25))
    
    # Memory bins: 0-4 (25% each)
    memory_bin = min(4, int(memory_usage / 25))
    
    # Response time bins: 0-4 (100ms each)
    rt_bin = min(4, int(response_time / 100))
    
    # Error rate bins: 0-2 (5% each)
    error_bin = min(2, int(error_rate / 5))
    
    # Request rate bins: 0-4 (50 rps each)
    rps_bin = min(4, int(request_rate / 50))
    
    return (cpu_bin, memory_bin, rt_bin, error_bin, rps_bin)
```

## Action Selection Algorithm

### Epsilon-Greedy with Enhancements
```python
def select_action(self, state, q_table, actions, epsilon, episode):
    # Calculate adaptive epsilon
    adaptive_epsilon = self._calculate_adaptive_epsilon(epsilon, episode)
    
    if random.random() < adaptive_epsilon:
        # EXPLORE: Try different pods
        return self._exploration_strategy(actions, state, q_table)
    else:
        # EXPLOIT: Use best known pod (with load balancing)
        return self._exploitation_strategy(state, q_table, actions)
```

### Adaptive Epsilon
```python
# Standard decay
epsilon = max(epsilon_min, epsilon_start * (decay_rate ^ episode))

# Load balancing enhancement
if action_diversity < 0.4:  # Low diversity
    epsilon = min(1.0, epsilon * 2.0)  # Boost exploration

# Always maintain 5% exploration
epsilon = max(0.05, epsilon)
```

### Exploration Strategies
1. **UCB**: `Q(s,a) + c * sqrt(ln(total_visits) / action_visits)`
2. **Least-tried**: Pick pods used least frequently
3. **Random**: Fallback option

### Exploitation with Load Balancing
1. **Find best Q-values** with 5% tolerance
2. **Among equally good pods**, prefer least recently used
3. **Random tie-breaking** for final selection

## Reward Function Algorithm

### Multi-Objective Formula
```python
Total_Reward = 0.4×Latency + 0.4×Errors + 0.1×Throughput + 0.1×Balance + 0.5×Stability
```

### Component Calculations

#### Latency Reward
```python
latency_reward = -avg_latency/threshold - 0.5*max_latency/threshold

Examples:
- 100ms response → -0.2 (good)
- 800ms response → -2.1 (bad)
```

#### Error Reward
```python
error_reward = -10*avg_error_rate - 20*max_error_rate

Examples:
- 0% errors → 0.0 (perfect)
- 5% errors → -1.5 (bad)
```

#### Balance Reward
```python
balance_reward = -variance(cpu_usage)/100 + optimal_range_bonus

Examples:
- Even CPU [40%, 45%, 42%] → +0.9 (balanced)
- Uneven CPU [20%, 90%, 30%] → -0.3 (unbalanced)
```

## Integration Flow

### Decision Flow
```
1. Load Balancer receives request
2. POST /decide → RL-Agent
3. RL-Agent gets metrics from Prometheus
4. RL-Agent selects optimal pod using Q-learning
5. RL-Agent returns selected pod
6. Load Balancer forwards request to selected pod
```

### Learning Flow
```
1. Load Balancer gets response from pod
2. POST /feedback → RL-Agent (with response time, status)
3. RL-Agent calculates reward based on outcome
4. RL-Agent updates Q-table using Bellman equation
5. RL-Agent improves for future decisions
```

## Performance Optimizations

### Caching Strategy
- **Decision Cache**: 100ms TTL with rotation logic
- **Metrics Cache**: 1s TTL for Prometheus data
- **Service Cache**: 5s TTL for service discovery
- **State Cache**: 5s TTL for encoded states

### Load Balancing Features
- **Rotation Logic**: Max 3 consecutive requests to same pod
- **Diversity Tracking**: Monitor action distribution
- **Adaptive Exploration**: Boost if low diversity detected
- **LRU Tie-breaking**: Prefer least recently used pods

This RL-Agent provides intelligent, adaptive load balancing that continuously learns and improves routing decisions while ensuring fair load distribution across all available pods.
