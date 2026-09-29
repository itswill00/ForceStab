<template>
  <span style="display:inline-flex;vertical-align:middle" v-html="svg"></span>
</template>

<script>
const ICONS = {
  check: '<path d="M20 6 9 17l-5-5"/>',
  video: '<path d="M23 7l-7 5 7 5V7z"/><path d="M1 5h15a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H1a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"/>',
  terminal: '<path d="M4 17l6-6-6-6"/><path d="M12 19h8"/>',
  refresh: '<path d="M23 4v6h-6"/><path d="M1 20v-6h6"/><path d="M3.5 9a9 9 0 0 1 14.9-3.4L23 10"/><path d="M1 14l.6-1.6A9 9 0 0 1 20.5 15"/>',
  info: '<path d="M12 16v-4"/><path d="M12 8h.01"/><path d="M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z"/>',
  camera: '<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><path d="M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/>'
}

export default {
  name: 'Icons',
  props: {
    name: { type: String, default: 'info' },
    size: { type: Number, default: 14 }
  },
  computed: {
    svg() {
      const inner = ICONS[this.name] || ICONS.info
      const s = this.size
      return `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`
    }
  }
}
</script>
