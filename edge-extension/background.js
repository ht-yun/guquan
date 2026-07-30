const GSXT_URL = "https://www.gsxt.gov.cn/";
const GSXT_PATTERNS = ["https://www.gsxt.gov.cn/*", "https://*.gsxt.gov.cn/*"];
const MIN_COMPANY_INTERVAL_MS = 8000;
let running = false;
const processingTabs = new Set();

function isGsxtUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:"
      && (url.hostname === "gsxt.gov.cn" || url.hostname.endsWith(".gsxt.gov.cn"));
  } catch (_) {
    return false;
  }
}

function normalizeConfig(config) {
  return {
    appUrl: String(config?.appUrl || "http://127.0.0.1:8080").replace(/\/+$/, ""),
    pairingCode: String(config?.pairingCode || "").trim(),
    jobId: String(config?.jobId || "").trim()
  };
}

async function setState(status, message, target = null, level = "") {
  const collectorState = {status, message, target, level, updatedAt: new Date().toISOString()};
  await chrome.storage.local.set({collectorState});
  return collectorState;
}

async function api(config, path, options = {}) {
  const response = await fetch(`${config.appUrl}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-Extension-Token": config.pairingCode,
      ...(options.headers || {})
    }
  });
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { data = {message: text}; }
  if (!response.ok) throw new Error(data?.message || data?.error || `本地项目返回 HTTP ${response.status}`);
  return data;
}

async function nextTarget(config) {
  return api(config, `/api/edge-extension/jobs/${encodeURIComponent(config.jobId)}/next`);
}

async function findOrOpenGsxtTab(preferredTabId = null) {
  if (preferredTabId != null) {
    try {
      const preferred = await chrome.tabs.get(preferredTabId);
      if (isGsxtUrl(preferred.url)) {
        await chrome.tabs.update(preferred.id, {active: true});
        return preferred;
      }
    } catch (_) {
      // The previously bound tab was closed.
    }
  }
  const activeTabs = await chrome.tabs.query({active: true, currentWindow: true, url: GSXT_PATTERNS});
  if (activeTabs.length) return activeTabs[0];
  const tabs = await chrome.tabs.query({url: GSXT_PATTERNS});
  if (tabs.length) {
    await chrome.tabs.update(tabs[0].id, {active: true});
    return tabs[0];
  }
  return chrome.tabs.create({url: GSXT_URL, active: true});
}

async function waitForTab(tabId, timeoutMs = 30000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const tab = await chrome.tabs.get(tabId);
    if (tab.status === "complete") return tab;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error("等待官网页面加载超时，请检查浏览器页面后点击“继续”");
}

async function sendToPage(tabId, message) {
  try {
    return await chrome.tabs.sendMessage(tabId, message);
  } catch (_) {
    await chrome.scripting.executeScript({target: {tabId}, files: ["content.js"]});
    return chrome.tabs.sendMessage(tabId, message);
  }
}

async function captureAndSave(config, tabId, target, force = false) {
  await setState("COLLECTING", "正在读取并解析当前企业详情页……", target);
  const capture = await sendToPage(tabId, {type: force ? "COLLECT_DETAIL_FORCE" : "COLLECT_DETAIL", target});
  if (!capture?.pageText || capture.pageText.length < 80) {
    throw new Error(capture?.message || "当前页面不是可采集的企业详情页");
  }
  const result = await api(config,
    `/api/edge-extension/jobs/${encodeURIComponent(config.jobId)}/companies/${target.companyId}/capture`,
    {method: "POST", body: JSON.stringify({pageText: capture.pageText, currentUrl: capture.currentUrl})});
  if (!result.completed) {
    await setState("WAITING_REVIEW", result.message, target, "warn");
    return;
  }
  if (!result.nextTarget) {
    running = false;
    await chrome.storage.local.set({collectorActive: false});
    await setState("COMPLETED", result.message, null, "ok");
    return;
  }
  await chrome.storage.local.set({lastCompanyCompletedAt: Date.now()});
  await setState("COOLDOWN", `${result.message}。为降低官网访问频率，${MIN_COMPANY_INTERVAL_MS / 1000} 秒后继续。`,
    result.nextTarget, "ok");
  await new Promise(resolve => setTimeout(resolve, MIN_COMPANY_INTERVAL_MS));
  const active = await chrome.storage.local.get("collectorActive");
  if (!running || !active.collectorActive) return;
  await setState("NEXT_COMPANY", result.message, result.nextTarget, "ok");
  await chrome.tabs.update(tabId, {url: GSXT_URL, active: true});
}

async function processTarget(config, tabId, target) {
  if (!running) return;
  for (let attempt = 0; attempt < 8; attempt++) {
    await setState(attempt === 0 ? "RUNNING" : "SEARCHING",
      attempt === 0 ? "正在检查官网页面并准备查询……" : "正在等待官网返回查询结果……", target);
    if (attempt > 0) {
      await new Promise(resolve => setTimeout(resolve, 2200));
      await waitForTab(tabId, 15000);
    }
    const result = await sendToPage(tabId, {type: "AUTOMATE_COMPANY", target});
    if (!result) throw new Error("扩展未收到官网页面响应");

    if (result.status === "DETAIL_READY") {
      await captureAndSave(config, tabId, target);
      return;
    }
    if (result.status === "SEARCH_SUBMITTED") {
      await setState("SEARCHING", result.message, target);
      continue;
    }
    if (result.status === "WAITING_SELECTION") {
      await setState("WAITING_SELECTION", result.message, target, "warn");
      return;
    }
    if (result.status === "WAITING_MANUAL" || result.status === "BLOCKED") {
      await setState(result.status, result.message, target, "warn");
      return;
    }
    await setState("WAITING_MANUAL", result.message || "请检查官网页面后点击继续", target, "warn");
    return;
  }
  await setState("WAITING_MANUAL", "官网查询结果长时间未稳定，请检查页面并在完成验证后点击继续", target, "warn");
}

async function runTarget(config, tabId, target) {
  if (processingTabs.has(tabId)) return;
  processingTabs.add(tabId);
  try {
    await processTarget(config, tabId, target);
  } finally {
    processingTabs.delete(tabId);
  }
}

async function start(configInput) {
  const config = normalizeConfig(configInput);
  if (!config.pairingCode || !config.jobId) throw new Error("缺少扩展配对码或企业任务编号");
  running = true;
  await chrome.storage.local.set({collectorActive: true});
  const target = await nextTarget(config);
  if (!target) {
    running = false;
    await chrome.storage.local.set({collectorActive: false});
    return setState("COMPLETED", "当前企业任务已经全部处理完成", null, "ok");
  }
  const stored = await chrome.storage.local.get("collectorTabId");
  const tab = await findOrOpenGsxtTab(stored.collectorTabId);
  await chrome.storage.local.set({collectorTabId: tab.id});
  await waitForTab(tab.id);
  await runTarget(config, tab.id, target);
  const currentState = await chrome.storage.local.get("collectorState");
  return currentState.collectorState;
}

async function continueCurrent(configInput, forceCapture = false) {
  const config = normalizeConfig(configInput);
  running = true;
  await chrome.storage.local.set({collectorActive: true});
  const stored = await chrome.storage.local.get(["collectorState", "collectorTabId"]);
  const target = stored.collectorState?.target || await nextTarget(config);
  if (!target) {
    running = false;
    await chrome.storage.local.set({collectorActive: false});
    return setState("COMPLETED", "当前企业任务已经全部处理完成", null, "ok");
  }
  const tab = await findOrOpenGsxtTab(stored.collectorTabId);
  await chrome.storage.local.set({collectorTabId: tab.id});
  await waitForTab(tab.id);
  if (forceCapture) {
    await captureAndSave(config, tab.id, target, true);
  } else {
    await runTarget(config, tab.id, target);
  }
  const current = await chrome.storage.local.get("collectorState");
  return current.collectorState;
}

async function stopCollector() {
  running = false;
  await chrome.storage.local.set({collectorActive: false});
  return setState("STOPPED", "已停止自动采集。解除官网限制前请不要继续刷新或重复查询。", null, "warn");
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  const work = message.type === "COLLECTOR_START"
    ? start(message.config)
    : message.type === "COLLECTOR_CONTINUE"
      ? continueCurrent(message.config, false)
      : message.type === "COLLECTOR_CAPTURE_CURRENT"
        ? continueCurrent(message.config, true)
        : message.type === "COLLECTOR_STOP"
          ? stopCollector()
        : null;
  if (!work) return false;
  work.then(state => sendResponse({state}))
    .catch(async error => {
      const state = await setState("ERROR", error.message || String(error), null, "error");
      sendResponse({error: state.message, state});
    });
  return true;
});

chrome.tabs.onUpdated.addListener(async (tabId, changeInfo, tab) => {
  if (changeInfo.status !== "complete" || !isGsxtUrl(tab.url)) return;
  const stored = await chrome.storage.local.get(
    ["collectorConfig", "collectorState", "collectorActive", "collectorTabId"]);
  if (!stored.collectorActive) return;
  if (stored.collectorTabId !== tabId) return;
  running = true;
  const config = stored.collectorConfig;
  const state = stored.collectorState;
  if (["WAITING_MANUAL", "WAITING_SELECTION", "WAITING_REVIEW", "COOLDOWN", "BLOCKED", "STOPPED", "COMPLETED", "ERROR"]
      .includes(state?.status)) return;
  const target = state?.target;
  if (!config || !target) return;
  try {
    await runTarget(normalizeConfig(config), tabId, target);
  } catch (error) {
    await setState("ERROR", error.message || String(error), target, "error");
  }
});

chrome.tabs.onRemoved.addListener(async tabId => {
  const stored = await chrome.storage.local.get(["collectorTabId", "collectorActive"]);
  if (stored.collectorTabId !== tabId) return;
  running = false;
  await chrome.storage.local.set({collectorActive: false});
  await setState("STOPPED", "采集标签页已关闭，请重新打开官网后再开始。", null, "warn");
});
