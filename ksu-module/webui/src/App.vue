<template>
  <div class="app-shell">
    <header class="page-header">
      <div>
        <div class="page-header-title">VideoModeFix</div>
        <div class="page-header-sub">Force third-party cameras into video mode</div>
      </div>
      <span class="badge-pill active">v0.1.0</span>
    </header>

    <div class="tabs-control">
      <button class="tab-btn" :class="{ active: tab === 'status' }" @click="tab = 'status'">
        <Icons name="info" :size="13" /><span>Status</span>
      </button>
      <button class="tab-btn" :class="{ active: tab === 'verify' }" @click="tab = 'verify'">
        <Icons name="check" :size="13" /><span>Verify</span>
      </button>
      <button class="tab-btn" :class="{ active: tab === 'logs' }" @click="tab = 'logs'">
        <Icons name="terminal" :size="13" /><span>Logs</span>
      </button>
    </div>

    <section v-if="tab === 'status'" class="card">
      <div class="card-title">Install status</div>
      <div class="card-sub">Module, hook APK and LSPosed framework presence.</div>
      <div class="row" v-for="r in statusRows" :key="r.label">
        <span class="row-label">{{ r.label }}</span>
        <span class="row-val" :class="r.ok === true ? 'ok' : (r.ok === false ? 'bad' : '')">{{ r.value }}</span>
      </div>
      <button class="btn-md3 btn-md3-secondary" @click="loadStatus" :disabled="busy">
        <Icons name="refresh" :size="13" /> Refresh
      </button>
      <div class="hint">Scope (which apps get hooked) is managed in LSPosed Manager, not here.</div>
    </section>

    <section v-if="tab === 'verify'" class="card">
      <div class="card-title">Camera verification</div>
      <div class="card-sub">Dumps HAL stabilization caps and recent camera clients. Record with the target app first.</div>
      <button class="btn-md3 btn-md3-primary" @click="runVerify" :disabled="busy">
        <Icons name="camera" :size="13" /> Run verify
      </button>
      <div class="terminal">{{ verifyOut }}</div>
    </section>

    <section v-if="tab === 'logs'" class="card">
      <div class="card-title">Hook log</div>
      <div class="card-sub">Recent <code>VideoModeFix</code> lines from the LSPosed modules log.</div>
      <button class="btn-md3 btn-md3-primary" @click="loadLogs" :disabled="busy">
        <Icons name="terminal" :size="13" /> Load logs
      </button>
      <div class="terminal">{{ logsOut }}</div>
      <div class="hint">Empty after a fresh boot is normal — open a scoped app's camera first.</div>
    </section>
    <footer class="about-footer">
      <div class="about-footer-sub">@itswill00 · VideoModeFix v0.1.0</div>
    </footer>
  </div>
</template>

<script>
import Icons from './components/Icons.vue'
import { execCommand } from './helpers/shell.js'
import { parseStatus } from './helpers/format.js'

export default {
  name: 'App',
  components: { Icons },
  data() {
    return {
      tab: 'status',
      busy: false,
      status: { module: '…', hookApk: '…', lsposed: '…', conf: [] },
      verifyOut: '',
      logsOut: ''
    }
  },
  computed: {
    statusRows() {
      const s = this.status
      const row = (label, value, good) => ({ label, value, ok: good == null ? null : value === good })
      return [
        row('KernelSU module', s.module, 'installed'),
        row('Hook APK', s.hookApk, 'installed'),
        row('LSPosed', s.lsposed, 'present')
      ]
    }
  },
  methods: {
    async loadStatus() {
      this.busy = true
      try {
        const out = await execCommand('sh /data/adb/modules/videomodefix/bin/videomodefix status')
        this.status = parseStatus(out)
      } finally {
        this.busy = false
      }
    },
    async runVerify() {
      this.busy = true
      this.verifyOut = 'running…'
      try {
        this.verifyOut = await execCommand('sh /data/adb/modules/videomodefix/bin/verify.sh') || '(empty)'
      } finally {
        this.busy = false
      }
    },
    async loadLogs() {
      this.busy = true
      this.logsOut = 'loading…'
      try {
        this.logsOut = await execCommand("grep -h 'VideoModeFix' /data/adb/lspd/log/modules_*.log 2>/dev/null | tail -n 12") || '(empty)'
      } finally {
        this.busy = false
      }
    }
  },
  mounted() {
    this.loadStatus()
  }
}
</script>
