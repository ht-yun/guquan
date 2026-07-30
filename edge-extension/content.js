(() => {
  if (globalThis.__companyCollectorLoaded) return;
  globalThis.__companyCollectorLoaded = true;

  const DETAIL_MARKERS = ["统一社会信用代码", "法定代表人", "登记机关", "登记状态", "经营范围"];
  const CHALLENGE_MARKERS = ["安全验证", "请完成验证", "拖动滑块", "点击完成验证", "访问过于频繁", "验证码"];

  function text() {
    return (document.body?.innerText || "").replace(/\u00a0/g, " ").trim();
  }

  function normalized(value) {
    return String(value || "").normalize("NFKC").replace(/\s+/g, "").toUpperCase();
  }

  function visible(element) {
    if (!element) return false;
    const style = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    return style.display !== "none" && style.visibility !== "hidden" && rect.width > 0 && rect.height > 0;
  }

  function blocked(body) {
    const lower = body.toLowerCase();
    return lower.includes("error 521") || lower.includes("web server is down")
      || lower.includes("403 forbidden") || lower.includes("access denied")
      || lower.includes("ip请求异常") || lower.includes("请求异常，请稍后再试");
  }

  function challenged(body) {
    return CHALLENGE_MARKERS.some(marker => body.includes(marker))
      || [...document.querySelectorAll("iframe")].some(frame => /captcha|verify/i.test(frame.src || frame.id || frame.className));
  }

  function detailPage(body, target) {
    const markerCount = DETAIL_MARKERS.filter(marker => body.includes(marker)).length;
    const targetName = normalized(target?.companyName);
    const targetCode = normalized(target?.creditCode);
    const normalizedBody = normalized(body);
    const containsTarget = (!targetName && !targetCode)
      || (targetName && normalizedBody.includes(targetName))
      || (targetCode && normalizedBody.includes(targetCode));
    return markerCount >= 3 && containsTarget;
  }

  function looksLikeDetailPage(body) {
    return DETAIL_MARKERS.filter(marker => body.includes(marker)).length >= 3;
  }

  function setNativeValue(input, value) {
    const prototype = input instanceof HTMLTextAreaElement
      ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(prototype, "value")?.set;
    if (setter) setter.call(input, value); else input.value = value;
    input.dispatchEvent(new Event("input", {bubbles: true}));
  }

  function searchInput() {
    const selectors = [
      "input[placeholder*='企业名称']",
      "input[placeholder*='统一社会信用代码']",
      "input[placeholder*='注册号']",
      "input[placeholder*='关键字']",
      "input[type='search']",
      "input[name*='keyword' i]",
      "input[id*='keyword' i]",
      "input[id*='search' i]"
    ];
    return selectors.flatMap(selector => [...document.querySelectorAll(selector)]).find(visible);
  }

  function clickableByText(labels) {
    const elements = [...document.querySelectorAll("button,a,[role='button'],input[type='submit']")];
    return elements.find(element => {
      if (!visible(element)) return false;
      const value = (element.innerText || element.value || "").trim();
      return labels.some(label => value === label || value.includes(label));
    });
  }

  function clickablesByText(labels) {
    return [...document.querySelectorAll("button,a,[role='button'],input[type='submit']")]
      .filter(element => {
        if (!visible(element)) return false;
        const value = (element.innerText || element.value || "").trim();
        return labels.some(label => value === label || value.includes(label));
      });
  }

  function resultCandidates(companyName) {
    const expected = normalized(companyName);
    if (!expected) return [];
    const candidates = [...document.querySelectorAll("a,[role='link'],h2,h3,h4,.title,[class*='name' i]")]
      .filter(visible)
      .filter(element => {
        const value = normalized(element.innerText || element.textContent);
        return value === expected || value.startsWith(expected);
      });
    return [...new Set(candidates.map(element => element.closest("a,[role='link']") || element))];
  }

  async function clickExpandAll() {
    const clicked = new WeakSet();
    for (let round = 0; round < 3; round++) {
      const expands = clickablesByText(["全部展开", "展开全部"])
        .filter(expand => !clicked.has(expand));
      if (!expands.length) break;
      expands.forEach(expand => {
        clicked.add(expand);
        expand.click();
      });
      await new Promise(resolve => setTimeout(resolve, 1200));
    }
  }

  async function automate(target) {
    const body = text();
    if (blocked(body)) {
      return {status: "BLOCKED", message: "官网提示 IP 请求异常或拒绝访问。扩展已暂停，请不要继续刷新或重试，等待官网解除限制后再点击继续。"};
    }
    if (challenged(body)) {
      return {status: "WAITING_MANUAL", message: "请在当前 Edge 页面完成验证码或安全验证，然后点击扩展中的“我已完成验证，继续”。"};
    }
    if (detailPage(body, target)) {
      return {status: "DETAIL_READY", message: "已进入目标企业详情页，准备采集。"};
    }

    const candidates = resultCandidates(target?.companyName);
    if (candidates.length === 1) {
      const openKey = `${target?.companyId || ""}:${normalized(target?.companyName)}`;
      if (document.documentElement.getAttribute("data-company-collector-open-key") === openKey) {
        return {status: "WAITING_MANUAL", message: "已尝试打开唯一匹配企业，但页面没有跳转。请手动进入企业详情页后点击继续。"};
      }
      document.documentElement.setAttribute("data-company-collector-open-key", openKey);
      const candidate = candidates[0];
      const anchor = candidate.closest("a[href]") || (candidate.matches?.("a[href]") ? candidate : null);
      if (anchor?.href && /^https?:/i.test(anchor.href)) {
        location.assign(anchor.href);
      } else {
        candidate.removeAttribute?.("target");
        candidate.click();
      }
      return {status: "SEARCH_SUBMITTED", message: "已选择唯一匹配企业，正在打开详情页。"};
    }
    if (candidates.length > 1) {
      return {status: "WAITING_SELECTION", message: "页面中存在多个同名或相似企业，请手动选择目标企业，进入详情页后点击继续。"};
    }

    const input = searchInput();
    if (!input) {
      return {status: "WAITING_MANUAL", message: "当前页面未找到企业搜索框。请手动打开官网首页或目标企业详情页后点击继续。"};
    }
    const keyword = target?.companyName || target?.creditCode;
    const fillKey = `${target?.companyId || ""}:${normalized(keyword)}`;
    const sharedFillKey = document.documentElement.getAttribute("data-company-collector-fill-key");
    if (globalThis.__companyCollectorLastFilled !== fillKey && sharedFillKey !== fillKey) {
      document.documentElement.setAttribute("data-company-collector-fill-key", fillKey);
      if (normalized(input.value) !== normalized(keyword)) {
        setNativeValue(input, keyword);
      }
      globalThis.__companyCollectorLastFilled = fillKey;
      input.focus();
    }
    return {
      status: "WAITING_MANUAL",
      message: `查询内容已准备：${keyword}。请由用户手动点击官网“查询/搜索”并完成验证，进入结果页后再点击扩展中的“我已完成验证，继续”。`
    };
  }

  async function collect(target, force = false) {
    let body = text();
    if (blocked(body)) return {message: "当前官网页面返回 521/403，无法采集。"};
    if (challenged(body)) return {message: "当前页面仍处于验证码或安全验证状态。"};
    if (!(force ? looksLikeDetailPage(body) : detailPage(body, target))) {
      return {message: "当前页面未识别为目标企业详情页，请先进入正确企业详情页。"};
    }
    await clickExpandAll();
    body = text();
    return {pageText: body, currentUrl: location.href};
  }

  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    const work = message.type === "AUTOMATE_COMPANY"
      ? automate(message.target)
      : message.type === "COLLECT_DETAIL"
        ? collect(message.target)
        : message.type === "COLLECT_DETAIL_FORCE"
          ? collect(message.target, true)
        : null;
    if (!work) return false;
    work.then(sendResponse).catch(error => sendResponse({status: "ERROR", message: error.message || String(error)}));
    return true;
  });
})();
