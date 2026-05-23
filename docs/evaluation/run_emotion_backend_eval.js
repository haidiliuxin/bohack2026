const fs = require("fs");
const path = require("path");

const repo = path.resolve(__dirname, "../..");
const datasetPath = path.join(__dirname, "neurogarden_emotion_test_100.jsonl");
const outputPath = path.join(__dirname, "emotion_backend_eval_latest.json");
const keyText = fs.readFileSync(path.join(repo, "key.txt"), "utf8");
const secrets = Object.fromEntries(
  keyText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && line.includes("="))
    .map((line) => {
      const index = line.indexOf("=");
      return [line.slice(0, index).trim(), line.slice(index + 1).trim()];
    })
);

const apiKey = secrets.APIkey || "";
const baseUrl = (secrets.URL || "").replace(/\/+$/, "");
const model = secrets.MODEL || "MiniMax-M2.7";
const endpoint = /\/v1\/messages$/i.test(baseUrl) ? baseUrl : `${baseUrl}/v1/messages`;
const allowed = ["平静", "专注", "紧张", "烦躁", "焦虑", "疲惫", "低落", "空落", "积极活跃", "运动干扰"];

const systemPrompt = `你是 NeuroGarden 的结构化情绪分类后端。只根据授权的手机/手表结构化特征判断，不诊断疾病，不推断文本内容。
你必须把 primaryEmotion 分类为以下十个中文标签之一，不能输出其它标签：平静、专注、紧张、烦躁、焦虑、疲惫、低落、空落、积极活跃、运动干扰。

边界规则：
1. motionLevel >= 0.60 时，优先考虑“运动干扰”，除非输入节奏极端异常且 motionLevel 只是中等。
2. 烦躁：常见于 chat_app/social_app，高删除率、快速输入、呼吸偏快，像“反复修改/急躁”。
3. 焦虑：常见于高心率 + 高呼吸 + 长停顿/输入变慢，像“卡住、担心、身体唤醒高”。
4. 紧张：中高唤醒但未达到焦虑，常见于 productivity/browser 场景，任务压力或临场压力。
5. 疲惫：输入速度降低、停顿增长、心率/呼吸不一定高，更像能量不足。
6. 低落：低唤醒负向，输入慢、停顿长、心率不高，整体偏沉。
7. 空落：深夜/夜间、低社交/长停顿、低能量，偏孤独或空荡感。
8. 积极活跃：高输入/高唤醒但删除率低、停顿短，或者娱乐/游戏场景下高唤醒但非负向。
9. 专注：输入稳定偏快、删除率低、停顿短，心率呼吸正常。
10. 平静：心率呼吸低且稳定，输入强度不高，整体低唤醒稳定。

只返回紧凑 JSON，不要 Markdown，不要额外文字：{"primaryEmotion":"标签","confidence":0.0,"riskLevel":"low|medium|high","ruleReason":"短原因"}`;

const fewShot = [
  [{ contextTag: "chat_app", timeSegment: "afternoon", heartRate: 100, breathRate: 23, motionLevel: 0.24, typingSpeedCpm: 195, deleteRate: 0.81, pauseDurationSec: 2.3 }, "烦躁"],
  [{ contextTag: "browser_app", timeSegment: "afternoon", heartRate: 120, breathRate: 30, motionLevel: 0.42, typingSpeedCpm: 66, deleteRate: 0.43, pauseDurationSec: 10.3 }, "焦虑"],
  [{ contextTag: "productivity_app", timeSegment: "morning", heartRate: 92, breathRate: 18, motionLevel: 0.18, typingSpeedCpm: 118, deleteRate: 0.18, pauseDurationSec: 2.5 }, "紧张"],
  [{ contextTag: "productivity_app", timeSegment: "night", heartRate: 72, breathRate: 13, motionLevel: 0.08, typingSpeedCpm: 42, deleteRate: 0.12, pauseDurationSec: 9.2 }, "疲惫"],
  [{ contextTag: "browser_app", timeSegment: "late_night", heartRate: 68, breathRate: 12, motionLevel: 0.05, typingSpeedCpm: 35, deleteRate: 0.08, pauseDurationSec: 12 }, "空落"],
  [{ contextTag: "game_app", timeSegment: "night", heartRate: 108, breathRate: 20, motionLevel: 0.22, typingSpeedCpm: 150, deleteRate: 0.05, pauseDurationSec: 1.0 }, "积极活跃"],
  [{ contextTag: "fitness_app", timeSegment: "afternoon", heartRate: 125, breathRate: 26, motionLevel: 0.78, typingSpeedCpm: 80, deleteRate: 0.1, pauseDurationSec: 2 }, "运动干扰"]
].flatMap(([input, label]) => [
  { role: "user", content: JSON.stringify(input) },
  { role: "assistant", content: JSON.stringify({ primaryEmotion: label, confidence: 0.76, riskLevel: label === "运动干扰" ? "low" : "medium", ruleReason: "few-shot boundary example" }) }
]);

function canonical(value) {
  const text = String(value || "").trim().toLowerCase();
  if (!text) return "未知";
  for (const label of allowed) if (text === label.toLowerCase() || text.includes(label.toLowerCase())) return label;
  if (text.includes("motion") || text.includes("exercise") || text.includes("movement")) return "运动干扰";
  if (text.includes("anxiety") || text.includes("anxious")) return "焦虑";
  if (text.includes("tense") || text.includes("stress") || text.includes("pressure")) return "紧张";
  if (text.includes("irrit") || text.includes("frustrat") || text.includes("annoy")) return "烦躁";
  if (text.includes("fatigue") || text.includes("tired") || text.includes("exhaust")) return "疲惫";
  if (text.includes("sad") || text.includes("depress") || text.includes("down")) return "低落";
  if (text.includes("empty") || text.includes("hollow") || text.includes("lonely")) return "空落";
  if (text.includes("active") || text.includes("excited") || text.includes("energetic")) return "积极活跃";
  if (text.includes("focus") || text.includes("flow") || text.includes("engaged") || text.includes("productive")) return "专注";
  if (text.includes("calm") || text.includes("stable") || text.includes("relax")) return "平静";
  return text.slice(0, 24);
}

function family(value) {
  const label = canonical(value);
  if (["紧张", "焦虑", "烦躁"].includes(label)) return "高唤醒负向";
  if (["疲惫", "低落", "空落"].includes(label)) return "低唤醒负向/疲惫";
  if (["平静", "专注"].includes(label)) return "稳定/专注";
  if (label === "积极活跃") return "高唤醒正向";
  if (label === "运动干扰") return "运动干扰";
  return "其他";
}

function localOverride(meta) {
  if (meta.motion_level >= 0.60) return { primaryEmotion: "运动干扰", confidence: 0.88, riskLevel: "low", source: "local_motion_rule" };
  if ((meta.context_tag.includes("chat") || meta.context_tag.includes("social")) && meta.delete_rate >= 0.55 && meta.typing_speed_cpm >= 135 && meta.pause_duration_sec <= 5.5) {
    return { primaryEmotion: "烦躁", confidence: 0.82, riskLevel: "high", source: "local_irritation_rule" };
  }
  if (meta.heart_rate >= 112 && meta.breath_rate >= 24 && meta.pause_duration_sec >= 6 && meta.motion_level < 0.55) {
    return { primaryEmotion: "焦虑", confidence: 0.82, riskLevel: "high", source: "local_anxiety_rule" };
  }
  return null;
}

function extractJson(text) {
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)\s*```/i);
  if (fenced) text = fenced[1];
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start >= 0 && end > start) text = text.slice(start, end + 1);
  return JSON.parse(text);
}

async function callLlm(meta) {
  const input = {
    contextTag: meta.context_tag,
    timeSegment: meta.time_segment,
    heartRate: meta.heart_rate,
    breathRate: meta.breath_rate,
    motionLevel: meta.motion_level,
    typingSpeedCpm: meta.typing_speed_cpm,
    deleteRate: meta.delete_rate,
    pauseDurationSec: meta.pause_duration_sec,
    temperatureC: meta.environment_temperature_c,
    humidityPercent: meta.humidity_percent,
    missingSignals: meta.missing_signals
  };
  const body = {
    model,
    max_tokens: 1200,
    temperature: 0,
    system: systemPrompt,
    messages: [...fewShot, { role: "user", content: JSON.stringify(input) }]
  };
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}`, "X-Api-Key": apiKey },
    body: JSON.stringify(body)
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 200)}`);
  const wrapper = JSON.parse(text);
  const content = (wrapper.content || []).filter((block) => block.type === "text").map((block) => block.text).join("\n");
  return extractJson(content);
}

async function predict(row) {
  const local = localOverride(row.meta);
  if (local) return { ...local, usedLocal: true };
  const json = await callLlm(row.meta);
  return {
    primaryEmotion: json.primaryEmotion || json.primary_emotion || json.label || "",
    confidence: Number(json.confidence || 0),
    riskLevel: json.riskLevel || json.risk_level || "",
    source: "llm_fewshot",
    usedLocal: false
  };
}

async function main() {
  const rows = fs.readFileSync(datasetPath, "utf8").split(/\r?\n/).filter(Boolean).map(JSON.parse);
  const results = [];
  const errors = [];
  let next = 0;
  async function worker() {
    while (next < rows.length) {
      const index = next++;
      try {
        const meta = rows[index].meta;
        const prediction = await predict(rows[index]);
        results[index] = {
          id: meta.id,
          label: meta.label,
          predRaw: prediction.primaryEmotion,
          pred: canonical(prediction.primaryEmotion),
          schemaCompliant: allowed.includes(String(prediction.primaryEmotion).trim()),
          confidence: prediction.confidence,
          riskGold: meta.risk_level,
          riskPred: prediction.riskLevel,
          usedLocal: prediction.usedLocal,
          source: prediction.source
        };
      } catch (error) {
        errors.push({ index, id: rows[index].meta.id, error: error.message });
      }
    }
  }
  await Promise.all(Array.from({ length: 4 }, worker));
  const completed = results.filter(Boolean);
  const labels = [...new Set(rows.map((row) => row.meta.label))];
  const strictCorrect = completed.filter((row) => row.pred === row.label).length;
  const familyCorrect = completed.filter((row) => family(row.pred) === family(row.label)).length;
  const output = {
    total: rows.length,
    completed: completed.length,
    errors,
    strictAccuracy: strictCorrect / completed.length,
    familyAccuracy: familyCorrect / completed.length,
    schemaCompliance: completed.filter((row) => row.schemaCompliant).length / completed.length,
    localRuleCount: completed.filter((row) => row.usedLocal).length,
    perLabel: Object.fromEntries(labels.map((label) => {
      const subset = completed.filter((row) => row.label === label);
      const correct = subset.filter((row) => row.pred === label).length;
      return [label, { n: subset.length, correct, acc: subset.length ? correct / subset.length : 0 }];
    })),
    confusion: {},
    results: completed
  };
  for (const row of completed) {
    output.confusion[row.label] ||= {};
    output.confusion[row.label][row.pred] = (output.confusion[row.label][row.pred] || 0) + 1;
  }
  fs.writeFileSync(outputPath, JSON.stringify(output, null, 2), "utf8");
  console.log(JSON.stringify({
    completed: output.completed,
    errors: output.errors.length,
    strictAccuracy: output.strictAccuracy,
    familyAccuracy: output.familyAccuracy,
    schemaCompliance: output.schemaCompliance,
    localRuleCount: output.localRuleCount,
    outputPath
  }, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
