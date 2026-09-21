# AGENT.md — 语音呼出的个人 agent（Hermes）on Rokid Glasses

design draft · 2026-09-19 · 对齐用

目标：眼镜负责**听、看、念**，手机负责**想**。用户说一句话/做个手势，把个人的 Hermes agent 叫出来，回复显示在眼镜上（短），重内容（图片、长文、链接）落到手机 app。

背景与更完整的调研见 [`RESEARCH-extensions.md`](RESEARCH-extensions.md)。

---

## 1. 两条接入路线（先定这个）

眼镜**不能**直连 Hermes。两种可达路径：

| | A. 直连 Hermes Gateway | B. 灵珠（Rizon）bridge |
|---|---|---|
| 链路 | 手机/眼镜 → `https://<gw>/v1/chat/completions`（OpenAI 兼容，SSE） | 眼镜 → Rokid AI App → 灵珠 → 公网 `https://<域名>/api/sse` → bridge → Hermes |
| 鉴权 | `HERMES_API_KEY`（Bearer） | `ROKID_AK`（Bearer，自己生成） |
| 需要 | Gateway 网络可达（同 LAN，或公网/VPN） | 公网 HTTPS 域名 + 灵珠「自定义智能体」配置 |
| 换来 | 最简单、我们完全掌控、无审批 | 系统语音助手可驱动；不用 VPN 也能公网到达；已被开源验证 |
| 适合 | 我们自研 app 的 **Agent tab** | 「Hi Rokid 直接呼出」/ 公开发布 |

**建议：抽象成一个 `AgentClient` 接口，两条都实现。**
- **P1 先做 A（直连 Gateway）**，因为 Agent tab 在我们自己的 app 里，不需要灵珠和公网审批，联调最快。
- **P4 再加 B（灵珠）**，因为它才是「Rokid 语音助手呼出 → 连 agent」的正路（见 §3）。
- 两者都是「发消息 → 流式收文本」，可共用一套流式解析抽象，只是 URL/鉴权/事件格式不同。

参考实现（B 路线的协议与部署）：`github.com/xingdongcai/rokid-hermes-bridge`、`github.com/h4dex/rokid-hermes-bridge`。

### 1.1 连接你自己的 Hermes（A 路线，实测口径）

> Slack 里的 bot ≠ API server。两者是同一个 gateway 的不同 adapter；**API server 默认关闭**，手机连的是它。

Hermes 侧的接口（来自官方 `hermes-agent` 文档）：
- 端口 `8642`，路径 `POST /v1/chat/completions`（OpenAI 兼容）、`GET /v1/models`、`GET /health`
- 鉴权：`Authorization: Bearer <API_SERVER_KEY>`
- 模型名：默认 profile 名，或 env `API_SERVER_MODEL_NAME`；默认 `hermes-agent`
- 流式：SSE `chat.completion.chunk`（`choices[0].delta.content`），会夹杂 `: keepalive` 注释行和 `event: hermes.tool.progress` 自定义事件（客户端要跳过非 `data:` / 非 choices 的行）
- 图片输入：`content` 数组里的 `{"type":"image_url","image_url":{"url":...}}`，支持 http(s) 和 `data:image/...`

**Hermes 侧要做的（一次）** —— 在 `~/.hermes/.env`：
```
API_SERVER_ENABLED=true
API_SERVER_KEY=<>=16 位随机串, openssl rand -hex 32>
API_SERVER_HOST=0.0.0.0        # 只在需要局域网直连时；默认 127.0.0.1
API_SERVER_PORT=8642
# API_SERVER_MODEL_NAME=hermes-agent
```
然后 `hermes gateway`（重启），本地自测：
```bash
curl -s http://127.0.0.1:8642/health
curl -s http://127.0.0.1:8642/v1/models -H "Authorization: Bearer $API_SERVER_KEY"
```

**手机能访问的三种方式**（任选其一）：
1. **同 Wi-Fi 直连**：`API_SERVER_HOST=0.0.0.0`，用运行 Hermes 那台机器的局域网 IP，如 `http://192.168.1.24:8642`。（安全组/防火墙放行 8642，仅内网）
2. **SSH 隧道**：`ssh -N -L 8642:127.0.0.1:8642 user@host`，手机用隧道另一端的地址（需手机侧有隧道，通常不如 1 方便）。
3. **公网 HTTPS**：Cloudflare Tunnel / nginx 反代 8642，手机填 `https://<域名>`。

**App 侧（已完成）**：Agent tab → Backend `Hermes` → 填 gateway URL（如 `http://192.168.1.24:8642`）、API key、model（`hermes-agent`）→ 点 **Test**（打 `/v1/models`）确认，再发消息。

**App 侧还差（P5）**：
- 「带上最近这段 convo」按钮（把 `memory` 里的对话作为上下文发给 agent）
- 「用眼镜拍一张」：CXR 拍照 → base64 `data:image/...` → Hermes 视觉
- 眼镜端流式显示已接（`agent_stream`），图片仍只在手机
- 灵珠（B 路线）client 未实现（只有 A）

### 两种事件格式（差异点）
- **A（OpenAI 兼容 SSE）**：`data: {"choices":[{"delta":{"content":"…"}}]}`，逐 delta 拼接，`data: [DONE]` 结束。
- **B（灵珠 SSE）**：必须 `event:message` 开头，`data:{"role":"agent","type":"answer","answer_stream":"…","message_id":"…","agent_id":"…","is_finish":false}`，`follow_up?:string[]` 可选。请求带 `message_id`/`agent_id`/`metadata`，响应须原样回传这两个 id。

---

## 2. 手机端：新增 `Agent` tab

现有 tab：Swipe（机会日历）/ Desk（设置+任务）/ Convo（对话历史）。新增第四个 **Agent**。

```
[Agent]
  ├─ 对话流：用户 / agent（流式逐字）
  ├─ 图片消息：手机内联渲染 + 点击全屏缩放   ← 解决"眼镜看图片难看"
  ├─ 「带上最近这段 convo」按钮：把当前 Convo tab 的内容作为上下文发给 agent
  ├─ 「用眼镜拍一张」按钮：调 CXR 拍照 → 作为图片输入给 agent（视觉模型）
  └─ 配置：后端(A/B) / URL / key / model
```

**数据模型**
```kotlin
data class AgentMessage(
    val role: String,          // "user" | "agent"
    val text: String,
    val images: List<String>,  // 本地路径或 http(s) URL
    val at: Long,
    val done: Boolean,
)
```

**客户端抽象**
```kotlin
interface AgentClient {
    fun ask(history: List<AgentMessage>, prompt: String,
            images: List<String>,
            onDelta: (String) -> Unit,
            onImage: (String) -> Unit,
            onDone: () -> Unit,
            onError: (String) -> Unit)
}
class HermesDirectClient(baseUrl: String, apiKey: String, model: String) : AgentClient
class LingzhuClient(bridgeUrl: String, ak: String) : AgentClient
```

**图片解析**：两种后端都可能用 markdown `![alt](url)` 或富内容返回图片。客户端在流里解析出 URL → 手机下载渲染；下发给眼镜只发占位串（见 §4）。

**复用现有基建**：`FocusStore` 加 `agentMessages` 持久化；流式读用现有 OkHttp；配置项像 `syncUrl` 一样存 prefs。

---

## 3. 眼镜端：成为一等 app + 语音呼出

### 3.1 一等 app（launcher）
- 现状：`glass/.../AndroidManifest.xml` 的 `MainActivity` **已声明 `CATEGORY_LAUNCHER`**，`com.rokid.os.sprite.launcher` 应该能列出它。
- **待验证**：重装后在眼镜 app 列表里是否出现「Focus」。若不出现，检查 launcher 是否只列特定包/需要额外 metadata（下一步实测）。
- 可选：换一个更醒目的 label/图标，让它一眼可辨。

### 3.2 语音呼出
- 设备实测**没有** `com.rokid.glass.*` 终端 SDK 服务（只有 `com.rokid.glass.ota`），文档里的 `GlassSdk.getGlassOfflineCmdService()` 在这台消费级眼镜上**大概率不可用**。
- 设备**有** `com.rokid.os.sprite.assistserver`（系统助手）。可能可行的路径（按优先级）：
  1. **助手按名打开我们的 app**：用户说「打开 Focus / 怪奇实验室」→ launcher 启动 MainActivity → app 立即进 Agent 模式。最省事，无需注册。
  2. **灵珠自定义智能体（B 路线）**：把 agent 挂到系统助手侧，说一句话直接进 Hermes。需要灵珠配置 + 公网。
  3. **本地手势兜底**（完全可控，先做）：复用 `KeyReceiver`——例如长按/双指手势 → 进 Agent 模式并开始收音。
- 结论：**P3 先做手势兜底**，同时实测路径 1；P4 再评估路径 2。

### 3.3 眼镜上如何显示 agent 回复
- 复用 Convo 视口：新增一个 agent 模式，agent 的流式文本按句渲染（和现有 `asr` 通道类似）。
- 新增 Caps（见 §4）：`agent_stream` 增量文本；`agent_done`；图片只发**占位提示**。
- 眼镜是 glance：**只显示最后一两行**，长回答截断，完整内容在手机 Agent tab。

---

## 4. Caps 协议增量（phone ↔ glass）

沿用现有 `rk_custom_client`（P→G）/ `rk_custom_key`（G→P）。

| 方向 | name | fields | 说明 |
|---|---|---|---|
| P→G | `agent_stream` | role, text | 流式增量，眼镜按句刷新 |
| P→G | `agent_img` | count | 只提示「有图片 · 手机查看」，不发原图 |
| P→G | `agent_done` | — | 本轮结束 |
| G→P | `agent_ask` | text? | 手势/语音触发，开始一轮 |
| G→P | `agent_pick` | index | 选择 follow_up 建议（灵珠 `follow_up`） |

（现状已有 `asr` / `react` / `listen_state`，新增通道与之并列，互不干扰。）

---

## 5. 图片为什么放手机
- 波导是全绿、低分辨率、单色倾向 → 图片在眼镜上必丑且耗流。
- 因此：**眼镜只给一行提示**（`🖼 图片 · 手机查看`）；手机 Agent tab 内联渲染，点击可全屏缩放、保存。
- 如果将来要在眼镜上显示，也只放**极低分辨率缩略图或线稿**，不是原图。

---

## 6. 分阶段

| 阶段 | 内容 |
|---|---|
| P0 | 本文档（已完成） |
| P1 | 手机 Agent tab + `HermesDirectClient`（纯文本流式）+ 配置项 |
| P2 | 图片：解析 + 手机内联渲染 + 全屏查看；眼镜占位提示 |
| P3 | 眼镜端：手势呼出 + agent 流式显示（Caps） |
| P4 | 灵珠（B 路线）+ 系统助手「按名打开/直呼 agent」验证 |
| P5 | 「带上 convo 上下文」「用眼镜拍照」输入 |

---

## 7. 待确认 / 风险
- **后端选择**：用户暂未定 A/B，两条都试。需要具体值：A 的 `gateway URL / HERMES_API_KEY / model`；B 的 `公网 /api/sse URL / ROKID_AK`。
- **可达性**：直连要求手机能到 Gateway（同 LAN 或公网）。若 Hermes 在家里/内网，需 Cloudflare Tunnel 或走灵珠。
- **流式格式**：以实测响应为准（A 的 delta 结构、B 的 `answer_stream`）。
- **隐私/同意**：眼镜在录音+拍摄，需考虑对方可见的提示与本地处理边界。
- **语音呼出**：assistserver 能否启动第三方 app 需实测，不保证。
- **电池/发热**：常态录音 + 双讯飞会话 + 长连接，注意眼镜续航。

---

## 8. 参考
- 调研：`docs/RESEARCH-extensions.md`
- 对话链路硬规则：`docs/LISTEN.md`
- Hermes bridge：`github.com/xingdongcai/rokid-hermes-bridge`、`github.com/h4dex/rokid-hermes-bridge`
- CXR-L SDK：`https://open.rokid.com/sdk?lang=zh`
