-- NeuroGarden 多日验收测试数据（待审核）
-- 先不要导入生产设备。审核通过后，建议接入 Demo Mode 或在测试库 neurogarden.db 中执行。

BEGIN TRANSACTION;

INSERT INTO user_habit_baselines (avgRestingHeartRate, avgBreathRate, avgTypingSpeed, avgDeleteRate, avgPauseDuration, commonActiveStartHour, commonActiveEndHour, avgRecoveryDuration, sampleCount, confidenceLevel, createdAt, updatedAt)
VALUES (72.0, 12.5, 108.0, 0.05, 1.6, 8, 23, 14.0, 42, 'high', 1778979600000, 1779494400000);

INSERT INTO threshold_profiles (heartRateDeltaWarning, breathRateWarning, typingSpeedDeltaWarning, deleteRateWarning, pauseDurationWarning, riskTriggerDuration, guardianNotifyThreshold, updatedBy, updatedReason, updatedAt)
VALUES (18.0, 7.0, 0.30, 0.14, 3.5, 15, 0.78, 'seed_review', 'multi_day_acceptance_dataset', 1779494400000);

-- 2026-05-17 stable day
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1778979600000, 70, 12, 0.08, 112.0, 0.04, 1.2, NULL, 'seed_stable_wear_mock', 'stable', 1778979600000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779004800000, 74, 13, 0.12, 105.0, 0.05, 1.7, NULL, 'seed_stable_wear_mock', 'stable', 1779004800000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779022800000, 71, 12, 0.06, 101.0, 0.05, 1.8, NULL, 'seed_stable_wear_mock', 'stable', 1779022800000);
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1778979600000, 70, 12, 0.08, 0.18, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779004800000, 74, 13, 0.12, 0.20, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779022800000, 71, 12, 0.06, 0.16, 0.82, 'stable');

-- 2026-05-18 mild wave
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779066000000, 72, 12, 0.09, 108.0, 0.05, 1.5, NULL, 'seed_mild_wave_wear_real', 'stable', 1779066000000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779091200000, 84, 15, 0.16, 82.0, 0.11, 2.9, NULL, 'seed_mild_wave_wear_real', 'observe', 1779091200000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779109200000, 76, 13, 0.08, 96.0, 0.07, 2.0, NULL, 'seed_mild_wave_wear_real', 'stable', 1779109200000);
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779066000000, 72, 12, 0.09, 0.18, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779091200000, 84, 15, 0.16, 0.42, 0.82, 'observe');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779109200000, 76, 13, 0.08, 0.24, 0.82, 'stable');
INSERT INTO risk_events (startTime, endTime, riskScore, riskLevel, confidence, mainReasons, metricDeviationPercent, heartRateDeviationPercent, breathRateDeviationPercent, typingSpeedDeviationPercent, deleteRateDeviationPercent, pauseDurationDeviationPercent, motionLevel, weather, timeSegment, agentAnalysis, suggestedAction, guardianNotified, guardianFeedback, isFalseAlarm, createdAt)
VALUES (1779091200000, 1779092280000, 0.48, 'observe', 0.72, '打字速度偏离个人习惯|删除频率升高', 'heartRate=17;breathRate=20;typingSpeed=-24;deleteRate=120;pauseDuration=81', 17, 20, -24, 120, 81, 0.16, '小雨 21C 湿度78% 上海 source=Real', 'afternoon', 'source=seed_review;reason=multi_day_acceptance_dataset', '建议稍后查看状态摘要。', 0, NULL, 0, 1779091200000);

-- 2026-05-19 recovery stable
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779152400000, 69, 12, 0.08, 113.0, 0.04, 1.3, NULL, 'seed_recovery_wear_real', 'stable', 1779152400000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779177600000, 75, 13, 0.14, 104.0, 0.05, 1.8, NULL, 'seed_recovery_wear_real', 'stable', 1779177600000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779195600000, 70, 12, 0.06, 102.0, 0.05, 1.7, NULL, 'seed_recovery_wear_real', 'stable', 1779195600000);
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779152400000, 69, 12, 0.08, 0.17, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779177600000, 75, 13, 0.14, 0.21, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779195600000, 70, 12, 0.06, 0.16, 0.82, 'stable');

-- 2026-05-20 night event
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779238800000, 71, 12, 0.08, 113.0, 0.04, 1.3, NULL, 'seed_night_event_wear_real', 'stable', 1779238800000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779264000000, 77, 14, 0.11, 98.0, 0.07, 2.1, NULL, 'seed_night_event_wear_real', 'stable', 1779264000000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779285600000, 99, 21, 0.08, 58.0, 0.23, 6.2, NULL, 'seed_night_event_wear_real', 'guardian_check', 1779285600000);
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779238800000, 71, 12, 0.08, 0.18, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779264000000, 77, 14, 0.11, 0.24, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779285600000, 99, 21, 0.08, 0.79, 0.84, 'guardian_check');
INSERT INTO risk_events (startTime, endTime, riskScore, riskLevel, confidence, mainReasons, metricDeviationPercent, heartRateDeviationPercent, breathRateDeviationPercent, typingSpeedDeviationPercent, deleteRateDeviationPercent, pauseDurationDeviationPercent, motionLevel, weather, timeSegment, agentAnalysis, suggestedAction, guardianNotified, guardianFeedback, isFalseAlarm, createdAt)
VALUES (1779285600000, 1779288120000, 0.79, 'guardian_check', 0.84, '心率高于个人基线|呼吸频率高于个人基线|停顿时长增加', 'heartRate=35;breathRate=68;typingSpeed=-43;deleteRate=360;pauseDuration=260', 35, 68, -43, 360, 260, 0.08, '阴 22C 湿度69% 上海 source=Real', 'night', 'source=seed_review;reason=multi_day_acceptance_dataset', '建议进行一次温和状态确认。', 1, '已联系本人', 0, 1779285600000);

-- 2026-05-21 motion noise and false alarm
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779325200000, 74, 13, 0.10, 106.0, 0.05, 1.4, NULL, 'seed_motion_noise_wear_real', 'stable', 1779325200000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779350400000, 104, 22, 0.82, 88.0, 0.09, 2.4, NULL, 'seed_motion_noise_wear_real', 'observe', 1779350400000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779368400000, 76, 13, 0.10, 99.0, 0.06, 1.8, NULL, 'seed_motion_noise_wear_real', 'stable', 1779368400000);
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779325200000, 74, 13, 0.10, 0.18, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779350400000, 104, 22, 0.82, 0.44, 0.48, 'observe');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779368400000, 76, 13, 0.10, 0.22, 0.82, 'stable');
INSERT INTO risk_events (startTime, endTime, riskScore, riskLevel, confidence, mainReasons, metricDeviationPercent, heartRateDeviationPercent, breathRateDeviationPercent, typingSpeedDeviationPercent, deleteRateDeviationPercent, pauseDurationDeviationPercent, motionLevel, weather, timeSegment, agentAnalysis, suggestedAction, guardianNotified, guardianFeedback, isFalseAlarm, createdAt)
VALUES (1779350400000, 1779351600000, 0.44, 'observe', 0.48, '运动干扰导致置信度下降|心率高于个人基线', 'heartRate=42;breathRate=76;typingSpeed=-19;deleteRate=100;pauseDuration=50', 42, 76, -19, 100, 50, 0.82, '晴 27C 湿度45% 上海 source=Real', 'afternoon', 'source=seed_review;reason=multi_day_acceptance_dataset', '运动干扰较高，仅作为观察记录。', 0, '标记误报', 1, 1779350400000);

-- 2026-05-22 family guardian check
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779411600000, 73, 13, 0.09, 106.0, 0.05, 1.5, NULL, 'seed_family_check_wear_real', 'stable', 1779411600000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779436800000, 93, 18, 0.10, 69.0, 0.18, 4.9, NULL, 'seed_family_check_wear_real', 'guardian_check', 1779436800000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779454800000, 84, 15, 0.08, 82.0, 0.10, 3.0, NULL, 'seed_family_check_wear_real', 'observe', 1779454800000);
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779411600000, 73, 13, 0.09, 0.18, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779436800000, 93, 18, 0.10, 0.74, 0.80, 'guardian_check');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779454800000, 84, 15, 0.08, 0.42, 0.82, 'observe');
INSERT INTO risk_events (startTime, endTime, riskScore, riskLevel, confidence, mainReasons, metricDeviationPercent, heartRateDeviationPercent, breathRateDeviationPercent, typingSpeedDeviationPercent, deleteRateDeviationPercent, pauseDurationDeviationPercent, motionLevel, weather, timeSegment, agentAnalysis, suggestedAction, guardianNotified, guardianFeedback, isFalseAlarm, createdAt)
VALUES (1779436800000, 1779438480000, 0.74, 'guardian_check', 0.80, '心率高于个人基线|删除频率升高|停顿时长增加', 'heartRate=29;breathRate=44;typingSpeed=-36;deleteRate=260;pauseDuration=206', 29, 44, -36, 260, 206, 0.10, '多云 24C 湿度61% 上海 source=Real', 'afternoon', 'source=seed_review;reason=multi_day_acceptance_dataset', '建议守护人进行一次状态确认。', 1, '确认异常', 0, 1779436800000);

-- 2026-05-23 special care
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779498000000, 74, 13, 0.08, 102.0, 0.06, 1.8, NULL, 'seed_special_care_wear_real', 'stable', 1779498000000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779523200000, 97, 19, 0.09, 62.0, 0.21, 5.8, NULL, 'seed_special_care_wear_real', 'guardian_check', 1779523200000);
INSERT INTO habit_samples (timestamp, heartRate, breathRate, motionLevel, typingSpeed, deleteRate, pauseDuration, userFeedback, contextTag, riskLevel, createdAt) VALUES (1779541200000, 92, 18, 0.07, 67.0, 0.18, 5.0, NULL, 'seed_special_care_wear_real', 'support', 1779541200000);
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779498000000, 74, 13, 0.08, 0.18, 0.82, 'stable');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779523200000, 97, 19, 0.09, 0.82, 0.86, 'guardian_check');
INSERT INTO sensor_records (timestamp, heartRate, breathRate, motionLevel, stressScore, confidence, state) VALUES (1779541200000, 92, 18, 0.07, 0.62, 0.82, 'support');
INSERT INTO risk_events (startTime, endTime, riskScore, riskLevel, confidence, mainReasons, metricDeviationPercent, heartRateDeviationPercent, breathRateDeviationPercent, typingSpeedDeviationPercent, deleteRateDeviationPercent, pauseDurationDeviationPercent, motionLevel, weather, timeSegment, agentAnalysis, suggestedAction, guardianNotified, guardianFeedback, isFalseAlarm, createdAt)
VALUES (1779523200000, 1779525300000, 0.82, 'guardian_check', 0.86, '呼吸频率高于个人基线|打字速度偏离个人习惯|停顿时长增加', 'heartRate=35;breathRate=52;typingSpeed=-43;deleteRate=320;pauseDuration=263', 35, 52, -43, 320, 263, 0.09, '小雨 20C 湿度82% 上海 source=Real', 'afternoon', 'source=seed_review;reason=multi_day_acceptance_dataset', '建议照护者进行确认，并保持温和陪伴。', 1, '继续观察', 0, 1779523200000);

-- feedback records
INSERT INTO feedback_records (timestamp, predictedRiskLevel, predictedState, userLabel, timingFeedback, helpful, source, createdAt) VALUES (1779109200000, 'observe', '轻度波动', '有点累', '刚好', 1, 'emotion_label', 1779109200000);
INSERT INTO feedback_records (timestamp, predictedRiskLevel, predictedState, userLabel, timingFeedback, helpful, source, createdAt) VALUES (1779285600000, 'guardian_check', '低落疲惫', '需要陪伴', '及时', 1, 'guardian_dashboard', 1779285600000);
INSERT INTO feedback_records (timestamp, predictedRiskLevel, predictedState, userLabel, timingFeedback, helpful, source, createdAt) VALUES (1779354000000, 'observe', '运动干扰', '误报', '太早', 0, 'guardian_dashboard', 1779354000000);
INSERT INTO feedback_records (timestamp, predictedRiskLevel, predictedState, userLabel, timingFeedback, helpful, source, createdAt) VALUES (1779438600000, 'guardian_check', '高压紧张', '提醒有效', '刚好', 1, 'guardian_dashboard', 1779438600000);

COMMIT;
