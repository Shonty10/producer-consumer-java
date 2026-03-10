# Assignment: Producer-Consumer System

## Overview
Thread-safe producer-consumer implementation demonstrating:
- Bounded queue with capacity enforcement (10 items)
- Proper synchronization using `ReentrantLock` with separate `Condition` objects
- Graceful shutdown and complete data transfer guarantee
- Real-time status monitoring

## Design Highlights
- **Two separate Conditions** (`notFull` / `notEmpty`) instead of one to avoid spurious wakeups
- **Fair lock** to prevent thread starvation
- **`signalAll()` over `signal()`** to avoid deadlock with multiple waiting threads
- **Close-then-drain pattern**: Producer finishes → queue closes → consumer drains remaining items

## How to Run
```bash
cd assignment1_producer_consumer
javac -encoding UTF-8 *.java
java ProducerConsumerMain
```

## Expected Output
- 25 items transferred successfully
- Queue reaches 100% capacity (demonstrates back-pressure)
- Producer blocks when full, consumer blocks when empty
- Real-time status updates every second
- Clean shutdown with all items accounted for

## Key Classes
- **SharedQueue**: Bounded blocking queue with lock/condition synchronization
- **Producer**: Reads from source list, enqueues items with simulated delay
- **Consumer**: Dequeues items, stores in destination with simulated processing
- **DataTransferManager**: Orchestrates lifecycle, handles graceful shutdown
