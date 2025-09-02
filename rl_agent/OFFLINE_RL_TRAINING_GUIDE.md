# 🧠 AI Load Balancer: Offline RL Training Guide

This guide provides a comprehensive overview of the offline reinforcement learning (RL) training process for the AI Load Balancer. It explains why we use offline training, how the process works, and how to run it.

## 1. Why Offline RL Training?

Training a reinforcement learning agent directly in a live production environment is risky. Poor decisions during the initial learning phase could lead to service outages or a degraded user experience. Offline training provides a safe and efficient alternative by allowing the agent to learn from a pre-collected dataset of interactions.

**Key Advantages**:

*   **Safety**: No impact on live traffic. The agent learns from historical data without making real-time decisions.
*   **Efficiency**: We can reuse large datasets to train and retrain models without needing to regenerate traffic.
*   **Rich Data**: Allows the agent to learn from a wide variety of scenarios, including rare edge cases and simulated failures, that might be too dangerous or expensive to create in a live environment.

## 2. The Offline Training Workflow

The entire process is a pipeline that transforms raw experience data into a trained, deployable AI model.

```mermaid
graph TD
    A[📊 Data Collection<br>(rl_experiences.jsonl)] --> B(🧹 Data Preprocessing);
    B --> C(🔢 State Encoding);
    C --> D{🤖 Q-Learning Training};
    D --> E(📈 Evaluation);
    E --> F[🚀 Model Deployment<br>(q_table.pkl, state_encoder.pkl)];
```

## 3. A Deep Dive into the Process

The training process is orchestrated by `offline_trainer.py` and can be broken down into the following steps:

### Step 1: Data Loading (`experience_loader.py`)

The process begins by loading the raw data collected by the load testing framework. This data is stored as a JSONL file where each line is an "experience."

*   **Input**: `rl_agent/data/rl_experiences.jsonl`
*   **Action**: The `ExperienceLoader` reads each line and parses it into an `Experience` object, which contains the `state`, `action`, `reward`, and `next_state`.

### Step 2: Data Preprocessing (`data_preprocessor.py`)

Raw data isn't suitable for training. It needs to be cleaned and structured.

*   **Action**: The `DataPreprocessor` takes the raw experiences and performs two key tasks:
    1.  **Builds Action Mappings**: It scans all unique actions (e.g., routing to `user-service-instance-1`) and assigns a unique integer index to each. This is crucial for the Q-table.
    2.  **Structures Data**: It converts the raw state dictionaries into lists of `ServiceMetrics` objects, creating structured `(state, action_idx, reward, next_state)` tuples.

### Step 3: State Encoding (`state_encoder.py`)

A state in our system is complex (CPU, memory, latency, etc., for *all* services). This creates a massive, unmanageable number of possible states. The `StateEncoder` solves this.

*   **Action**: It uses a binning technique to discretize the continuous state variables. For example, CPU usage might be binned into `[Low, Medium, High]`. By combining the bins for all metrics, it generates a single, simplified, hashable state representation.
*   **Output**: A unique integer hash for each distinct state, which can be used as a key in the Q-table.

### Step 4: Q-Learning Training (`q_learning_agent.py`)

This is the core of the learning process.

*   **Action**: The `OfflineTrainer` iterates through the preprocessed data in epochs. In each step, it provides a `(state, action, reward, next_state)` tuple to the `QLearningAgent`. The agent then updates its Q-table using the Bellman equation:
    
    `Q(s, a) = Q(s, a) + alpha * [reward + gamma * max(Q(s', a')) - Q(s, a)]`
    
    This update nudges the Q-value for the given state-action pair closer to a more accurate estimate of its long-term value.

### Step 5: Evaluation (`training_evaluator.py`)

To ensure the model is actually learning, we periodically evaluate its performance on a separate validation dataset.

*   **Action**: The `TrainingEvaluator` calculates metrics like:
    *   **Validation Accuracy**: How often the agent's predicted best action matches the action that was actually taken in the dataset.
    *   **Average Q-Value**: The average value across the Q-table, which indicates the overall confidence of the agent.

### Step 6: Model Deployment

Once training is complete, the learned knowledge needs to be saved.

*   **Action**: The `OfflineTrainer` saves the following critical files to the `rl_agent/models/` directory:
    1.  `q_table.pkl`: The trained Q-table containing the learned state-action values.
    2.  `state_encoder.pkl`: The fitted state encoder, needed to convert live metrics into the same state representation used during training.
    3.  `action_mappings.json`: The mapping of action names to integer indices.

These files contain everything the live RL agent needs to start making intelligent routing decisions.

## 4. Key Files and Locations

- **Entry Point**: `rl_agent/offline-training/train.py`
- **Main Orchestrator**: `rl_agent/offline-training/offline_trainer.py`
- **Input Data**: `rl_agent/data/rl_experiences.jsonl`
- **Output Models**: 
    - `rl_agent/models/q_table.pkl`
    - `rl_agent/models/state_encoder.pkl`
    - `rl_agent/models/action_mappings.json`

## 5. How to Run the Training

Executing the training process is simple.

1.  **Navigate to the directory**:
    ```bash
    cd /path/to/ai-loadbalancer/rl_agent/offline-training
    ```

2.  **Run the script**:
    ```bash
    python train.py
    ```

**Expected Output**:

You will see logs indicating the progress of each step: loading data, preprocessing, training epochs with loss and validation performance, and finally, a confirmation that the models have been saved and copied to the production directory.
