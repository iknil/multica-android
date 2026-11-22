import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const script = readFileSync(new URL("../app/src/main/assets/download-capture.js", import.meta.url), "utf8")
  .replace("__TOKEN__", "test-token");
const source = new Blob(["issue,title\n1,Example\n"], { type: "text/csv" });
const chunks = [];
let began = false;
let finished = false;
let revoked = false;
let capture;

class FakeElement {
  closest() { return this; }
  hasAttribute(name) { return name === "download"; }
  click() {}
  href = "blob:https://multica.ai/test-export";
  download = "issues.csv";
}

const context = {
  window: {
    MulticaDownloads: {
      begin(token, name, type, size) {
        assert.equal(token, "test-token");
        assert.equal(name, "issues.csv");
        assert.equal(type, "text/csv");
        assert.equal(size, source.size);
        began = true;
        return true;
      },
      append(token, base64) {
        assert.equal(token, "test-token");
        chunks.push(Buffer.from(base64, "base64"));
        return true;
      },
      finish(token) {
        assert.equal(token, "test-token");
        finished = true;
        return true;
      },
      fail() { assert.fail("unexpected export failure"); },
    },
  },
  document: { addEventListener(type, callback) {
    assert.equal(type, "click");
    capture = callback;
  } },
  Element: FakeElement,
  HTMLAnchorElement: FakeElement,
  URL: { revokeObjectURL() { revoked = true; } },
  fetch: async () => ({ blob: async () => source }),
  Uint8Array,
  btoa: (binary) => Buffer.from(binary, "binary").toString("base64"),
};
vm.runInNewContext(script, context);

let prevented = false;
const anchor = new FakeElement();
capture({ target: anchor, preventDefault() { prevented = true; } });
assert.equal(prevented, true);
context.URL.revokeObjectURL(anchor.href); // The web app revokes immediately after click.
assert.equal(revoked, false, "capture must retain the URL during export");

for (let attempt = 0; attempt < 30 && !finished; attempt++) {
  await new Promise((resolve) => setTimeout(resolve, 10));
}
assert.equal(began, true);
assert.equal(finished, true);
assert.equal(Buffer.concat(chunks).toString(), "issue,title\n1,Example\n");
assert.equal(revoked, true, "capture must release the URL after export");
console.log("download capture: immediate-revoke blob export passed");
