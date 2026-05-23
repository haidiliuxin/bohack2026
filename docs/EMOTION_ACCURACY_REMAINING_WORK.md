# NeuroGarden 情绪判断改进收尾与未完成功能

## 1. 本轮已完成

### P0：当前情绪判断链路可靠化

已完成：

- 新增 `SignalPreprocessor`，统一进行信号清洗、裁剪、缺失标记、数据质量判断和个人基线偏离计算。
- 新增 `EmotionLabelNormalizer`，把 Agent 和用户反馈中的开放情绪标签归一到稳定标签体系。
- 扩展 `AgentSignalRequest`，增加：
  - `cleanedSignalSummary`
  - `baselineDeviationPercent`
  - `dataQuality`
  - `dataLimits`
  - `localEmotionGuess`
- 扩展 `AgentSignalResponse`，增加：
  - `primaryEmotion`
  - `secondaryEmotions`
  - `emotionFamily`
  - `valence`
  - `arousal`
  - `stress`
  - `fatigue`
  - `loneliness`
  - `observedClues`
  - `counterEvidence`
  - `uncertainty`
  - `supportStyle`
  - `thresholdAdvice`
- 更新 MiniMax Agent Prompt，要求输出结构化多元情绪 JSON。
- 更新 `AgentResponseNormalizer`，对模型输出进行标签归一、诊断词清洗和字段兜底。
- 今日页情绪卡展示主情绪、次情绪、情绪族群、证据、反证和不确定性。
- 风险事件 `agentAnalysis` 中写入 Agent 情绪判断和不确定性。

当前效果：

- 不再只显示“正常 / 异常”。
- 能表达平静、专注、轻松、积极活跃、疲惫、紧张、烦躁、低落、孤独、空落、压力偏高、不确定等状态。
- 低质量数据会降低置信度，不会被强行解释成明确情绪。

### P1：场景化误判控制

已完成：

- `SignalPreprocessor` 支持识别场景：
  - `chat_app`
  - `video_app`
  - `game_app`
  - `browser_app`
  - `social_app`
  - `productivity_app`
  - `unknown`
- 聊天场景下，提高删除率、停顿时长对紧张/烦躁的解释权。
- 视频和游戏场景下，降低心率和输入波动直接导向负面情绪的权重。
- 深夜和连续使用场景下，提高疲惫、空落、恢复需求候选权重。
- 短时间多场景切换时，提高压力或分心候选权重。
- 后台守护服务的输入节奏风险加入场景权重，降低视频/游戏场景误报。

当前效果：

- 运动、视频、游戏和专注输入不再容易被直接判成负面情绪。
- 聊天中的高删除率和长停顿更容易进入“紧张 / 烦躁 / 压力偏高”的候选。

### P2：用户反馈学习与评估记录

已完成：

- 新增 `emotion_evaluation_records` 表。
- 新增 `EmotionEvaluationRecordEntity`，记录：
  - 系统预测主情绪
  - 系统预测次情绪
  - 用户纠正情绪
  - 置信度
  - valence / arousal / stress / fatigue / loneliness
  - 信号摘要
  - 场景摘要
  - Agent 版本
  - 用户是否接受判断
- 用户在应用中主动标注情绪时，会写入情绪评估记录。
- 设置页“情绪识别验收”卡片展示：
  - 评估样本数
  - 用户接受数
  - 接受率
  - 最近纠正路径
- 原有 `EmotionCalibrationEngine` 继续参考最近反馈，对下次情绪判断进行轻量校准。

当前效果：

- 用户纠正“不是烦躁，是疲惫”后，系统有结构化记录。
- 后续可以基于这些记录形成离线评估集。
- 当前仍是轻量学习，不是真正模型微调。

## 2. 本轮没有完全完成的功能

### P3：长期评估集与真实准确率评估

未完全完成。

原因：

- 需要连续多天真实用户授权数据。
- 需要用户多次主动标注真实感受。
- 当前只有记录表和验收卡，还没有形成完整离线评估脚本。
- 当前“准确率”仍是用户接受率和文本匹配粗略指标，不是严格实验指标。

后续建议：

- 新增导出评估集功能，把 `emotion_evaluation_records`、`habit_samples`、`risk_events`、`agent_audit_logs` 组合导出为 CSV 或 JSON。
- 构建标准样本集：
  - 平静
  - 专注
  - 疲惫
  - 烦躁
  - 紧张
  - 低落
  - 孤独
  - 运动干扰
  - 视频/游戏高唤醒
  - 深夜空落
- 每次修改 Prompt 或算法后，用同一批样本回放评估。

### P4：真实 Wear OS 高质量生理数据增强

未完成。

原因：

- 当前项目仍主要依赖模拟手表数据和 Wear Data Layer 传入数据。
- 真实 HRV、睡眠阶段、静息心率趋势、运动后恢复速度需要 Wear OS Health Services 或厂商健康平台能力。
- 这部分需要真实手表调试和权限验证。

后续建议：

- 接入 Wear OS Health Services：
  - heart rate
  - HRV 或 RR interval，如果设备支持
  - activity / exercise state
  - sleep summary，如果可用
- 手机端新增字段：
  - `hrv`
  - `restingHeartRate`
  - `sleepDuration`
  - `sleepQuality`
  - `recoveryAfterExercise`
- `SignalPreprocessor` 中增加 HRV 和睡眠权重。

### P5：模型微调或小模型蒸馏

未完成。

原因：

- 目前没有足够规模、授权明确、脱敏后的用户样本。
- 直接微调 MiniMax 或训练本地模型，容易把数据偏差固化，反而降低准确性。
- 当前比赛阶段更适合使用 Prompt、Normalizer、本地规则和用户反馈闭环。

后续建议：

- 先完成 P3 评估集。
- 至少积累数百条用户授权的结构化情绪纠正样本。
- 对比三种方案：
  - 只改 Prompt
  - Prompt + 本地 Normalizer
  - 小模型分类器或微调模型
- 微调前必须保证：
  - 不含输入原文
  - 不含聊天文本
  - 不含未授权健康原始数据
  - 可以离线评估和回滚

## 3. 当前准确性边界

当前版本可以做到：

- 比之前更准确地区分多元情绪候选。
- 能把情绪判断建立在个人基线、场景、数据质量和 Agent 结构化分析上。
- 能展示证据、反证和不确定性。
- 能保存用户纠正，为后续学习做准备。

当前版本不能保证：

- 不能医学诊断用户情绪障碍。
- 不能百分百确认用户真实感受。
- 不能在没有真实长期数据的情况下给出高置信长期心理画像。
- 不能仅靠心率或打字节奏判断复杂情绪。

建议答辩表达：

> NeuroGarden 不是诊断系统，而是个体化状态偏离和情绪线索识别系统。它通过手机和手表端授权采集的结构化数据，结合个人基线、本地算法和 LLM 结构化解释，给出多元情绪候选、置信度、证据和不确定性，并通过用户反馈持续校准。

## 4. 下一轮优先级

建议下一轮按以下顺序继续：

1. 做评估集导出和回放测试。
2. 接入真实 Wear OS Health Services。
3. 给 `emotion_evaluation_records` 增加详情页和筛选。
4. 把 Agent Prompt 版本号写入审计日志。
5. 用 20-50 条人工构造样本做 Prompt 回归测试。
6. 等真实样本足够后，再考虑微调或小模型。
