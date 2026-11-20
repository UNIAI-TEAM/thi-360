/*
 * Runtime của các bản vẽ `*.dc.html` (FU-DESIGN-1, FU-DESIGN-2).
 *
 * Mỗi trang có một khối `<x-dc>` (mẫu), một `<script type="text/x-dc" data-dc-script>` khai báo `class Component extends
 * DCLogic` (state, `renderVals()`) và có thể có `<helmet>` (font, CSS). Tệp này đọc ba khối đó, thế `{{đường.dẫn}}` bằng
 * giá trị của `renderVals()`, chạy `<sc-for list as>` và `<sc-if value>`, gắn `onClick`, `onChange` vào hàm cùng tên và vẽ lại
 * khi `setState`. Không phụ thuộc thư viện nào; chỉ dùng cho xem bản vẽ, không vào mã sản phẩm.
 *
 * Mẫu được đọc từ chính tệp nguồn (fetch), vì trình duyệt đặt lại phần tử lạ nằm trong `<table>` (`<sc-for>` quanh `<tr>`)
 * trước khi mã chạy. Mở qua http (ví dụ `npx serve docs/design`) cho đủ bảng; mở thẳng bằng `file://` thì fetch bị chặn, mẫu
 * lấy từ DOM đã dựng và các bảng có `<sc-for>` có thể sai chỗ.
 *
 * Tham số trang: `?dark=1` bật chế độ tối (prop `dark`).
 */
(function () {
  "use strict";

  var SC_TAG = /<(\/?)sc-(for|if)\b/g;
  var WHOLE = /^\s*\{\{\s*([\w$.]+)\s*\}\}\s*$/;
  var ANY = /\{\{\s*([\w$.]+)\s*\}\}/g;
  var BOOLEAN_ATTRS = { checked: 1, disabled: 1, selected: 1, hidden: 1, readonly: 1, required: 1, open: 1 };

  /** Lớp cơ sở của `Component`: giữ props, state và vẽ lại sau `setState`. */
  function DCLogic(props) {
    this.props = props || {};
    this.state = {};
    this.__rerender = function () {};
  }
  DCLogic.prototype.setState = function (patch) {
    var next = typeof patch === "function" ? patch(this.state) : patch;
    this.state = Object.assign({}, this.state, next);
    this.__rerender();
  };
  DCLogic.prototype.renderVals = function () {
    return {};
  };
  window.DCLogic = DCLogic;

  function resolve(path, scope) {
    var parts = path.split(".");
    var value = scope[parts[0]];
    for (var i = 1; i < parts.length && value != null; i += 1) value = value[parts[i]];
    return value;
  }

  function text(value) {
    return value === null || value === undefined || value === false ? "" : String(value);
  }

  function fill(source, scope) {
    return source.replace(ANY, function (_all, path) {
      return text(resolve(path, scope));
    });
  }

  function truthy(value) {
    return Boolean(value) && value !== "false";
  }

  function applyAttribute(el, name, raw, scope) {
    var whole = WHOLE.exec(raw);
    if (!whole) {
      el.setAttribute(name, fill(raw, scope));
      return;
    }
    var value = resolve(whole[1], scope);
    if (/^on[A-Z]/.test(name) || /^on[a-z]+$/.test(name)) {
      if (typeof value === "function") el.addEventListener(name.slice(2).toLowerCase(), value);
      return;
    }
    var lower = name.toLowerCase();
    if (BOOLEAN_ATTRS[lower]) {
      if (truthy(value)) el.setAttribute(name, "");
      return;
    }
    if (value === null || value === undefined) return;
    el.setAttribute(name, String(value));
  }

  function renderNodes(nodes, scope, out) {
    for (var i = 0; i < nodes.length; i += 1) renderNode(nodes[i], scope, out);
  }

  function renderNode(node, scope, out) {
    if (node.nodeType === 3) {
      out.appendChild(document.createTextNode(fill(node.nodeValue, scope)));
      return;
    }
    if (node.nodeType !== 1) return;
    if (node.localName === "template" && node.dataset.sc) {
      var test = node.getAttribute("list") || node.getAttribute("value") || "";
      var match = WHOLE.exec(test);
      var value = match ? resolve(match[1], scope) : undefined;
      if (node.dataset.sc === "if") {
        if (truthy(value)) renderNodes(node.content.childNodes, scope, out);
        return;
      }
      var name = node.getAttribute("as") || "item";
      var list = Array.isArray(value) ? value : [];
      list.forEach(function (item, index) {
        var inner = Object.create(scope);
        inner[name] = item;
        inner.$index = index;
        renderNodes(node.content.childNodes, inner, out);
      });
      return;
    }
    var el = document.createElementNS(node.namespaceURI, node.localName);
    for (var a = 0; a < node.attributes.length; a += 1) applyAttribute(el, node.attributes[a].name, node.attributes[a].value, scope);
    renderNodes(node.childNodes, scope, el);
    if (node.localName === "input" && el.hasAttribute("value")) el.value = el.getAttribute("value");
    out.appendChild(el);
  }

  /** `<sc-for>`, `<sc-if>` thành `<template data-sc>`: `<template>` đứng được giữa `<tbody>`, `<tr>` mà không bị đẩy ra ngoài bảng. */
  function toTemplates(html) {
    return html.replace(SC_TAG, function (_all, close, kind) {
      return close ? "</template" : '<template data-sc="' + kind + '"';
    });
  }

  function propsOf(script) {
    var declared = {};
    try {
      declared = JSON.parse(script.getAttribute("data-props") || "{}");
    } catch (_error) {
      declared = {};
    }
    var props = {};
    Object.keys(declared).forEach(function (key) {
      if (key.charAt(0) !== "$" && declared[key] && "default" in declared[key]) props[key] = declared[key].default;
    });
    var query = new URLSearchParams(window.location.search);
    if (query.has("dark")) props.dark = query.get("dark") !== "0" && query.get("dark") !== "false";
    return props;
  }

  function templateSource(host) {
    return fetch(window.location.href)
      .then(function (response) {
        if (!response.ok) throw new Error(String(response.status));
        return response.text();
      })
      .then(function (source) {
        var found = /<x-dc>([\s\S]*)<\/x-dc>/.exec(source);
        if (!found) throw new Error("x-dc not found");
        return found[1];
      })
      .catch(function () {
        return host.innerHTML;
      });
  }

  function start() {
    var host = document.querySelector("x-dc");
    var script = document.querySelector("script[data-dc-script]");
    if (!host || !script) return;
    var style = document.createElement("style");
    style.textContent = "x-dc{display:block}";
    document.head.appendChild(style);

    templateSource(host).then(function (source) {
      var helmet = /<helmet>([\s\S]*?)<\/helmet>/.exec(source);
      if (helmet) {
        var head = document.createElement("template");
        head.innerHTML = helmet[1];
        document.head.appendChild(head.content);
        source = source.replace(helmet[0], "");
      }
      var template = document.createElement("template");
      template.innerHTML = toTemplates(source);

      var Component = new Function("DCLogic", script.textContent + "\nreturn Component;")(DCLogic);
      var logic = new Component(propsOf(script));
      function render() {
        var active = document.activeElement;
        var focusId = active && active.id ? active.id : null;
        var scope = Object.create(logic.renderVals() || {});
        var out = document.createDocumentFragment();
        renderNodes(template.content.childNodes, scope, out);
        host.replaceChildren(out);
        if (focusId) {
          var again = document.getElementById(focusId);
          if (again) again.focus();
        }
      }
      logic.__rerender = render;
      render();
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start);
  else start();
})();
