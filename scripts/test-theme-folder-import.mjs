import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const script = readFileSync(new URL("../app/src/main/assets/theme-folder-import.js", import.meta.url), "utf8")
  .replace("__TOKEN__", "test-state");
let click;
let picked = false;
let changed = false;

class Input {
  hasAttribute(name) { return name === "webkitdirectory"; }
  dispatchEvent(event) {
    assert.equal(event.type, "change");
    changed = true;
  }
}
class Transfer {
  files = [];
  items = { add: (file) => this.files.push(file) };
}
class File {
  constructor(parts, name, options) {
    this.bytes = parts[0];
    this.name = name;
    this.type = options.type;
  }
}

const context = {
  window: { MulticaThemeFolders: { choose(token) {
    assert.equal(token, "test-state");
    picked = true;
  } } },
  document: { addEventListener(type, callback) {
    assert.equal(type, "click");
    click = callback;
  } },
  location: { pathname: "/sample/settings" },
  HTMLInputElement: Input,
  DataTransfer: Transfer,
  File,
  Event: class { constructor(type) { this.type = type; } },
  Uint8Array,
  atob: (value) => Buffer.from(value, "base64").toString("binary"),
};
vm.runInNewContext(script, context);
const input = new Input();
let prevented = false;
click({ target: input, preventDefault() { prevented = true; }, stopImmediatePropagation() {} });
assert.equal(prevented, true);
assert.equal(picked, true);
assert.equal(context.window.__multicaThemeFolderChunk("bad-state", "", true), false);

const bytes = Buffer.from("test-theme-zip");
const encoded = bytes.toString("base64");
context.window.__multicaThemeFolderChunk("test-state", encoded.slice(0, 8), false);
context.window.__multicaThemeFolderChunk("test-state", encoded.slice(8), true);
assert.equal(changed, true);
assert.equal(input.files.length, 1);
assert.equal(input.files[0].name, "multica-theme.zip");
assert.equal(Buffer.from(input.files[0].bytes).toString(), "test-theme-zip");

context.location.pathname = "/sample/skills";
picked = false;
click({ target: new Input(), preventDefault() {}, stopImmediatePropagation() {} });
assert.equal(picked, false, "unrelated directory import must stay with WebView");
console.log("theme folder bridge: picker and chunk reconstruction passed");
