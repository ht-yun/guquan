const byId = id => document.getElementById(id);

async function load() {
  const stored = await chrome.storage.local.get(["collectorConfig", "collectorState"]);
  const config = stored.collectorConfig || {};
  byId("appUrl").value = config.appUrl || "http://127.0.0.1:8080";
  byId("pairingCode").value = config.pairingCode || "";
  byId("jobId").value = config.jobId || "";
  render(stored.collectorState);
}

function configFromForm() {
  const pasted = byId("pairingInfo").value.trim();
  if (pasted) {
    try {
      const parsed = JSON.parse(pasted);
      byId("appUrl").value = parsed.appUrl || byId("appUrl").value;
      byId("pairingCode").value = parsed.pairingCode || "";
      byId("jobId").value = parsed.jobId || "";
    } catch (_) {
      throw new Error("配对信息格式不正确，请重新从项目页面复制完整内容。");
    }
  }
  return {
    appUrl: byId("appUrl").value.trim().replace(/\/+$/, ""),
    pairingCode: byId("pairingCode").value.trim(),
    jobId: byId("jobId").value.trim()
  };
}

function render(state) {
  if (!state) {
    byId("status").textContent = "尚未连接本地项目。";
    return;
  }
  const company = state.target?.companyName ? `\n当前企业：${state.target.companyName}` : "";
  byId("status").className = `status ${state.level || ""}`;
  byId("status").textContent = `${state.message || state.status || "等待操作"}${company}`;
}

async function send(type) {
  const buttons = [...document.querySelectorAll("button")];
  buttons.forEach(button => { button.disabled = true; });
  let config;
  try {
    config = configFromForm();
  } catch (error) {
    render({level: "error", message: error.message});
    buttons.forEach(button => { button.disabled = false; });
    return;
  }
  if (!config.appUrl || !config.pairingCode || !config.jobId) {
    render({level: "error", message: "请填写项目地址、配对码和企业任务编号。"});
    buttons.forEach(button => { button.disabled = false; });
    return;
  }
  try {
    await chrome.storage.local.set({collectorConfig: config});
    const response = await chrome.runtime.sendMessage({type, config});
    if (response?.error) {
      render({level: "error", message: response.error});
    } else {
      render(response?.state);
    }
  } catch (error) {
    render({level: "error", message: error.message || String(error)});
  } finally {
    buttons.forEach(button => { button.disabled = false; });
  }
}

byId("start").addEventListener("click", () => send("COLLECTOR_START"));
byId("continue").addEventListener("click", () => send("COLLECTOR_CONTINUE"));
byId("capture").addEventListener("click", () => send("COLLECTOR_CAPTURE_CURRENT"));
byId("stop").addEventListener("click", async () => {
  const response = await chrome.runtime.sendMessage({type: "COLLECTOR_STOP"});
  render(response?.state || {level: "warn", message: "已停止采集。"});
});
chrome.storage.onChanged.addListener(changes => {
  if (changes.collectorState) render(changes.collectorState.newValue);
});
load();
