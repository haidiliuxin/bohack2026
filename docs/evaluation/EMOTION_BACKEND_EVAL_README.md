# NeuroGarden Emotion Backend Evaluation

## Dataset

- `neurogarden_emotion_test_100.jsonl`
- 100 synthetic/benchmark-style structured samples.
- Labels are balanced across 10 classes:
  - 平静
  - 专注
  - 紧张
  - 烦躁
  - 焦虑
  - 疲惫
  - 低落
  - 空落
  - 积极活跃
  - 运动干扰

The dataset contains structured phone/watch context only. It does not contain raw user input text.

## Current Optimized Result

Stored in:

- `emotion_backend_eval_100_v4_optimized_complete.json`

Summary:

```text
total: 100
completed: 100
errors: 0
strictAccuracy: 0.66
familyAccuracy: 0.96
schemaCompliance: 1.00
localRuleCount: 18
```

Per-label accuracy:

```text
平静: 60%
专注: 100%
紧张: 20%
烦躁: 70%
焦虑: 70%
疲惫: 70%
低落: 30%
空落: 40%
积极活跃: 100%
运动干扰: 100%
```

## Re-run

Make sure `key.txt` exists in the project root:

```text
URL=https://api.minimaxi.com/anthropic
APIkey=...
MODEL=MiniMax-M2.7
```

Then run:

```bash
node docs/evaluation/run_emotion_backend_eval.js
```

The script writes:

```text
docs/evaluation/emotion_backend_eval_latest.json
```

## Notes

This evaluation uses:

- closed-set emotion labels
- few-shot boundary examples
- local motion override
- local irritation/anxiety boundary rules

The result is not a clinical accuracy metric. It only measures how well the current structured backend matches this benchmark label set.
